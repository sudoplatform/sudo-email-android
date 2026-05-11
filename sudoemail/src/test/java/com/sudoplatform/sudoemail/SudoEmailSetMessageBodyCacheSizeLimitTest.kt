/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.EmailMessageBodyCache
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.setMessageBodyCacheSizeLimit]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailSetMessageBodyCacheSizeLimitTest : BaseTests() {
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
    fun `setMessageBodyCacheSizeLimit() should delegate to cache setCacheSizeLimit`() =
        runTest {
            client.setMessageBodyCacheSizeLimit(500L * 1024 * 1024)

            verify(mockEmailMessageBodyCache).setCacheSizeLimit(500L * 1024 * 1024)
        }

    @Test
    fun `setMessageBodyCacheSizeLimit() should delegate zero value to disable cache`() =
        runTest {
            client.setMessageBodyCacheSizeLimit(0L)

            verify(mockEmailMessageBodyCache).setCacheSizeLimit(0L)
        }

    @Test
    fun `setMessageBodyCacheSizeLimit() should throw when cache throws IllegalArgumentException`() =
        runTest {
            mockEmailMessageBodyCache.stub {
                onBlocking { setCacheSizeLimit(any()) } doThrow IllegalArgumentException("Negative value")
            }

            shouldThrow<IllegalArgumentException> {
                client.setMessageBodyCacheSizeLimit(-1L)
            }

            verify(mockEmailMessageBodyCache).setCacheSizeLimit(-1L)
        }
}
