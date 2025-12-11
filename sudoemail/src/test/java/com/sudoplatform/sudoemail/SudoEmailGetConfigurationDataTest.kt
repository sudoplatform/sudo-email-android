/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.getConfigurationData]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetConfigurationDataTest : BaseTests() {
    private val configurationDataEntity by before {
        EntityDataFactory.getConfigurationDataEntity()
    }

    private val mockConfigurationDataService by before {
        mock<ConfigurationDataService>().stub {
            onBlocking {
                getConfigurationData()
            } doReturn configurationDataEntity
        }
    }

    private val mockServiceKeyManager by before {
        DefaultServiceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
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
            configurationDataService = mockConfigurationDataService,
            useCaseFactory = mockUseCaseFactory,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockContext,
            mockUserClient,
            mockKeyManager,
            mockApiClient,
            mockS3Client,
            mockConfigurationDataService,
        )
    }

    @Test
    fun `getConfigurationData() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getConfigurationData()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            with(result) {
                deleteEmailMessagesLimit shouldBe 10
                updateEmailMessagesLimit shouldBe 5
                emailMessageMaxInboundMessageSize shouldBe 200
                emailMessageMaxOutboundMessageSize shouldBe 100
                emailMessageRecipientsLimit shouldBe 5
                encryptedEmailMessageRecipientsLimit shouldBe 10
                prohibitedFileExtensions shouldBe listOf(".js", ".exe", ".lib")
            }

            verify(mockConfigurationDataService).getConfigurationData()
        }

    @Test
    fun `getConfigurationData() should throw when unknown error occurs`() =
        runTest {
            mockConfigurationDataService.stub {
                onBlocking {
                    getConfigurationData()
                } doThrow SudoEmailClient.EmailConfigurationException.FailedException("Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailConfigurationException.FailedException> {
                        client.getConfigurationData()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockConfigurationDataService).getConfigurationData()
        }
}
