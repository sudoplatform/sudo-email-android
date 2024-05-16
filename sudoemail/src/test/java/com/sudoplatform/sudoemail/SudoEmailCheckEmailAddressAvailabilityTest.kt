/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.CheckEmailAddressAvailabilityQuery
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.inputs.CheckEmailAddressAvailabilityInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException
import com.sudoplatform.sudoemail.graphql.type.CheckEmailAddressAvailabilityInput as CheckEmailAddressAvailabilityRequest

/**
 * Test the correct operation of [SudoEmailClient.checkEmailAddressAvailability]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailCheckEmailAddressAvailabilityTest : BaseTests() {

    private val localParts = listOf("foo")
    private val domains = listOf("bar.com")
    private val answers = listOf("foo@bar.com", "food@bar.com")

    private val queryResult by before {
        CheckEmailAddressAvailabilityQuery.CheckEmailAddressAvailability("typename", answers)
    }

    private val queryInput = CheckEmailAddressAvailabilityRequest.builder()
        .localParts(localParts)
        .domains(domains)
        .build()

    private val queryResponse by before {
        Response.builder<CheckEmailAddressAvailabilityQuery.Data>(CheckEmailAddressAvailabilityQuery(queryInput))
            .data(CheckEmailAddressAvailabilityQuery.Data(queryResult))
            .build()
    }

    private val queryHolder = CallbackHolder<CheckEmailAddressAvailabilityQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<CheckEmailAddressAvailabilityQuery>()) } doReturn queryHolder.queryOperation
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { getPassword(anyString()) } doReturn ByteArray(42)
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

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking { upload(any(), anyString(), anyOrNull()) } doReturn "42"
        }
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        DefaultSealingService(mockServiceKeyManager, mockLogger)
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>()
    }

    private val client by before {
        DefaultSudoEmailClient(
            mockContext,
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
        queryHolder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockContext,
            mockUserClient,
            mockKeyManager,
            mockAppSyncClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `checkEmailAddressAvailability() should return results when no error present`() = runTest {
        queryHolder.callback shouldBe null

        val input = CheckEmailAddressAvailabilityInput(
            localParts,
            domains,
        )
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.checkEmailAddressAvailability(input)
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(queryResponse)

        val result = deferredResult.await()
        result shouldNotBe null
        result.isEmpty() shouldBe false
        result.size shouldBe 2
        result shouldContainExactlyInAnyOrder answers

        verify(mockAppSyncClient).query(any<CheckEmailAddressAvailabilityQuery>())
    }

    @Test
    fun `checkEmailAddressAvailability() should return empty list output when query result data is empty`() = runTest {
        queryHolder.callback shouldBe null

        val queryResultWithEmptyList by before {
            CheckEmailAddressAvailabilityQuery.CheckEmailAddressAvailability(
                "typename",
                emptyList(),
            )
        }

        val responseWithEmptyList by before {
            Response.builder<CheckEmailAddressAvailabilityQuery.Data>(CheckEmailAddressAvailabilityQuery(queryInput))
                .data(CheckEmailAddressAvailabilityQuery.Data(queryResultWithEmptyList))
                .build()
        }

        val input = CheckEmailAddressAvailabilityInput(
            localParts,
            domains,
        )
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.checkEmailAddressAvailability(input)
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithEmptyList)

        val result = deferredResult.await()
        result shouldNotBe null
        result.isEmpty() shouldBe true
        result.size shouldBe 0

        verify(mockAppSyncClient).query(any<CheckEmailAddressAvailabilityQuery>())
    }

    @Test
    fun `checkEmailAddressAvailability() should return empty list output when query response is null`() = runTest {
        queryHolder.callback shouldBe null

        val responseWithNullData by before {
            Response.builder<CheckEmailAddressAvailabilityQuery.Data>(CheckEmailAddressAvailabilityQuery(queryInput))
                .data(null)
                .build()
        }

        val input = CheckEmailAddressAvailabilityInput(
            localParts,
            domains,
        )
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.checkEmailAddressAvailability(input)
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithNullData)

        val result = deferredResult.await()
        result shouldNotBe null
        result.isEmpty() shouldBe true
        result.size shouldBe 0

        verify(mockAppSyncClient).query(any<CheckEmailAddressAvailabilityQuery>())
    }

    @Test
    fun `checkEmailAddressAvailability() should throw when response has error`() = runTest {
        queryHolder.callback shouldBe null

        val error = com.apollographql.apollo.api.Error(
            "mock",
            emptyList(),
            mapOf("errorType" to "DilithiumCrystalsOutOfAlignment"),
        )

        val responseWithNullData by before {
            Response.builder<CheckEmailAddressAvailabilityQuery.Data>(CheckEmailAddressAvailabilityQuery(queryInput))
                .errors(listOf(error))
                .build()
        }

        val input = CheckEmailAddressAvailabilityInput(
            localParts,
            domains,
        )
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                client.checkEmailAddressAvailability(input)
            }
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithNullData)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<CheckEmailAddressAvailabilityQuery>())
    }

    @Test
    fun `checkEmailAddressAvailability() should throw when http error occurs`() = runTest {
        queryHolder.callback shouldBe null

        val input = CheckEmailAddressAvailabilityInput(
            localParts,
            domains,
        )
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                client.checkEmailAddressAvailability(input)
            }
        }
        deferredResult.start()
        delay(100L)

        val request = Request.Builder()
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

        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onHttpError(ApolloHttpException(forbidden))

        deferredResult.await()

        verify(mockAppSyncClient).query(any<CheckEmailAddressAvailabilityQuery>())
    }

    @Test
    fun `checkEmailAddressAvailability() should throw when unknown error occurs`() = runTest {
        queryHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<CheckEmailAddressAvailabilityQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val input = CheckEmailAddressAvailabilityInput(
            localParts,
            domains,
        )
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.UnknownException> {
                client.checkEmailAddressAvailability(input)
            }
        }
        deferredResult.start()

        delay(100L)
        deferredResult.await()

        verify(mockAppSyncClient).query(any<CheckEmailAddressAvailabilityQuery>())
    }

    @Test
    fun `checkEmailAddressAvailability() should not block coroutine cancellation exception`() = runTest {
        mockAppSyncClient.stub {
            on { query(any<CheckEmailAddressAvailabilityQuery>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        val input = CheckEmailAddressAvailabilityInput(
            localParts,
            domains,
        )
        shouldThrow<CancellationException> {
            client.checkEmailAddressAvailability(input)
        }

        verify(mockAppSyncClient).query(any<CheckEmailAddressAvailabilityQuery>())
    }
}
