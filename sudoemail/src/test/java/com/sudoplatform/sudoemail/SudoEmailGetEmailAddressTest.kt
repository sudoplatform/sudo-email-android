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
import com.sudoplatform.sudoemail.graphql.GetEmailAddressQuery
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.keys.DefaultPublicKeyService
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudoprofiles.S3Client
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.CancellationException
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
import org.mockito.ArgumentMatchers.anyString
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.getEmailAddress]
 * using mocks and spies.
 *
 * @since 2020-08-05
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetEmailAddressTest : BaseTests() {

    private val queryResult by before {
        GetEmailAddressQuery.GetEmailAddress(
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
    }

    private val response by before {
        Response.builder<GetEmailAddressQuery.Data>(GetEmailAddressQuery("emailAddressId"))
            .data(GetEmailAddressQuery.Data(queryResult))
            .build()
    }

    private var holder = CallbackHolder<GetEmailAddressQuery.Data>()

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
            on { query(any<GetEmailAddressQuery>()) } doReturn holder.queryOperation
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
        holder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockContext, mockUserClient, mockSudoClient, mockKeyManager, mockAppSyncClient, mockS3Client)
    }

    @Test
    fun `getEmailAddress() should return results when no error present`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.getEmailAddress("emailAddressId")
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        val result = deferredResult.await()
        result shouldNotBe null

        with(result!!) {
            id shouldBe "emailAddressId"
            emailAddress shouldBe "example@sudoplatform.com"
            userId shouldBe "userId"
            sudoId shouldBe "sudoId"
            owners shouldBe emptyList()
            createdAt shouldBe Date(1L)
            updatedAt shouldBe Date(1L)
        }

        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
    }

    @Test
    fun `getEmailAddress() should return null result when query result data is null`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val responseWithNullResult by before {
            Response.builder<GetEmailAddressQuery.Data>(GetEmailAddressQuery("emailAddressId"))
                .data(GetEmailAddressQuery.Data(null))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.getEmailAddress("emailAddressId")
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(responseWithNullResult)

        val result = deferredResult.await()
        result shouldBe null

        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
    }

    @Test
    fun `getEmailAddress() should return null result when query response is null`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val nullResponse by before {
            Response.builder<GetEmailAddressQuery.Data>(GetEmailAddressQuery("emailAddressId"))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.getEmailAddress("emailAddressId")
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(nullResponse)

        val result = deferredResult.await()
        result shouldBe null

        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
    }

    @Test
    fun `getEmailAddress() should throw when http error occurs`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                client.getEmailAddress("emailAddressId")
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

        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
    }

    @Test
    fun `getEmailAddress() should throw when unknown error occurs`() = runBlocking<Unit> {

        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<GetEmailAddressQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.UnknownException> {
                client.getEmailAddress("emailAddressId")
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
    }

    @Test
    fun `getEmailAddress() should not suppress CancellationException`() = runBlocking<Unit> {

        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<GetEmailAddressQuery>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<CancellationException> {
                client.getEmailAddress("emailAddressId")
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
    }
}
