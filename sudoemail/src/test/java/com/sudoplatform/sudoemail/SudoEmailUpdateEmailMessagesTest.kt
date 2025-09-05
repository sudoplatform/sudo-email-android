/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amplifyframework.api.graphql.GraphQLResponse
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.graphql.fragment.UpdateEmailMessagesResult
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailMessagesStatus
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
        DataFactory.updateEmailMessagesMutationResponse()
    }

    private val mutationFailedResponse by before {
        DataFactory.updateEmailMessagesMutationResponse(UpdateEmailMessagesStatus.FAILED)
    }

    private val mutationPartialResponse by before {
        DataFactory.updateEmailMessagesMutationResponse(
            UpdateEmailMessagesStatus.PARTIAL,
            failedMessages =
                listOf(
                    UpdateEmailMessagesResult.FailedMessage(
                        "id2",
                        "error",
                    ),
                ),
            successMessages =
                listOf(
                    UpdateEmailMessagesResult.SuccessMessage(
                        "id1",
                        1.0,
                        2.0,
                    ),
                ),
        )
    }

    private val mockUpdateEmailMessagesLimit = 10

    private val getEmailConfigQueryResponse by before {
        DataFactory.getEmailConfigQueryResponse(
            updateEmailMessagesLimit = mockUpdateEmailMessagesLimit,
        )
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
                updateEmailMessagesMutation(
                    any(),
                )
            } doAnswer {
                mutationSuccessResponse
            }
            onBlocking {
                getEmailConfigQuery()
            } doAnswer {
                getEmailConfigQueryResponse
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
    fun `updateEmailMessages() should return success result when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.updateEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockApiClient).updateEmailMessagesMutation(
                check { mutationInput ->
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                    mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                    mutationInput.values.seen shouldBe Optional.Present(true)
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `updateEmailMessages() should return failure result when no error present`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(
                        any(),
                    )
                } doAnswer {
                    mutationFailedResponse
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.updateEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.FAILURE

            verify(mockApiClient).updateEmailMessagesMutation(
                check { mutationInput ->
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                    mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                    mutationInput.values.seen shouldBe Optional.Present(true)
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `updateEmailMessages() should return partial result when no error present`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(
                        any(),
                    )
                }.thenAnswer {
                    mutationPartialResponse
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.updateEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.PARTIAL
            result.successValues shouldBe
                listOf(
                    UpdatedEmailMessageSuccess(
                        "id1",
                        1.0.toDate(),
                        2.0.toDate(),
                    ),
                )
            result.failureValues shouldBe
                listOf(
                    EmailMessageOperationFailureResult(
                        "id2",
                        "error",
                    ),
                )

            verify(mockApiClient).updateEmailMessagesMutation(
                check { mutationInput ->
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                    mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                    mutationInput.values.seen shouldBe Optional.Present(true)
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `updateEmailMessages() should throw LimitExceededException when passed too many ids`() =
        runTest {
            val ids = mutableListOf<String>()
            for (i in 0..mockUpdateEmailMessagesLimit + 1) {
                ids.add(UUID.randomUUID().toString())
            }
            val input =
                UpdateEmailMessagesInput(
                    ids,
                    UpdateEmailMessagesInput.UpdatableValues("folderId2", true),
                )
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
                        client.updateEmailMessages(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient, times(0)).updateEmailMessagesMutation(
                any(),
            )
        }

    @Test
    fun `updateEmailMessages() should throw when email mutation response is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, null)
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.updateEmailMessages(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).updateEmailMessagesMutation(
                check { mutationInput ->
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                    mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                    mutationInput.values.seen shouldBe Optional.Present(true)
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `updateEmailMessages() should throw when response has unexpected error`() =
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
                    updateEmailMessagesMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                        client.updateEmailMessages(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).updateEmailMessagesMutation(
                check { mutationInput ->
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                    mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                    mutationInput.values.seen shouldBe Optional.Present(true)
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `updateEmailMessages() should throw when http error occurs`() =
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
                    updateEmailMessagesMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.updateEmailMessages(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).updateEmailMessagesMutation(
                check { mutationInput ->
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                    mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                    mutationInput.values.seen shouldBe Optional.Present(true)
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `updateEmailMessages() should throw when unknown error occurs()`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(
                        any(),
                    )
                } doThrow RuntimeException("Mock Runtime Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                        client.updateEmailMessages(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).updateEmailMessagesMutation(
                check { mutationInput ->
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                    mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                    mutationInput.values.seen shouldBe Optional.Present(true)
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `updateEmailMessage() should not block coroutine cancellation exception`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(
                        any(),
                    )
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<CancellationException> {
                        client.updateEmailMessages(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).updateEmailMessagesMutation(
                check { mutationInput ->
                    mutationInput.messageIds shouldBe listOf("id1", "id2")
                    mutationInput.values.folderId shouldBe Optional.Present("folderId2")
                    mutationInput.values.seen shouldBe Optional.Present(true)
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
        }
}
