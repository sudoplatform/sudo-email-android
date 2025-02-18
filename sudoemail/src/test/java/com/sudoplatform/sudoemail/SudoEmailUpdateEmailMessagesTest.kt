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
import com.apollographql.apollo3.api.Optional
import com.sudoplatform.sudoemail.graphql.GetEmailConfigQuery
import com.sudoplatform.sudoemail.graphql.UpdateEmailMessagesMutation
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.EmailMessageOperationFailureResult
import com.sudoplatform.sudoemail.types.UpdatedEmailMessageResult.UpdatedEmailMessageSuccess
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailMessagesInput
import com.sudoplatform.sudoemail.types.transformers.toDate
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.util.UUID
import java.util.concurrent.CancellationException
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailMessagesInput as UpdateEmailMessagesRequest

/**
 * Test the correct operation of [SudoEmailClient.updateEmailMessages]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailUpdateEmailMessagesTest : BaseTests() {

    private val input by before {
        UpdateEmailMessagesInput(
            listOf("id1", "id2"),
            UpdateEmailMessagesInput.UpdatableValues("folderId2", true),
        )
    }

    private val mutationSuccessResponse by before {
        JSONObject(
            """
                {
                    'updateEmailMessagesV2': {
                        '__typename': 'UpdateEmailMessagesResult',
                        'status': 'SUCCESS',
                        'failedMessages': null,
                        'successMessages': null
                    }
                }
            """.trimIndent(),
        )
    }

    private val mutationFailedResponse by before {
        JSONObject(
            """
                {
                    'updateEmailMessagesV2': {
                        '__typename': 'UpdateEmailMessagesResult',
                        'status': 'FAILED',
                        'failedMessages': null,
                        'successMessages': null
                    }
                }
            """.trimIndent(),
        )
    }

    private val mutationPartialResponse by before {
        JSONObject(
            """
                {
                    'updateEmailMessagesV2': {
                        '__typename': 'UpdateEmailMessagesResult',
                        'status': 'PARTIAL',
                        'failedMessages': [{
                            '__typename': 'typename',
                            'id': 'id2',
                            'errorType': 'error'
                        }],
                        'successMessages': [{
                            '__typename': 'typename',
                            'id': 'id1',
                            'createdAtEpochMs': 1.0,
                            'updatedAtEpochMs': 2.0
                        }]
                    }
                }
            """.trimIndent(),
        )
    }

    private val mockUpdateEmailMessagesLimit = 10

    private val getEmailConfigQueryResponse by before {
        JSONObject(
            """
                {
                    'getEmailConfig': {
                        '__typename': 'EmailConfigurationData',
                        'deleteEmailMessagesLimit': 10,
                        'updateEmailMessagesLimit': $mockUpdateEmailMessagesLimit,
                        'emailMessageMaxInboundMessageSize': 200,
                        'emailMessageMaxOutboundMessageSize': 100,
                        'emailMessageRecipientsLimit': 5,
                        'encryptedEmailMessageRecipientsLimit': 10
                    }
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
                    argThat { this.query.equals(UpdateEmailMessagesMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(mutationSuccessResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
            }
            on {
                query<String>(
                    argThat { this.query.equals(GetEmailConfigQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(getEmailConfigQueryResponse.toString(), null),
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
    fun `updateEmailMessages() should return success result when no error present`() =
        runTest {
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.updateEmailMessages(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe UpdateEmailMessagesMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as UpdateEmailMessagesRequest
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                    mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                    mutationInput.values.seen shouldBe Optional.Present(true)
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
        }

    @Test
    fun `updateEmailMessages() should return failure result when no error present`() =
        runTest {
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(UpdateEmailMessagesMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(mutationFailedResponse.toString(), null),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.updateEmailMessages(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.FAILURE

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe UpdateEmailMessagesMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as UpdateEmailMessagesRequest
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                    mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                    mutationInput.values.seen shouldBe Optional.Present(true)
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
        }

    @Test
    fun `updateEmailMessages() should return partial result when no error present`() =
        runTest {
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(UpdateEmailMessagesMutation.OPERATION_DOCUMENT) },
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
                client.updateEmailMessages(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.PARTIAL
            result.successValues shouldBe listOf(
                UpdatedEmailMessageSuccess(
                    "id1",
                    1.0.toDate(),
                    2.0.toDate(),
                ),
            )
            result.failureValues shouldBe listOf(
                EmailMessageOperationFailureResult(
                    "id2",
                    "error",
                ),
            )

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe UpdateEmailMessagesMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as UpdateEmailMessagesRequest
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                    mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                    mutationInput.values.seen shouldBe Optional.Present(true)
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
        }

    @Test
    fun `updateEmailMessages() should throw LimitExceededException when passed too many ids`() =
        runTest {
            val ids = mutableListOf<String>()
            for (i in 0..mockUpdateEmailMessagesLimit + 1) {
                ids.add(UUID.randomUUID().toString())
            }
            val input = UpdateEmailMessagesInput(
                ids,
                UpdateEmailMessagesInput.UpdatableValues("folderId2", true),
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
                    client.updateEmailMessages(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory, times(0)).mutate<String>(
                check {
                    it.query shouldBe UpdateEmailMessagesMutation.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
        }

    @Test
    fun `updateEmailMessages() should throw when email mutation response is null`() =
        runTest {
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(UpdateEmailMessagesMutation.OPERATION_DOCUMENT) },
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
                    client.updateEmailMessages(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe UpdateEmailMessagesMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as UpdateEmailMessagesRequest
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                    mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                    mutationInput.values.seen shouldBe Optional.Present(true)
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
        }

    @Test
    fun `updateEmailMessages() should throw when response has unexpected error`() =
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
                        argThat { this.query.equals(UpdateEmailMessagesMutation.OPERATION_DOCUMENT) },
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
                    client.updateEmailMessages(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe UpdateEmailMessagesMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as UpdateEmailMessagesRequest
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                    mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                    mutationInput.values.seen shouldBe Optional.Present(true)
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
        }

    @Test
    fun `updateEmailMessages() should throw when http error occurs`() = runTest {
        val testError = GraphQLResponse.Error(
            "mock",
            null,
            null,
            mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
        )
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(UpdateEmailMessagesMutation.OPERATION_DOCUMENT) },
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
                client.updateEmailMessages(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe UpdateEmailMessagesMutation.OPERATION_DOCUMENT
                val mutationInput = it.variables["input"] as UpdateEmailMessagesRequest
                mutationInput.messageIds shouldBe listOf("id1", "id2")
                mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                mutationInput.values.seen shouldBe Optional.Present(true)
            },
            any(),
            any(),
        )
        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
    }

    @Test
    fun `updateEmailMessages() should throw when unknown error occurs()`() = runTest {
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(UpdateEmailMessagesMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.updateEmailMessages(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe UpdateEmailMessagesMutation.OPERATION_DOCUMENT
                val mutationInput = it.variables["input"] as UpdateEmailMessagesRequest
                mutationInput.messageIds shouldBe listOf("id1", "id2")
                mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                mutationInput.values.seen shouldBe Optional.Present(true)
            },
            any(),
            any(),
        )
        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
    }

    @Test
    fun `updateEmailMessage() should not block coroutine cancellation exception`() =
        runTest {
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(UpdateEmailMessagesMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<CancellationException> {
                    client.updateEmailMessages(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe UpdateEmailMessagesMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as UpdateEmailMessagesRequest
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                    mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                    mutationInput.values.seen shouldBe Optional.Present(true)
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
        }
}
