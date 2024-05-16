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

    private val input by before {
        DeleteEmailMessagesInput.builder()
            .messageIds(listOf("id"))
            .build()
    }

    private val mutationResult by before {
        emptyList<String>()
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
    fun `deleteEmailMessage() should return results when no error present`() = runTest {
        holder.callback shouldBe null

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.deleteEmailMessage("id")
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(mutationResponse)

        val result = deferredResult.await()
        result shouldNotBe null
        result?.isBlank() shouldBe false

        verify(mockAppSyncClient)
            .mutate<
                DeleteEmailMessagesMutation.Data,
                DeleteEmailMessagesMutation,
                DeleteEmailMessagesMutation.Variables,
                >(
                check {
                    it.variables().input().messageIds() shouldBe listOf("id")
                },
            )
    }

    @Test
    fun `deleteEmailMessage() should return null result when delete operation fails`() = runTest {
        val mutationResult by before {
            listOf("id")
        }

        val mutationResponse by before {
            Response.builder<DeleteEmailMessagesMutation.Data>(DeleteEmailMessagesMutation(input))
                .data(DeleteEmailMessagesMutation.Data(mutationResult))
                .build()
        }

        holder.callback shouldBe null

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.deleteEmailMessage("id")
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(mutationResponse)

        val result = deferredResult.await()
        result shouldBe null

        verify(mockAppSyncClient)
            .mutate<
                DeleteEmailMessagesMutation.Data,
                DeleteEmailMessagesMutation,
                DeleteEmailMessagesMutation.Variables,
                >(
                check {
                    it.variables().input().messageIds() shouldBe listOf("id")
                },
            )
    }

    @Test
    fun `deleteEmailMessage() should throw when email mutation response is null`() = runTest {
        holder.callback shouldBe null

        val nullProvisionResponse by before {
            Response.builder<DeleteEmailMessagesMutation.Data>(DeleteEmailMessagesMutation(input))
                .data(null)
                .build()
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.deleteEmailMessage("id")
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
                    it.variables().input().messageIds() shouldBe listOf("id")
                },
            )
    }

    @Test
    fun `deleteEmailMessage() should throw when response has various errors`() = runTest {
        testException<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException>("EmailMessageNotFound")
        testException<SudoEmailClient.EmailMessageException.FailedException>("blah")

        verify(mockAppSyncClient, times(2))
            .mutate<
                DeleteEmailMessagesMutation.Data,
                DeleteEmailMessagesMutation,
                DeleteEmailMessagesMutation.Variables,
                >(
                check {
                    it.variables().input().messageIds() shouldBe listOf("id")
                },
            )
    }

    @Test
    fun `deleteEmailMessage() should throw when http error occurs`() = runTest {
        holder.callback shouldBe null

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.deleteEmailMessage("id")
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
                    it.variables().input().messageIds() shouldBe listOf("id")
                },
            )
    }

    @Test
    fun `deleteEmailMessage() should throw when unknown error occurs()`() = runTest {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<DeleteEmailMessagesMutation>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.deleteEmailMessage("id")
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
                    it.variables().input().messageIds() shouldBe listOf("id")
                },
            )
    }

    @Test
    fun `deleteEmailMessage() should not block coroutine cancellation exception`() = runTest {
        mockAppSyncClient.stub {
            on { mutate(any<DeleteEmailMessagesMutation>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<CancellationException> {
                client.deleteEmailMessage("id1")
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeleteEmailMessagesMutation>())
    }

    private inline fun <reified T : Exception> testException(apolloError: String) = runTest {
        holder.callback = null

        val errorSendResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to apolloError),
            )
            Response.builder<DeleteEmailMessagesMutation.Data>(DeleteEmailMessagesMutation(input))
                .errors(listOf(error))
                .build()
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<T> {
                client.deleteEmailMessage("id")
            }
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(errorSendResponse)

        deferredResult.await()
    }
}
