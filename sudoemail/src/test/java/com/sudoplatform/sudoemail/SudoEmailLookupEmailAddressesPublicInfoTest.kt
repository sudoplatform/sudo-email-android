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
import com.sudoplatform.sudoemail.graphql.LookupEmailAddressesPublicInfoQuery
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressPublicInfo
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.sealing.DefaultSealingService
import com.sudoplatform.sudoemail.types.inputs.LookupEmailAddressesPublicInfoInput
import com.sudoplatform.sudokeymanager.KeyManagerInterface
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
import com.sudoplatform.sudoemail.graphql.type.LookupEmailAddressesPublicInfoInput as LookupEmailAddressesPublicInfoRequest

/**
 * Test the correct operation of [SudoEmailClient.lookupEmailAddressesPublicInfo]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailLookupEmailAddressesPublicInfoTest : BaseTests() {

    private val emailAddressPublicInfo by before {
        EmailAddressPublicInfo(
            "typename",
            "emailAddress",
            "publicKey",
        )
    }

    private val input by before {
        LookupEmailAddressesPublicInfoRequest.builder()
            .emailAddresses(mutableListOf("emailAddress"))
            .build()
    }

    private val queryResult by before {
        LookupEmailAddressesPublicInfoQuery.LookupEmailAddressesPublicInfo(
            "typename",
            listOf(
                LookupEmailAddressesPublicInfoQuery.Item(
                    "typename",
                    LookupEmailAddressesPublicInfoQuery.Item.Fragments(emailAddressPublicInfo),
                ),
            ),
        )
    }

    private val response by before {
        Response.builder<LookupEmailAddressesPublicInfoQuery.Data>(
            LookupEmailAddressesPublicInfoQuery(input),
        )
            .data(LookupEmailAddressesPublicInfoQuery.Data(queryResult))
            .build()
    }

    private var holder = CallbackHolder<LookupEmailAddressesPublicInfoQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<LookupEmailAddressesPublicInfoQuery>()) } doReturn holder.queryOperation
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
        mock<S3Client>().stub {
            onBlocking { upload(any(), anyString(), anyOrNull()) } doReturn "42"
        }
    }

    private val mockSealingService by before {
        DefaultSealingService(
            mockDeviceKeyManager,
            mockLogger,
        )
    }

    private val client by before {
        DefaultSudoEmailClient(
            mockContext,
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
        verifyNoMoreInteractions(mockContext, mockUserClient, mockKeyManager, mockAppSyncClient, mockS3Client)
    }

    @Test
    fun `lookupEmailAddressesPublicInfo() should return results when no error present`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val input = LookupEmailAddressesPublicInfoInput(emailAddresses = listOf("emailAddress"))
        val deferredResult = async(Dispatchers.IO) {
            client.lookupEmailAddressesPublicInfo(input)
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        val result = deferredResult.await()
        result shouldNotBe null
        result.count() shouldBe 1

        with(result[0]) {
            emailAddress shouldBe "emailAddress"
            publicKey shouldBe "publicKey"
        }

        verify(mockAppSyncClient).query(any<LookupEmailAddressesPublicInfoQuery>())
    }

    @Test
    fun `lookupEmailAddressesPublicInfo() should return empty result when query result data is empty`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val emptyInput by before {
            LookupEmailAddressesPublicInfoRequest.builder()
                .emailAddresses(emptyList())
                .build()
        }

        val emptyQueryResult by before {
            LookupEmailAddressesPublicInfoQuery.LookupEmailAddressesPublicInfo(
                "typename",
                emptyList(),
            )
        }

        val emptyResponse by before {
            Response.builder<LookupEmailAddressesPublicInfoQuery.Data>(
                LookupEmailAddressesPublicInfoQuery(emptyInput),
            )
                .data(LookupEmailAddressesPublicInfoQuery.Data(emptyQueryResult))
                .build()
        }

        val input = LookupEmailAddressesPublicInfoInput(emptyList())
        val deferredResult = async(Dispatchers.IO) {
            client.lookupEmailAddressesPublicInfo(input)
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(emptyResponse)

        val result = deferredResult.await()
        result shouldBe emptyList()

        verify(mockAppSyncClient).query(any<LookupEmailAddressesPublicInfoQuery>())
    }

    @Test
    fun `lookupEmailAddressesPublicInfo() should throw when http error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val input = LookupEmailAddressesPublicInfoInput(listOf("emailAddress"))
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                client.lookupEmailAddressesPublicInfo(input)
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

        verify(mockAppSyncClient).query(any<LookupEmailAddressesPublicInfoQuery>())
    }

    @Test
    fun `lookupEmailAddressesPublicInfo() should throw when unknown error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<LookupEmailAddressesPublicInfoQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val input = LookupEmailAddressesPublicInfoInput(listOf("emailAddress"))
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.UnknownException> {
                client.lookupEmailAddressesPublicInfo(input)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<LookupEmailAddressesPublicInfoQuery>())
    }
}
