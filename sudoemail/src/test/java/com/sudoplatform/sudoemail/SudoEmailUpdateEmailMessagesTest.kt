/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.UpdateEmailMessagesMutation
import com.sudoplatform.sudoemail.graphql.fragment.UpdateEmailMessagesResult
import com.sudoplatform.sudoemail.graphql.type.EmailMessageUpdateValuesInput
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailMessagesInput as UpdateEmailMessagesRequest

/**
 * Test the correct operation of [SudoEmailClient.updateEmailMessages]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailUpdateEmailMessagesTest : BaseTests() {

    private val input by before {
        UpdateEmailMessagesRequest.builder()
            .messageIds(listOf("id1", "id2"))
            .values(
                EmailMessageUpdateValuesInput.builder()
                    .folderId("folderId2")
                    .seen(true)
                    .build(),
            )
            .build()
    }

    private val mutationResult by before {
        UpdateEmailMessagesMutation.UpdateEmailMessagesV2(
            "typename",
            UpdateEmailMessagesMutation.UpdateEmailMessagesV2.Fragments(
                UpdateEmailMessagesResult(
                    "typename",
                    UpdateEmailMessagesStatus.SUCCESS,
                    null,
                    null,
                ),
            ),
        )
    }

    private val mutationResponse by before {
        Response.builder<UpdateEmailMessagesMutation.Data>(UpdateEmailMessagesMutation(input))
            .data(UpdateEmailMessagesMutation.Data(mutationResult))
            .build()
    }

    private val holder = CallbackHolder<UpdateEmailMessagesMutation.Data>()

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<UpdateEmailMessagesMutation>()) } doReturn holder.mutationOperation
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
            mockAppSyncClient,
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

    @Before
    fun init() {
        holder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockUserClient,
            mockKeyManager,
            mockAppSyncClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `updateEmailMessages() should return success result when no error present`() =
        runTest {
            holder.callback shouldBe null

            val input = UpdateEmailMessagesInput(
                listOf("id1", "id2"),
                UpdateEmailMessagesInput.UpdatableValues("folderId2", true),
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.updateEmailMessages(input)
            }
            deferredResult.start()

            delay(100L)
            holder.callback shouldNotBe null
            holder.callback?.onResponse(mutationResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockAppSyncClient)
                .mutate<
                    UpdateEmailMessagesMutation.Data,
                    UpdateEmailMessagesMutation,
                    UpdateEmailMessagesMutation.Variables,
                    >(
                    check {
                        it.variables().input().messageIds() shouldBe listOf("id1", "id2")
                        it.variables().input().values().folderId() shouldBe "folderId2"
                        it.variables().input().values().seen() shouldBe true
                    },
                )
        }

    @Test
    fun `updateEmailMessages() should return failure result when no error present`() =
        runTest {
            val mutationResult by before {
                UpdateEmailMessagesMutation.UpdateEmailMessagesV2(
                    "typename",
                    UpdateEmailMessagesMutation.UpdateEmailMessagesV2.Fragments(
                        UpdateEmailMessagesResult(
                            "typename",
                            UpdateEmailMessagesStatus.FAILED,
                            null,
                            null,
                        ),
                    ),
                )
            }

            val mutationResponse by before {
                Response.builder<UpdateEmailMessagesMutation.Data>(UpdateEmailMessagesMutation(input))
                    .data(UpdateEmailMessagesMutation.Data(mutationResult))
                    .build()
            }

            holder.callback shouldBe null

            val input = UpdateEmailMessagesInput(
                listOf("id1", "id2"),
                UpdateEmailMessagesInput.UpdatableValues("folderId2", true),
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.updateEmailMessages(input)
            }
            deferredResult.start()

            delay(100L)
            holder.callback shouldNotBe null
            holder.callback?.onResponse(mutationResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            result.status shouldBe BatchOperationStatus.FAILURE

            verify(mockAppSyncClient)
                .mutate<
                    UpdateEmailMessagesMutation.Data,
                    UpdateEmailMessagesMutation,
                    UpdateEmailMessagesMutation.Variables,
                    >(
                    check {
                        it.variables().input().messageIds() shouldBe listOf("id1", "id2")
                        it.variables().input().values().folderId() shouldBe "folderId2"
                        it.variables().input().values().seen() shouldBe true
                    },
                )
        }

    @Test
    fun `updateEmailMessages() should return partial result when no error present`() =
        runTest {
            val mutationResult by before {
                UpdateEmailMessagesMutation.UpdateEmailMessagesV2(
                    "typename",
                    UpdateEmailMessagesMutation.UpdateEmailMessagesV2.Fragments(
                        UpdateEmailMessagesResult(
                            "typename",
                            UpdateEmailMessagesStatus.PARTIAL,
                            listOf(
                                UpdateEmailMessagesResult.FailedMessage(
                                    "__typename",
                                    "id2",
                                    "error",
                                ),
                            ),
                            listOf(
                                UpdateEmailMessagesResult.SuccessMessage(
                                    "__typename",
                                    "id1",
                                    1.0,
                                    2.0,
                                ),
                            ),
                        ),
                    ),
                )
            }

            val mutationResponse by before {
                Response.builder<UpdateEmailMessagesMutation.Data>(UpdateEmailMessagesMutation(input))
                    .data(UpdateEmailMessagesMutation.Data(mutationResult))
                    .build()
            }

            holder.callback shouldBe null

            val input = UpdateEmailMessagesInput(
                listOf("id1", "id2"),
                UpdateEmailMessagesInput.UpdatableValues("folderId2", true),
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.updateEmailMessages(input)
            }
            deferredResult.start()

            delay(100L)
            holder.callback shouldNotBe null
            holder.callback?.onResponse(mutationResponse)

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

            verify(mockAppSyncClient)
                .mutate<
                    UpdateEmailMessagesMutation.Data,
                    UpdateEmailMessagesMutation,
                    UpdateEmailMessagesMutation.Variables,
                    >(
                    check {
                        it.variables().input().messageIds() shouldBe listOf("id1", "id2")
                        it.variables().input().values().folderId() shouldBe "folderId2"
                        it.variables().input().values().seen() shouldBe true
                    },
                )
        }

    @Test
    fun `updateEmailMessages() should throw when email mutation response is null`() =
        runTest {
            holder.callback shouldBe null

            val nullProvisionResponse by before {
                Response.builder<UpdateEmailMessagesMutation.Data>(UpdateEmailMessagesMutation(input))
                    .data(null)
                    .build()
            }

            val input = UpdateEmailMessagesInput(
                listOf("id1", "id2"),
                UpdateEmailMessagesInput.UpdatableValues("folderId2", true),
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                    client.updateEmailMessages(input)
                }
            }
            deferredResult.start()
            delay(100L)

            holder.callback shouldNotBe null
            holder.callback?.onResponse(nullProvisionResponse)

            deferredResult.await()

            verify(mockAppSyncClient)
                .mutate<
                    UpdateEmailMessagesMutation.Data,
                    UpdateEmailMessagesMutation,
                    UpdateEmailMessagesMutation.Variables,
                    >(
                    check {
                        it.variables().input().messageIds() shouldBe listOf("id1", "id2")
                        it.variables().input().values().folderId() shouldBe "folderId2"
                        it.variables().input().values().seen() shouldBe true
                    },
                )
        }

    @Test
    fun `updateEmailMessages() should throw when response has unexpected error`() =
        runTest {
            holder.callback = null

            val errorResponse by before {
                val error = com.apollographql.apollo.api.Error(
                    "mock",
                    emptyList(),
                    mapOf("errorType" to "blah"),
                )
                Response.builder<UpdateEmailMessagesMutation.Data>(UpdateEmailMessagesMutation(input))
                    .errors(listOf(error))
                    .build()
            }

            val input = UpdateEmailMessagesInput(
                listOf("id1", "id2"),
                UpdateEmailMessagesInput.UpdatableValues("folderId2", true),
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                    client.updateEmailMessages(input)
                }
            }
            deferredResult.start()

            delay(100L)
            holder.callback shouldNotBe null
            holder.callback?.onResponse(errorResponse)

            deferredResult.await()

            verify(mockAppSyncClient)
                .mutate<
                    UpdateEmailMessagesMutation.Data,
                    UpdateEmailMessagesMutation,
                    UpdateEmailMessagesMutation.Variables,
                    >(
                    check {
                        it.variables().input().messageIds() shouldBe listOf("id1", "id2")
                        it.variables().input().values().folderId() shouldBe "folderId2"
                        it.variables().input().values().seen() shouldBe true
                    },
                )
        }

    @Test
    fun `updateEmailMessages() should throw when http error occurs`() = runTest {
        holder.callback shouldBe null

        val input = UpdateEmailMessagesInput(
            listOf("id1", "id2"),
            UpdateEmailMessagesInput.UpdatableValues("folderId2", true),
        )
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.updateEmailMessages(input)
            }
        }
        deferredResult.start()
        delay(100L)

        val request = okhttp3.Request.Builder()
            .get()
            .url("http://www.smh.com.au")
            .build()
        val responseBody = "{}".toResponseBody("application/json; charset=utf-8".toMediaType())
        val forbidden = okhttp3.Response.Builder()
            .protocol(Protocol.HTTP_1_1)
            .code(HttpURLConnection.HTTP_FORBIDDEN)
            .request(request)
            .message("Forbidden")
            .body(responseBody)
            .build()

        holder.callback shouldNotBe null
        holder.callback?.onHttpError(ApolloHttpException(forbidden))

        deferredResult.await()

        verify(mockAppSyncClient)
            .mutate<
                UpdateEmailMessagesMutation.Data,
                UpdateEmailMessagesMutation,
                UpdateEmailMessagesMutation.Variables,
                >(
                check {
                    it.variables().input().messageIds() shouldBe listOf("id1", "id2")
                    it.variables().input().values().folderId() shouldBe "folderId2"
                    it.variables().input().values().seen() shouldBe true
                },
            )
    }

    @Test
    fun `updateEmailMessages() should throw when unknown error occurs()`() = runTest {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<UpdateEmailMessagesMutation>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val input = UpdateEmailMessagesInput(
            listOf("id1", "id2"),
            UpdateEmailMessagesInput.UpdatableValues("folderId2", true),
        )
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.updateEmailMessages(input)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient)
            .mutate<
                UpdateEmailMessagesMutation.Data,
                UpdateEmailMessagesMutation,
                UpdateEmailMessagesMutation.Variables,
                >(
                check {
                    it.variables().input().messageIds() shouldBe listOf("id1", "id2")
                    it.variables().input().values().folderId() shouldBe "folderId2"
                    it.variables().input().values().seen() shouldBe true
                },
            )
    }

    @Test
    fun `updateEmailMessage() should not block coroutine cancellation exception`() =
        runTest {
            mockAppSyncClient.stub {
                on { mutate(any<UpdateEmailMessagesMutation>()) } doThrow CancellationException("Mock Cancellation Exception")
            }

            val input = UpdateEmailMessagesInput(
                listOf("id1", "id2"),
                UpdateEmailMessagesInput.UpdatableValues("folderId2", true),
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<CancellationException> {
                    client.updateEmailMessages(input)
                }
            }
            deferredResult.start()
            delay(100L)

            deferredResult.await()

            verify(mockAppSyncClient).mutate(any<UpdateEmailMessagesMutation>())
        }
}
