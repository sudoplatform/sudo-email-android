/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [SudoEmailClient.getConfigurationData]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetConfigurationDataTest : BaseTests() {
    private val queryResponse by before {
        DataFactory.getEmailConfigQueryResponse()
    }

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                getEmailConfigQuery()
            } doAnswer {
                queryResponse
            }
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val mockServiceKeyManager by before {
        DefaultServiceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking { upload(any(), anyString(), anyOrNull()) } doReturn "42"
        }
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        DefaultSealingService(
            mockServiceKeyManager,
            mockLogger,
        )
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>()
    }

    private val client by before {
        DefaultSudoEmailClient(
            mockContext,
            mockApiClient,
            mockUserClient,
            mockLogger,
            mockServiceKeyManager,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
            "region",
            "identityBucket",
            "transientBucket",
            null,
            mockS3Client,
            mockS3Client,
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
            mockEmailMessageProcessor,
            mockEmailCryptoService,
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

            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `getConfigurationData() should throw when unknown error occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getEmailConfigQuery()
                } doThrow RuntimeException("Mock Runtime Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailConfigurationException.UnknownException> {
                        client.getConfigurationData()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `getConfigurationData() should throw when no config data is returned`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getEmailConfigQuery()
                }.thenAnswer {
                    GraphQLResponse(null, null)
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailConfigurationException.FailedException> {
                        client.getConfigurationData()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `getConfigurationData() should throw when query response contains errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    null,
                    null,
                    null,
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailConfigQuery()
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailConfigurationException.FailedException> {
                        client.getConfigurationData()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `getConfigurationData() should not block coroutine cancellation exception`() =
        runBlocking<Unit> {
            mockApiClient.stub {
                onBlocking {
                    getEmailConfigQuery()
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            shouldThrow<CancellationException> {
                client.getConfigurationData()
            }

            verify(mockApiClient).getEmailConfigQuery()
        }
}
