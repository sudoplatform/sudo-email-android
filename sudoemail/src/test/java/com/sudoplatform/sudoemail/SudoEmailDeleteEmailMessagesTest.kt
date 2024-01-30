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
import com.sudoplatform.sudoemail.graphql.DeleteEmailMessagesMutation
import com.sudoplatform.sudoemail.graphql.type.DeleteEmailMessagesInput
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.sealing.DefaultSealingService
import com.sudoplatform.sudoemail.types.BatchOperationResult
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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

/**
 * Test the correct operation of [SudoEmailClient.deleteEmailMessages]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailDeleteEmailMessagesTest : BaseTests() {

    private val input by before {
        DeleteEmailMessagesInput.builder()
            .messageIds(listOf("id1", "1d2"))
            .build()
    }

    private val mutationResult by before {
        listOf<String>()
    }

    private val mutationResponse by before {
        Response.builder<DeleteEmailMessagesMutation.Data>(DeleteEmailMessagesMutation(input))
            .data(DeleteEmailMessagesMutation.Data(mutationResult))
            .build()
    }

    private val holder = CallbackHolder<DeleteEmailMessagesMutation.Data>()

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<DeleteEmailMessagesMutation>()) } doReturn holder.mutationOperation
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val mockDeviceKeyManager by before {
        DefaultDeviceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
    }

    private val mockS3Client by before {
        mock<S3Client>()
    }

    private val mockSealingService by before {
        DefaultSealingService(
            mockDeviceKeyManager,
            mockLogger,
        )
    }

    private val client by before {
        DefaultSudoEmailClient(
            context,
            mockAppSyncClient,
            mockUserClient,
            mockLogger,
            mockDeviceKeyManager,
            mockSealingService,
            "region",
            "identityBucket",
            "transientBucket",
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
        verifyNoMoreInteractions(mockUserClient, mockKeyManager, mockAppSyncClient, mockS3Client)
    }

    @Test
    fun `deleteEmailMessages() should return success result when no error present`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.deleteEmailMessages(listOf("id1", "id2"))
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(mutationResponse)

        val result = deferredResult.await()
        result shouldNotBe null

        when (result) {
            is BatchOperationResult.SuccessOrFailureResult -> {
                result.status shouldBe BatchOperationStatus.SUCCESS
            }
            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }

        verify(mockAppSyncClient)
            .mutate<
                DeleteEmailMessagesMutation.Data,
                DeleteEmailMessagesMutation,
                DeleteEmailMessagesMutation.Variables,
                >(
                check {
                    it.variables().input().messageIds() shouldBe listOf("id1", "id2")
                },
            )
    }

    @Test
    fun `deleteEmailMessages() should return failure result when no error present`() = runBlocking<Unit> {
        val mutationResult by before {
            listOf("id1", "id2")
        }

        val mutationResponse by before {
            Response.builder<DeleteEmailMessagesMutation.Data>(DeleteEmailMessagesMutation(input))
                .data(DeleteEmailMessagesMutation.Data(mutationResult))
                .build()
        }

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.deleteEmailMessages(listOf("id1", "id2"))
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(mutationResponse)

        val result = deferredResult.await()
        result shouldNotBe null

        when (result) {
            is BatchOperationResult.SuccessOrFailureResult -> {
                result.status shouldBe BatchOperationStatus.FAILURE
            }
            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }

        verify(mockAppSyncClient)
            .mutate<
                DeleteEmailMessagesMutation.Data,
                DeleteEmailMessagesMutation,
                DeleteEmailMessagesMutation.Variables,
                >(
                check {
                    it.variables().input().messageIds() shouldBe listOf("id1", "id2")
                },
            )
    }

    @Test
    fun `deleteEmailMessages() should return partial result when no error present`() = runBlocking<Unit> {
        val mutationResult by before {
            listOf("id1")
        }

        val mutationResponse by before {
            Response.builder<DeleteEmailMessagesMutation.Data>(DeleteEmailMessagesMutation(input))
                .data(DeleteEmailMessagesMutation.Data(mutationResult))
                .build()
        }

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.deleteEmailMessages(listOf("id1", "id2"))
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(mutationResponse)

        val result = deferredResult.await()
        result shouldNotBe null

        when (result) {
            is BatchOperationResult.PartialResult -> {
                result.status shouldBe BatchOperationStatus.PARTIAL
                result.successValues shouldBe listOf("id2")
                result.failureValues shouldBe listOf("id1")
            }
            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }

        verify(mockAppSyncClient)
            .mutate<
                DeleteEmailMessagesMutation.Data,
                DeleteEmailMessagesMutation,
                DeleteEmailMessagesMutation.Variables,
                >(
                check {
                    it.variables().input().messageIds() shouldBe listOf("id1", "id2")
                },
            )
    }

    @Test
    fun `deleteEmailMessages() should throw when email mutation response is null`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val nullProvisionResponse by before {
            Response.builder<DeleteEmailMessagesMutation.Data>(DeleteEmailMessagesMutation(input))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.deleteEmailMessages(listOf("id1", "id2"))
            }
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(nullProvisionResponse)

        deferredResult.await()

        verify(mockAppSyncClient)
            .mutate<
                DeleteEmailMessagesMutation.Data,
                DeleteEmailMessagesMutation,
                DeleteEmailMessagesMutation.Variables,
                >(
                check {
                    it.variables().input().messageIds() shouldBe listOf("id1", "id2")
                },
            )
    }

    @Test
    fun `deleteEmailMessages() should throw when response has unexpected error`() = runBlocking<Unit> {
        holder.callback = null

        val errorSendResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "blah"),
            )
            Response.builder<DeleteEmailMessagesMutation.Data>(DeleteEmailMessagesMutation(input))
                .errors(listOf(error))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.deleteEmailMessages(listOf("id1", "id2"))
            }
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(errorSendResponse)

        deferredResult.await()

        verify(mockAppSyncClient)
            .mutate<
                DeleteEmailMessagesMutation.Data,
                DeleteEmailMessagesMutation,
                DeleteEmailMessagesMutation.Variables,
                >(
                check {
                    it.variables().input().messageIds() shouldBe listOf("id1", "id2")
                },
            )
    }

    @Test
    fun `deleteEmailMessages() should throw when http error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.deleteEmailMessages(listOf("id1", "id2"))
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
                DeleteEmailMessagesMutation.Data,
                DeleteEmailMessagesMutation,
                DeleteEmailMessagesMutation.Variables,
                >(
                check {
                    it.variables().input().messageIds() shouldBe listOf("id1", "id2")
                },
            )
    }

    @Test
    fun `deleteEmailMessages() should throw when unknown error occurs()`() = runBlocking<Unit> {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<DeleteEmailMessagesMutation>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.deleteEmailMessages(listOf("id1", "id2"))
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient)
            .mutate<
                DeleteEmailMessagesMutation.Data,
                DeleteEmailMessagesMutation,
                DeleteEmailMessagesMutation.Variables,
                >(
                check {
                    it.variables().input().messageIds() shouldBe listOf("id1", "id2")
                },
            )
    }

    @Test
    fun `deleteEmailMessage() should not block coroutine cancellation exception`() = runBlocking<Unit> {
        mockAppSyncClient.stub {
            on { mutate(any<DeleteEmailMessagesMutation>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<CancellationException> {
                client.deleteEmailMessages(listOf("id1", "id2"))
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeleteEmailMessagesMutation>())
    }
}
