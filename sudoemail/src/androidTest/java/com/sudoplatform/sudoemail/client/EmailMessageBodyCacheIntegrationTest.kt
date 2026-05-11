/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.DefaultSudoEmailClient
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.SudoEmailClient.Companion.DEFAULT_KEYRING_SERVICE_NAME
import com.sudoplatform.sudoemail.TestData
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.internal.data.emailMessage.cache.RoomEmailMessageBodyCache
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.CacheFlushInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.CacheGetResult
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.CachePutInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.EmailMessageBodyCache
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.inputs.FlushMessageBodyCacheInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageWithBodyInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.collections.shouldNotContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * An instrumented wrapper around [EmailMessageBodyCache] that records
 * cache hits and misses for verification in integration tests.
 */
internal class InstrumentedEmailMessageBodyCache(
    private val delegate: EmailMessageBodyCache,
) : EmailMessageBodyCache {
    val hits = mutableListOf<String>()
    val misses = mutableListOf<String>()
    val puts = mutableListOf<String>()
    val deletes = mutableListOf<String>()
    val flushes = mutableListOf<CacheFlushInput>()

    override suspend fun get(messageId: String): CacheGetResult? {
        val result = delegate.get(messageId)
        if (result != null) {
            hits.add(messageId)
        } else {
            misses.add(messageId)
        }
        return result
    }

    override suspend fun put(input: CachePutInput) {
        puts.add(input.messageId)
        delegate.put(input)
    }

    override suspend fun deleteMessage(messageId: String) {
        deletes.add(messageId)
        delegate.deleteMessage(messageId)
    }

    override suspend fun flush(input: CacheFlushInput) {
        flushes.add(input)
        delegate.flush(input)
    }

    override suspend fun flushAll() {
        delegate.flushAll()
    }

    override suspend fun setCacheSizeLimit(bytes: Long) {
        delegate.setCacheSizeLimit(bytes)
    }

    override suspend fun getCacheSizeLimit(): Long = delegate.getCacheSizeLimit()

    fun reset() {
        hits.clear()
        misses.clear()
        puts.clear()
        deletes.clear()
        flushes.clear()
    }
}

/**
 * Integration tests for the email message body cache feature.
 *
 * These tests verify that the cache correctly intercepts the S3 download path,
 * stores sealed blobs locally, and returns cached copies on subsequent requests.
 */
