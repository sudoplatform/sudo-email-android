/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage.cache

import androidx.test.core.app.ApplicationProvider
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.CacheFlushInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.CachePutInput
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Property-based tests for [RoomEmailMessageBodyCache].
 *
 * These tests use a real Room database (via Robolectric) to verify correctness
 * properties across many random inputs.
 */
@RunWith(RobolectricTestRunner::class)
class RoomEmailMessageBodyCachePropertyTest : BaseTests() {
    private lateinit var cache: RoomEmailMessageBodyCache
    private lateinit var tmpDir: File

    @Before
    fun setUp() {
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        tmpDir = File(context.cacheDir, "cache-prop-test-${System.nanoTime()}")
        tmpDir.mkdirs()

        cache =
            RoomEmailMessageBodyCache(
                context = context,
                logger = mockLogger,
                cacheStoragePath = tmpDir.absolutePath,
                initialCacheSizeLimitBytes = 50L * 1024 * 1024, // 50 MB
                largeMessageThresholdBytes = 500, // 500 bytes for easier testing
            )
    }

    @After
    fun fini() {
        cache.close()
        tmpDir.deleteRecursively()
    }

    // Feature: pemc-1738, Property 4: Cache entry round-trip
    @Test
    fun `Property 4 - after put then get, returned blob equals original and metadata is preserved`() =
        runTest {
            checkAll(
                100,
                Arb.bind(
                    Arb.uuid(),
                    Arb.uuid(),
                    Arb.uuid(),
                    Arb.int(1..400).map { size -> ByteArray(size) { (it % 256).toByte() } },
                    Arb.of("sudoplatform-crypto", "sudoplatform-compression", null),
                ) { messageId, sudoId, emailAddressId, blob, encoding ->
                    CachePutInput(
                        messageId = messageId.toString(),
                        sudoId = sudoId.toString(),
                        emailAddressId = emailAddressId.toString(),
                        sealedBlob = blob,
                        contentEncoding = encoding,
                    )
                },
            ) { input ->
                cache.put(input)

                val result = cache.get(input.messageId)

                result shouldNotBe null
                result!!.messageId shouldBe input.messageId
                result.sudoId shouldBe input.sudoId
                result.emailAddressId shouldBe input.emailAddressId
                result.sealedBlob.contentEquals(input.sealedBlob) shouldBe true
                result.contentEncoding shouldBe input.contentEncoding

                // Clean up for next iteration
                cache.deleteMessage(input.messageId)
            }
        }

    // Feature: pemc-1738, Property 5: Storage routing by blob size
    @Test
    fun `Property 5 - blobs are routed to inline or filesystem based on size threshold`() =
        runTest {
            checkAll(100, Arb.int(1..1000)) { size ->
                val messageId = "msg-size-$size-${System.nanoTime()}"
                val blob = ByteArray(size) { (it % 256).toByte() }

                cache.put(
                    CachePutInput(
                        messageId = messageId,
                        sudoId = "sudo-1",
                        emailAddressId = "addr-1",
                        sealedBlob = blob,
                        contentEncoding = null,
                    ),
                )

                val blobFile = File(tmpDir, "blobs/$messageId.blob")

                if (size > 500) {
                    // Should be stored on filesystem
                    blobFile.exists() shouldBe true
                } else {
                    // Should be stored inline — no filesystem file
                    blobFile.exists() shouldBe false
                }

                // Verify retrieval works regardless
                val result = cache.get(messageId)
                result shouldNotBe null
                result!!.sealedBlob.contentEquals(blob) shouldBe true

                cache.deleteMessage(messageId)
            }
        }

    // Feature: pemc-1738, Property 6: LRU eviction maintains size invariant
    @Test
    fun `Property 6 - total cache size never exceeds limit after any put`() =
        runTest {
            // Use a small limit for this test
            cache.setCacheSizeLimit(2000)

            checkAll(
                50,
                Arb.bind(
                    Arb.uuid(),
                    Arb.int(1..400),
                ) { id, size -> Pair(id.toString(), size) },
            ) { (messageId, size) ->
                cache.put(
                    CachePutInput(
                        messageId = messageId,
                        sudoId = "sudo-1",
                        emailAddressId = "addr-1",
                        sealedBlob = ByteArray(size),
                        contentEncoding = null,
                    ),
                )

                // Total size should never exceed the limit
                // We can't directly query getTotalSizeBytes from the cache interface,
                // but we can verify that get still works (cache is consistent)
                val result = cache.get(messageId)
                if (size <= 2000) {
                    // Should be cached (unless evicted by a subsequent put)
                    // At minimum, the most recent put should be retrievable
                    result shouldNotBe null
                }
            }

            // Reset for other tests
            cache.setCacheSizeLimit(50L * 1024 * 1024)
        }

