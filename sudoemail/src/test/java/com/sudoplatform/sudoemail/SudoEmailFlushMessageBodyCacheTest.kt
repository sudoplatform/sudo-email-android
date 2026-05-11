/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.EmailMessageBodyCache
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.FlushMessageBodyCacheInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.flushMessageBodyCache]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailFlushMessageBodyCacheTest : BaseTests() {
    private val mockServiceKeyManager by before {
        DefaultServiceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
    }

    private val mockEmailMessageBodyCache by before {
        mock<EmailMessageBodyCache>()
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>()
    }

    private val client by before {
        DefaultSudoEmailClient(
            context = mockContext,
            serviceKeyManager = mockServiceKeyManager,
            apiClient = mockApiClient,
            sudoUserClient = mockUserClient,
            logger = mockLogger,
            region = "region",
            emailBucket = "identityBucket",
            transientBucket = "transientBucket",
            notificationHandler = null,
            s3TransientClient = mockS3Client,
            s3EmailClient = mockS3Client,
            emailMessageBodyCache = mockEmailMessageBodyCache,
            useCaseFactory = mockUseCaseFactory,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockEmailMessageBodyCache,
        )
    }

    @Test
    fun `flushMessageBodyCache() should delegate to cache flush with sudoId`() =
        runTest {
            val input = FlushMessageBodyCacheInput(sudoId = "test-sudo-id")

            client.flushMessageBodyCache(input)

            verify(mockEmailMessageBodyCache).flush(
                check { cacheInput ->
                    cacheInput.sudoId shouldBe "test-sudo-id"
                    cacheInput.emailAddressId shouldBe null
                },
            )
        }

    @Test
    fun `flushMessageBodyCache() should delegate to cache flush with emailAddressId`() =
        runTest {
            val input = FlushMessageBodyCacheInput(emailAddressId = "test-email-address-id")

            client.flushMessageBodyCache(input)

            verify(mockEmailMessageBodyCache).flush(
                check { cacheInput ->
                    cacheInput.sudoId shouldBe null
                    cacheInput.emailAddressId shouldBe "test-email-address-id"
                },
            )
        }

    @Test
    fun `flushMessageBodyCache() should throw when cache flush throws IllegalArgumentException`() =
        runTest {
            mockEmailMessageBodyCache.stub {
                onBlocking { flush(any()) } doThrow IllegalArgumentException("Neither sudoId nor emailAddressId provided")
            }

            val input = FlushMessageBodyCacheInput()

            shouldThrow<IllegalArgumentException> {
                client.flushMessageBodyCache(input)
            }

            verify(mockEmailMessageBodyCache).flush(any())
        }
}
