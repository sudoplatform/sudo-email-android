/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.ListEmailAddressesQuery
import com.sudoplatform.sudoemail.graphql.type.ListEmailAddressesInput
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.keys.DefaultPublicKeyService
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudoprofiles.S3Client
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.util.Date
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [SudoEmailClient.listEmailAddresses] using mocks
 * and spies.
 *
 * @since 2020-08-05
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListEmailAddressesTest : BaseTests() {

    private val input by before {
        ListEmailAddressesInput.builder()
            .sudoId(null)
            .filter(null)
            .limit(null)
            .nextToken(null)
            .build()
    }

    private val queryResult by before {
        ListEmailAddressesQuery.ListEmailAddresses(
            "typename",
            listOf(
                ListEmailAddressesQuery.Item(
                    "typename",
                    "emailAddressId",
                    "userId",
                    "sudoId",
                    "identityId",
                    "keyRingId",
                    emptyList(),
                    1,
                    1.0,
                    1.0,
                    "example@sudoplatform.com"
                )
            ),
            null
        )
    }

    private val queryResponse by before {
        Response.builder<ListEmailAddressesQuery.Data>(ListEmailAddressesQuery(input))
            .data(ListEmailAddressesQuery.Data(queryResult))
            .build()
    }

    private val queryHolder = CallbackHolder<ListEmailAddressesQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockSudoClient by before {
        mock<SudoProfilesClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<ListEmailAddressesQuery>()) } doReturn queryHolder.queryOperation
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val mockDeviceKeyManager by before {
        DefaultDeviceKeyManager(
            mockContext,
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger
        )
    }

    private val publicKeyService by before {
        DefaultPublicKeyService(
            mockDeviceKeyManager,
            mockAppSyncClient,
            mockLogger
        )
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking { upload(any(), anyString()) } doReturn "42"
        }
    }

    private val client by before {
        DefaultSudoEmailClient(
            mockContext,
            mockAppSyncClient,
            mockUserClient,
            mockSudoClient,
            mockLogger,
            mockDeviceKeyManager,
            publicKeyService,
            "region",
            "identityBucket",
            "transientBucket",
            mockS3Client,
            mockS3Client
        )
    }

    @Before
    fun init() {
        queryHolder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockContext, mockUserClient, mockSudoClient, mockKeyManager, mockAppSyncClient, mockS3Client)
    }

    @Test
    fun `listEmailAddresses() should return results when no error present`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.listEmailAddresses()
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(queryResponse)

        val result = deferredResult.await()
        result shouldNotBe null
        result.items.isEmpty() shouldBe false
        result.items.size shouldBe 1
        result.nextToken shouldBe null

        with(result.items[0]) {
            id shouldBe "emailAddressId"
            emailAddress shouldBe "example@sudoplatform.com"
            userId shouldBe "userId"
            sudoId shouldBe "sudoId"
            owners shouldBe emptyList()
            createdAt shouldBe Date(1L)
            updatedAt shouldBe Date(1L)
        }

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
    }

    @Test
    fun `listEmailAddresses() should return results when populating nextToken`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val queryResultWithNextToken by before {
            ListEmailAddressesQuery.ListEmailAddresses(
                "typename",
                listOf(
                    ListEmailAddressesQuery.Item(
                        "typename",
                        "emailAddressId",
                        "userId",
                        "sudoId",
                        "identityId",
                        "keyRingId",
                        emptyList(),
                        1,
                        1.0,
                        1.0,
                        "example@sudoplatform.com"
                    )
                ),
                "dummyNextToken"
            )
        }

        val inputWithNextToken by before {
            ListEmailAddressesInput.builder()
                .sudoId(null)
                .filter(null)
                .limit(1)
                .nextToken("dummyNextToken")
                .build()
        }
        val responseWithNextToken by before {
            Response.builder<ListEmailAddressesQuery.Data>(ListEmailAddressesQuery(inputWithNextToken))
                .data(ListEmailAddressesQuery.Data(queryResultWithNextToken))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listEmailAddresses(null, 1, "dummyNextToken", CachePolicy.REMOTE_ONLY)
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithNextToken)

        val result = deferredResult.await()
        result shouldNotBe null
        result.items.isEmpty() shouldBe false
        result.items.size shouldBe 1
        result.nextToken shouldBe "dummyNextToken"

        with(result.items[0]) {
            id shouldBe "emailAddressId"
            emailAddress shouldBe "example@sudoplatform.com"
            userId shouldBe "userId"
            sudoId shouldBe "sudoId"
            owners shouldBe emptyList()
            createdAt shouldBe Date(1L)
            updatedAt shouldBe Date(1L)
        }

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
    }

    @Test
    fun `listEmailAddresses() should return results when providing sudoId`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val inputWithSudoId by before {
            ListEmailAddressesInput.builder()
                .sudoId("sudoId")
                .filter(null)
                .limit(1)
                .nextToken(null)
                .build()
        }
        val responseWithSudoId by before {
            Response.builder<ListEmailAddressesQuery.Data>(ListEmailAddressesQuery(inputWithSudoId))
                .data(ListEmailAddressesQuery.Data(queryResult))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listEmailAddresses("sudoId", 1, null, CachePolicy.REMOTE_ONLY)
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithSudoId)

        val result = deferredResult.await()
        result shouldNotBe null
        result.items.isEmpty() shouldBe false
        result.items.size shouldBe 1
        result.nextToken shouldBe null

        with(result.items[0]) {
            id shouldBe "emailAddressId"
            emailAddress shouldBe "example@sudoplatform.com"
            userId shouldBe "userId"
            sudoId shouldBe "sudoId"
            owners shouldBe emptyList()
            createdAt shouldBe Date(1L)
            updatedAt shouldBe Date(1L)
        }

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
    }

    @Test
    fun `listEmailAddresses() should return empty list output when query result data is empty`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val queryResultWithEmptyList by before {
            ListEmailAddressesQuery.ListEmailAddresses(
                "typename",
                emptyList(),
                null
            )
        }

        val responseWithEmptyList by before {
            Response.builder<ListEmailAddressesQuery.Data>(ListEmailAddressesQuery(input))
                .data(ListEmailAddressesQuery.Data(queryResultWithEmptyList))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listEmailAddresses()
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithEmptyList)

        val result = deferredResult.await()
        result shouldNotBe null
        result.items.isEmpty() shouldBe true
        result.items.size shouldBe 0
        result.nextToken shouldBe null

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
    }

    @Test
    fun `listEmailAddresses() should return empty list output when query response is null`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val nullQueryResponse by before {
            Response.builder<ListEmailAddressesQuery.Data>(ListEmailAddressesQuery(input))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listEmailAddresses()
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(nullQueryResponse)

        val result = deferredResult.await()
        result shouldNotBe null
        result.items.isEmpty() shouldBe true
        result.items.size shouldBe 0
        result.nextToken shouldBe null

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
    }

    @Test
    fun `listEmailAddresses() should throw when http error occurs`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                client.listEmailAddresses()
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

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
    }

    @Test
    fun `listEmailAddresses() should throw when unknown error occurs`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<ListEmailAddressesQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.UnknownException> {
                client.listEmailAddresses()
            }
        }
        deferredResult.start()

        delay(100L)
        deferredResult.await()

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
    }

    @Test
    fun `listEmailAddresses() should not block coroutine cancellation exception`() = runBlocking<Unit> {

        mockAppSyncClient.stub {
            on { query(any<ListEmailAddressesQuery>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        shouldThrow<CancellationException> {
            client.listEmailAddresses()
        }

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
    }
}