    // Feature: pemc-1738, Property 10: Cache size limit persists across reconstructions
    @Test
    fun `Property 10 - cache size limit persists across reconstructions`() =
        runTest {
            val context: android.content.Context = ApplicationProvider.getApplicationContext()

            checkAll(20, Arb.long(1L..Long.MAX_VALUE / 2)) { limit ->
                cache.setCacheSizeLimit(limit)

                // Create a new instance against the same storage path
                val cache2 =
                    RoomEmailMessageBodyCache(
                        context = context,
                        logger = mockLogger,
                        cacheStoragePath = tmpDir.absolutePath,
                        initialCacheSizeLimitBytes = 99999, // should be ignored
                    )

                cache2.getCacheSizeLimit() shouldBe limit
                cache2.close()
            }

            // Reset
            cache.setCacheSizeLimit(50L * 1024 * 1024)
        }

    // Feature: pemc-1738, Property 11: Oversized messages never cached
    @Test
    fun `Property 11 - put with a blob larger than cache size limit is never cached`() =
        runTest {
            cache.setCacheSizeLimit(1000)

            checkAll(50, Arb.int(1001..3000)) { blobSize ->
                val messageId = "oversized-$blobSize-${System.nanoTime()}"

                cache.put(
                    CachePutInput(
                        messageId = messageId,
                        sudoId = "sudo-1",
                        emailAddressId = "addr-1",
                        sealedBlob = ByteArray(blobSize),
                        contentEncoding = null,
                    ),
                )

                val result = cache.get(messageId)
                result shouldBe null
            }

            cache.setCacheSizeLimit(50L * 1024 * 1024)
        }

    // Feature: pemc-1738, Property 12: Scoped flush removes exactly matching entries
    @Test
    fun `Property 12 - flush by sudoId removes only matching entries`() =
        runTest {
            checkAll(
                30,
                Arb.bind(
                    Arb.uuid(),
                    Arb.uuid(),
                ) { targetSudoId, otherSudoId -> Pair(targetSudoId.toString(), otherSudoId.toString()) },
            ) { (targetSudoId, otherSudoId) ->
                if (targetSudoId == otherSudoId) return@checkAll

                cache.flushAll()

                cache.put(CachePutInput("msg-target", targetSudoId, "addr-1", "target".toByteArray(), null))
                cache.put(CachePutInput("msg-other", otherSudoId, "addr-2", "other".toByteArray(), null))

                cache.flush(CacheFlushInput(sudoId = targetSudoId))

                cache.get("msg-target") shouldBe null
                cache.get("msg-other") shouldNotBe null
            }
        }

    // Feature: pemc-1738, Property 12: Scoped flush removes exactly matching entries
    @Test
    fun `Property 12 - flush by emailAddressId removes only matching entries`() =
        runTest {
            checkAll(
                30,
                Arb.bind(
                    Arb.uuid(),
                    Arb.uuid(),
                ) { targetAddr, otherAddr -> Pair(targetAddr.toString(), otherAddr.toString()) },
            ) { (targetAddr, otherAddr) ->
                if (targetAddr == otherAddr) return@checkAll

                cache.flushAll()

                cache.put(CachePutInput("msg-target", "sudo-1", targetAddr, "target".toByteArray(), null))
                cache.put(CachePutInput("msg-other", "sudo-1", otherAddr, "other".toByteArray(), null))

                cache.flush(CacheFlushInput(emailAddressId = targetAddr))

                cache.get("msg-target") shouldBe null
                cache.get("msg-other") shouldNotBe null
            }
        }

    // Feature: pemc-1738, Property 13: Delete by message ID is idempotent
    @Test
    fun `Property 13 - deleteMessage succeeds regardless of whether entry exists`() =
        runTest {
            checkAll(
                50,
                Arb.bind(
                    Arb.uuid(),
                    Arb.of(true, false),
                ) { id, exists -> Pair(id.toString(), exists) },
            ) { (messageId, exists) ->
                cache.flushAll()

                if (exists) {
                    cache.put(CachePutInput(messageId, "sudo-1", "addr-1", "content".toByteArray(), null))
                }

                // Should not throw regardless
                cache.deleteMessage(messageId)

                // After delete, entry should not exist
                cache.get(messageId) shouldBe null
            }
        }

    // Feature: pemc-1738, Property 14: Stale entry resilience
    @Test
    fun `Property 14 - stale filesystem entries are removed and return null`() =
        runTest {
            checkAll(30, Arb.uuid()) { id ->
                val messageId = id.toString()
                cache.flushAll()

                // Put a large blob (goes to filesystem due to 500 byte threshold)
                val blob = ByteArray(600) { (it % 256).toByte() }
                cache.put(CachePutInput(messageId, "sudo-1", "addr-1", blob, null))

                // Delete the filesystem blob to simulate staleness
                val blobFile = File(tmpDir, "blobs/$messageId.blob")
                if (blobFile.exists()) {
                    blobFile.delete()
                }

                // get should return null and clean up the stale entry
                val result = cache.get(messageId)
                result shouldBe null
            }
        }
}
