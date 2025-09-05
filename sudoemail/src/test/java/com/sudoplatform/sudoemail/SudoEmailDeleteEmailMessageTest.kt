/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [SudoEmailClient.deleteEmailMessage]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailDeleteEmailMessageTest : BaseTests() {
    private val mutationResponse by before {
        DataFactory.deleteEmailMessagesMutationResponse(listOf("id"))
    }

    private val mutationEmptyResponse by before {
        DataFactory.deleteEmailMessagesMutationResponse(listOf())
    }

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                deleteEmailMessagesMutation(
                    any(),
                )
            } doAnswer {
                mutationEmptyResponse
            }
            onBlocking {
                getEmailConfigQuery()
            } doAnswer {
                DataFactory.getEmailConfigQueryResponse()
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
        mock<S3Client>()
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
            context,
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
            mockUserClient,
            mockKeyManager,
            mockApiClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `deleteEmailMessage() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteEmailMessage("id")
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result?.id?.isBlank() shouldBe false

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `deleteEmailMessage() should return null result when delete operation fails`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(
                        any(),
                    )
                } doAnswer {
                    mutationResponse
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteEmailMessage("id")
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe null

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `deleteEmailMessage() should throw when email mutation response is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, null)
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.deleteEmailMessage("id")
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `deleteEmailMessage() should throw when response has various errors`() =
        runTest {
            testException<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException>("EmailMessageNotFound")
            testException<SudoEmailClient.EmailMessageException.FailedException>("blah")

            verify(mockApiClient, times(2)).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id")
                },
            )
            verify(mockApiClient, times(2)).getEmailConfigQuery()
        }

    @Test
    fun `deleteEmailMessage() should throw when http error occurs`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "mock",
                    null,
                    null,
                    mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
                )
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.deleteEmailMessage("id")
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `deleteEmailMessage() should throw when unknown error occurs()`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(
                        any(),
                    )
                } doThrow RuntimeException("Mock Runtime Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                        client.deleteEmailMessage("id")
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `deleteEmailMessage() should not block coroutine cancellation exception`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(
                        any(),
                    )
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<CancellationException> {
                        client.deleteEmailMessage("id")
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    private inline fun <reified T : Exception> testException(apolloError: String) =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to apolloError),
                )
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<T> {
                        client.deleteEmailMessage("id")
                    }
                }
            deferredResult.start()
            deferredResult.await()
        }
}