@RunWith(AndroidJUnit4::class)
class EmailMessageBodyCacheIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

    private lateinit var instrumentedCache: InstrumentedEmailMessageBodyCache
    private lateinit var cacheDir: File
    private lateinit var emailAddress: EmailAddress
    private lateinit var cachedEmailClient: SudoEmailClient

    @Before
    fun setup() =
        runTest {
            sudoClient.reset()
            sudoClient.generateEncryptionKey()

            // Create a temp directory for the cache
            cacheDir = File(context.cacheDir, "email-cache-integ-${System.nanoTime()}")
            cacheDir.mkdirs()

            // Create the instrumented cache wrapping a real RoomEmailMessageBodyCache
            val realCache =
                RoomEmailMessageBodyCache(
                    context = context,
                    logger = logger,
                    cacheStoragePath = cacheDir.absolutePath,
                    initialCacheSizeLimitBytes = 50L * 1024 * 1024, // 50 MB for tests
                )
            instrumentedCache = InstrumentedEmailMessageBodyCache(realCache)
            val configuration = SudoEmailClient.readConfiguration(context, logger)
            // Build a client with the instrumented cache injected
            cachedEmailClient =
                DefaultSudoEmailClient(
                    context = context,
                    apiClient =
                        ApiClient(
                            ApiClientManager.getClient(
                                context,
                                userClient,
                                "emService",
                            ),
                            logger,
                        ),
                    sudoUserClient = userClient,
                    logger = logger,
                    serviceKeyManager =
                        DefaultServiceKeyManager(
                            keyRingServiceName = DEFAULT_KEYRING_SERVICE_NAME,
                            userClient = userClient,
                            keyManager = keyManager,
                        ),
                    region = configuration.region,
                    emailBucket = configuration.emailBucket,
                    transientBucket = configuration.transientBucket,
                    emailMessageBodyCache = instrumentedCache,
                )
        }

    @After
    fun teardown() =
        runTest {
            emailAddressList.map { cachedEmailClient.deprovisionEmailAddress(it.id) }
            sudoList.map { sudoClient.deleteSudo(it) }
            sudoClient.reset()
            cacheDir.deleteRecursively()
            instrumentedCache.flushAll()
        }

    fun waitForMessageCachedClient(messageId: String) {
        await.atMost(Duration.ONE_MINUTE).pollInterval(Duration.TEN_SECONDS).until {
            runTest {
                cachedEmailClient.getEmailMessage(GetEmailMessageInput(messageId)) != null
            }
            true
        }
    }

    // --- Cache hit and miss behaviour ---

    @Test
    fun firstFetchResultsInCacheMissAndPopulatesCache() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = createSudo(TestData.sudo)
            sudo.id shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            emailAddress = provisionEmailAddress(cachedEmailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val sendResult =
                sendEmailMessage(
                    client = cachedEmailClient,
                    fromAddress = emailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(successSimulatorAddress)),
                    subject = "Cache miss test ${UUID.randomUUID()}",
                    body = "First fetch test body",
                )
            waitForMessageCachedClient(sendResult.id)

            // First fetch — should be a cache miss
            val result =
                cachedEmailClient.getEmailMessageWithBody(
                    GetEmailMessageWithBodyInput(
                        id = sendResult.id,
                        emailAddressId = emailAddress.id,
                    ),
                )

            result shouldNotBe null
            result!!.body shouldNotBe null
            instrumentedCache.misses shouldContain sendResult.id
            instrumentedCache.puts shouldContain sendResult.id
            instrumentedCache.hits shouldNotContain sendResult.id
        }

    @Test
    fun secondFetchOfSameMessageResultsInCacheHit() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = createSudo(TestData.sudo)
            sudo.id shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            emailAddress = provisionEmailAddress(cachedEmailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val sendResult =
                sendEmailMessage(
                    client = cachedEmailClient,
                    fromAddress = emailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(successSimulatorAddress)),
                    subject = "Cache hit test ${UUID.randomUUID()}",
                    body = "Second fetch test body",
                )
            waitForMessageCachedClient(sendResult.id)

            // First fetch — populates cache
            cachedEmailClient.getEmailMessageWithBody(
                GetEmailMessageWithBodyInput(
                    id = sendResult.id,
                    emailAddressId = emailAddress.id,
                ),
            )

            instrumentedCache.reset()

            // Second fetch — should be a cache hit
            val result =
                cachedEmailClient.getEmailMessageWithBody(
                    GetEmailMessageWithBodyInput(
                        id = sendResult.id,
                        emailAddressId = emailAddress.id,
                    ),
                )

            result shouldNotBe null
            instrumentedCache.hits shouldContain sendResult.id
            instrumentedCache.misses shouldNotContain sendResult.id
        }

    @Test
    fun cachedContentIsIdenticalToFreshContent() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = createSudo(TestData.sudo)
            sudo.id shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            emailAddress = provisionEmailAddress(cachedEmailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val body = "Content integrity test — special chars: é à ü ñ"
            val sendResult =
                sendEmailMessage(
                    client = cachedEmailClient,
                    fromAddress = emailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(successSimulatorAddress)),
                    subject = "Integrity test ${UUID.randomUUID()}",
                    body = body,
                )
            waitForMessageCachedClient(sendResult.id)

            // First fetch from S3
            val firstResult =
                cachedEmailClient.getEmailMessageWithBody(
                    GetEmailMessageWithBodyInput(
                        id = sendResult.id,
                        emailAddressId = emailAddress.id,
                    ),
                )

            // Second fetch from cache
            val secondResult =
                cachedEmailClient.getEmailMessageWithBody(
                    GetEmailMessageWithBodyInput(
                        id = sendResult.id,
                        emailAddressId = emailAddress.id,
                    ),
                )

            firstResult shouldBe secondResult
        }

    // --- Cache invalidation ---

    @Test
    fun deletingMessageRemovesItFromCache() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = createSudo(TestData.sudo)
            sudo.id shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            emailAddress = provisionEmailAddress(cachedEmailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val sendResult =
                sendEmailMessage(
                    client = cachedEmailClient,
                    fromAddress = emailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(successSimulatorAddress)),
                    subject = "Delete cache test ${UUID.randomUUID()}",
                    body = "Delete test body",
                )
            waitForMessageCachedClient(sendResult.id)

            // Populate cache
            cachedEmailClient.getEmailMessageWithBody(
                GetEmailMessageWithBodyInput(
                    id = sendResult.id,
                    emailAddressId = emailAddress.id,
                ),
            )
            instrumentedCache.puts shouldContain sendResult.id

            instrumentedCache.reset()

            // Delete the message
            cachedEmailClient.deleteEmailMessage(sendResult.id)

            // Verify cache delete was called
            instrumentedCache.deletes shouldContain sendResult.id
        }

    @Test
    fun flushByEmailAddressClearsAllCachedEntriesForThatAddress() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = createSudo(TestData.sudo)
            sudo.id shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            emailAddress = provisionEmailAddress(cachedEmailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            // Send and cache two messages
            val send1 =
                sendEmailMessage(
                    client = cachedEmailClient,
                    fromAddress = emailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(successSimulatorAddress)),
                    subject = "Flush test 1 ${UUID.randomUUID()}",
                    body = "Flush test body 1",
                )
            val send2 =
                sendEmailMessage(
                    client = cachedEmailClient,
                    fromAddress = emailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(successSimulatorAddress)),
                    subject = "Flush test 2 ${UUID.randomUUID()}",
                    body = "Flush test body 2",
                )
            waitForMessageCachedClient(send1.id)
            waitForMessageCachedClient(send2.id)

            cachedEmailClient.getEmailMessageWithBody(
                GetEmailMessageWithBodyInput(id = send1.id, emailAddressId = emailAddress.id),
            )
            cachedEmailClient.getEmailMessageWithBody(
                GetEmailMessageWithBodyInput(id = send2.id, emailAddressId = emailAddress.id),
            )

            instrumentedCache.reset()

            // Flush by email address
            cachedEmailClient.flushMessageBodyCache(
                FlushMessageBodyCacheInput(emailAddressId = emailAddress.id),
            )

            instrumentedCache.flushes shouldContain CacheFlushInput(emailAddressId = emailAddress.id)

            // Re-fetching should result in cache misses
            cachedEmailClient.getEmailMessageWithBody(
                GetEmailMessageWithBodyInput(id = send1.id, emailAddressId = emailAddress.id),
            )
            instrumentedCache.misses shouldContain send1.id
        }

    // --- Cache size management ---

    @Test
    fun setCacheSizeLimitPersistsAndTakesEffect() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = createSudo(TestData.sudo)
            sudo.id shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            emailAddress = provisionEmailAddress(cachedEmailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val sendResult =
                sendEmailMessage(
                    client = cachedEmailClient,
                    fromAddress = emailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(successSimulatorAddress)),
                    subject = "Size limit test ${UUID.randomUUID()}",
                    body = "Size limit test body",
                )
            waitForMessageCachedClient(sendResult.id)

            // Populate cache
            cachedEmailClient.getEmailMessageWithBody(
                GetEmailMessageWithBodyInput(
                    id = sendResult.id,
                    emailAddressId = emailAddress.id,
                ),
            )
            instrumentedCache.puts shouldContain sendResult.id

            // Set cache size to 0 — should flush everything
            cachedEmailClient.setMessageBodyCacheSizeLimit(0)

            instrumentedCache.reset()

            // Fetching should still work (from S3) but cache get returns null
            val result =
                cachedEmailClient.getEmailMessageWithBody(
                    GetEmailMessageWithBodyInput(
                        id = sendResult.id,
                        emailAddressId = emailAddress.id,
                    ),
                )
            result shouldNotBe null
            instrumentedCache.misses shouldContain sendResult.id

            // Restore cache size for other tests
            cachedEmailClient.setMessageBodyCacheSizeLimit(50L * 1024 * 1024)
        }
}
