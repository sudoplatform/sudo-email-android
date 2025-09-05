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
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.DeleteEmailMessageSuccessResult
import com.sudoplatform.sudoemail.types.EmailMessageOperationFailureResult
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
import java.util.UUID
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [SudoEmailClient.deleteEmailMessages]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailDeleteEmailMessagesTest : BaseTests() {
    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockDeleteEmailMessagesLimit = 10

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                deleteEmailMessagesMutation(
                    any(),
                )
            } doAnswer {
                DataFactory.deleteEmailMessagesMutationResponse(listOf("id1", "id2"))
            }
            onBlocking {
                getEmailConfigQuery()
            } doAnswer {
                DataFactory.getEmailConfigQueryResponse(
                    deleteEmailMessagesLimit = mockDeleteEmailMessagesLimit,
                )
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
    fun `deleteEmailMessages() should return success result when no error present`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(
                        any(),
                    )
                } doAnswer {
                    DataFactory.deleteEmailMessagesMutationResponse(emptyList())
                }
            }
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteEmailMessages(listOf("id1", "id2"))
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id1", "id2")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `deleteEmailMessages() should return failure result when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteEmailMessages(listOf("id1", "id2"))
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.FAILURE

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id1", "id2")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `deleteEmailMessages() should return partial result when no error present`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(
                        any(),
                    )
                }.thenAnswer {
                    DataFactory.deleteEmailMessagesMutationResponse(listOf("id1"))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteEmailMessages(listOf("id1", "id2"))
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.PARTIAL
            result.successValues shouldBe listOf(DeleteEmailMessageSuccessResult("id2"))
            result.failureValues shouldBe listOf(EmailMessageOperationFailureResult("id1", "Failed to delete email message"))

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id1", "id2")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `deleteEmailMessages() should throw LimitExceededException when passed too many ids`() =
        runTest {
            val ids = mutableListOf<String>()
            for (i in 0..mockDeleteEmailMessagesLimit + 1) {
                ids.add(UUID.randomUUID().toString())
            }
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
                        client.deleteEmailMessages(ids)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient, times(0)).deleteEmailMessagesMutation(
                any(),
            )
        }

    @Test
    fun `deleteEmailMessages() should throw when email mutation response is null`() =
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
                        client.deleteEmailMessages(listOf("id1", "id2"))
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id1", "id2")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `deleteEmailMessages() should throw when response has unexpected error`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "mock",
                    null,
                    null,
                    mapOf("httpStatus" to "blah"),
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
                    shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                        client.deleteEmailMessages(listOf("id1", "id2"))
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id1", "id2")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `deleteEmailMessages() should throw when http error occurs`() =
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
                        client.deleteEmailMessages(listOf("id1", "id2"))
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id1", "id2")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `deleteEmailMessages() should throw when unknown error occurs()`() =
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
                        client.deleteEmailMessages(listOf("id1", "id2"))
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id1", "id2")
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
                        client.deleteEmailMessages(listOf("id1", "id2"))
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe listOf("id1", "id2")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }
}
