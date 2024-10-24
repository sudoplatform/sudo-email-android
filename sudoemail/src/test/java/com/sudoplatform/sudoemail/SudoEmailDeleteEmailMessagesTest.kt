/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudoemail.graphql.DeleteEmailMessagesMutation
import com.sudoplatform.sudoemail.graphql.type.DeleteEmailMessagesInput
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
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [SudoEmailClient.deleteEmailMessages]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailDeleteEmailMessagesTest : BaseTests() {

    private val mutationResponse by before {
        JSONObject(
            """
                {
                    'deleteEmailMessages': ['id1', 'id2']
                }
            """.trimIndent(),
        )
    }

    private val mutationPartialResponse by before {
        JSONObject(
            """
                {
                    'deleteEmailMessages': ['id1']
                }
            """.trimIndent(),
        )
    }

    private val mutationEmptyResponse by before {
        JSONObject(
            """
                {
                    'deleteEmailMessages': []
                }
            """.trimIndent(),
        )
    }

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(DeleteEmailMessagesMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(mutationResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
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
            GraphQLClient(mockApiCategory),
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
            mockApiCategory,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `deleteEmailMessages() should return success result when no error present`() =
        runTest {
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(DeleteEmailMessagesMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(mutationEmptyResponse.toString(), null),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.deleteEmailMessages(listOf("id1", "id2"))
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe DeleteEmailMessagesMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as DeleteEmailMessagesInput
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                },
                any(),
                any(),
            )
        }

    @Test
    fun `deleteEmailMessages() should return failure result when no error present`() =
        runTest {
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.deleteEmailMessages(listOf("id1", "id2"))
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.FAILURE

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe DeleteEmailMessagesMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as DeleteEmailMessagesInput
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                },
                any(),
                any(),
            )
        }

    @Test
    fun `deleteEmailMessages() should return partial result when no error present`() =
        runTest {
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(DeleteEmailMessagesMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                }.thenAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(mutationPartialResponse.toString(), null),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.deleteEmailMessages(listOf("id1", "id2"))
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.PARTIAL
            result.successValues shouldBe listOf(DeleteEmailMessageSuccessResult("id2"))
            result.failureValues shouldBe listOf(EmailMessageOperationFailureResult("id1", "Failed to delete email message"))

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe DeleteEmailMessagesMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as DeleteEmailMessagesInput
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                },
                any(),
                any(),
            )
        }

    @Test
    fun `deleteEmailMessages() should throw when email mutation response is null`() =
        runTest {
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(DeleteEmailMessagesMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                }.thenAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(null, null),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                    client.deleteEmailMessages(listOf("id1", "id2"))
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe DeleteEmailMessagesMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as DeleteEmailMessagesInput
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                },
                any(),
                any(),
            )
        }

    @Test
    fun `deleteEmailMessages() should throw when response has unexpected error`() =
        runTest {
            val testError = GraphQLResponse.Error(
                "mock",
                null,
                null,
                mapOf("httpStatus" to "blah"),
            )
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(DeleteEmailMessagesMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                }.thenAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(null, listOf(testError)),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                    client.deleteEmailMessages(listOf("id1", "id2"))
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe DeleteEmailMessagesMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as DeleteEmailMessagesInput
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                },
                any(),
                any(),
            )
        }

    @Test
    fun `deleteEmailMessages() should throw when http error occurs`() = runTest {
        val testError = GraphQLResponse.Error(
            "mock",
            null,
            null,
            mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
        )
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(DeleteEmailMessagesMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            }.thenAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(null, listOf(testError)),
                )
                mock<GraphQLOperation<String>>()
            }
        }
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.deleteEmailMessages(listOf("id1", "id2"))
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe DeleteEmailMessagesMutation.OPERATION_DOCUMENT
                val mutationInput = it.variables["input"] as DeleteEmailMessagesInput
                mutationInput.messageIds shouldBe listOf("id1", "id2")
            },
            any(),
            any(),
        )
    }

    @Test
    fun `deleteEmailMessages() should throw when unknown error occurs()`() = runTest {
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(DeleteEmailMessagesMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.deleteEmailMessages(listOf("id1", "id2"))
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe DeleteEmailMessagesMutation.OPERATION_DOCUMENT
                val mutationInput = it.variables["input"] as DeleteEmailMessagesInput
                mutationInput.messageIds shouldBe listOf("id1", "id2")
            },
            any(),
            any(),
        )
    }

    @Test
    fun `deleteEmailMessage() should not block coroutine cancellation exception`() =
        runTest {
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(DeleteEmailMessagesMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<CancellationException> {
                    client.deleteEmailMessages(listOf("id1", "id2"))
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe DeleteEmailMessagesMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as DeleteEmailMessagesInput
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                },
                any(),
                any(),
            )
        }
}
