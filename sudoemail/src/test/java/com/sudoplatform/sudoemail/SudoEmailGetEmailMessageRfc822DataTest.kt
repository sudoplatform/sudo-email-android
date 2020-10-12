/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.auth.CognitoCredentialsProvider
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.GetEmailMessageQuery
import com.sudoplatform.sudoemail.graphql.type.EmailMessageDirection
import com.sudoplatform.sudoemail.graphql.type.EmailMessageState
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.keys.DefaultPublicKeyService
import com.sudoplatform.sudoemail.keys.PublicKeyService
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
import org.apache.commons.codec.binary.Base64
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection

/**
 * Test the correct operation of [SudoEmailClient.getEmailMessageRfc822Data] using mocks and spies.
 *
 * @since 2020-08-18
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetEmailMessageRfc822DataTest : BaseTests() {

    private fun mockSeal(value: String): String {
        val valueBytes = value.toByteArray()
        val data = ByteArray(256)
        valueBytes.copyInto(data)
        return Base64.encodeBase64String(data)
    }

    private val queryResult by before {
        GetEmailMessageQuery.GetEmailMessage(
            "typename",
            "id",
            "messageId",
            "userId",
            "sudoId",
            "emailAddressId",
            1,
            1.0,
            1.0,
            "algorithm",
            "keyId",
            EmailMessageDirection.INBOUND,
            false,
            EmailMessageState.DELIVERED,
            "clientRefId",
            listOf(mockSeal("from")),
            listOf(mockSeal("replyTo")),
            listOf(mockSeal("to")),
            listOf(mockSeal("cc")),
            listOf(mockSeal("bcc")),
            mockSeal("subject")
        )
    }

    private val queryResponse by before {
        Response.builder<GetEmailMessageQuery.Data>(GetEmailMessageQuery("messageId"))
            .data(GetEmailMessageQuery.Data(queryResult))
            .build()
    }

    private var queryHolder = CallbackHolder<GetEmailMessageQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockCredentialsProvider by before {
        mock< CognitoCredentialsProvider>().stub {
            on { identityId } doReturn "identityId"
        }
    }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "subject"
            on { getCredentialsProvider() } doReturn mockCredentialsProvider
        }
    }

    private val mockSudoClient by before {
        mock<SudoProfilesClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<GetEmailMessageQuery>()) } doReturn queryHolder.queryOperation
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { getPassword(anyString()) } doReturn ByteArray(42)
            on { getPublicKeyData(anyString()) } doReturn ByteArray(42)
            on { getPrivateKeyData(anyString()) } doReturn ByteArray(42)
            on { decryptWithPrivateKey(anyString(), any(), any()) } doReturn ByteArray(42)
            on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn "42".toByteArray()
        }
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
            onBlocking { download(anyString()) } doReturn mockSeal("foobar").toByteArray(Charsets.UTF_8)
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
    fun `getEmailMessageRfc822Data() should return results when no error present`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.getEmailMessageRfc822Data("messageId")
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(queryResponse)

        val result = deferredResult.await()
        result shouldNotBe null

        verify(mockAppSyncClient).query(any<GetEmailMessageQuery>())
        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
        verify(mockKeyManager).getPrivateKeyData(anyString())
        verify(mockUserClient).getSubject()
        verify(mockKeyManager, times(7)).decryptWithPrivateKey(anyString(), any(), any())
        verify(mockKeyManager, times(7)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        verify(mockUserClient).getCredentialsProvider()
        verify(mockCredentialsProvider).identityId
        verify(mockS3Client).download(anyString())
    }

    @Test
    fun `getEmailMessageRfc822Data() should return null result when query result data is null`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val responseWithNullResult by before {
            Response.builder<GetEmailMessageQuery.Data>(GetEmailMessageQuery("messageId"))
                .data(GetEmailMessageQuery.Data(null))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.getEmailMessageRfc822Data("messageId")
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithNullResult)

        val result = deferredResult.await()
        result shouldBe null

        verify(mockAppSyncClient).query(any<GetEmailMessageQuery>())
        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
        verify(mockKeyManager).getPrivateKeyData(anyString())
        verify(mockUserClient).getSubject()
    }

    @Test
    fun `getEmailMessageRfc822Data() should return null result when query response is null`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val nullResponse by before {
            Response.builder<GetEmailMessageQuery.Data>(GetEmailMessageQuery("messageId"))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.getEmailMessageRfc822Data("messageId")
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(nullResponse)

        val result = deferredResult.await()
        result shouldBe null

        verify(mockAppSyncClient).query(any<GetEmailMessageQuery>())
        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
        verify(mockKeyManager).getPrivateKeyData(anyString())
        verify(mockUserClient).getSubject()
    }

    @Test
    fun `getEmailMessageRfc822Data() should throw when password retrieval fails`() = runBlocking<Unit> {

        mockKeyManager.stub {
            on { getPassword(anyString()) } doThrow PublicKeyService.PublicKeyServiceException.KeyCreateException(
                "Mock PublicKey Service Exception"
            )
        }

        shouldThrow<SudoEmailClient.EmailMessageException.PublicKeyException> {
            client.getEmailMessageRfc822Data("messageId")
        }

        verify(mockKeyManager).getPassword(anyString())
    }

    @Test
    fun `getEmailMessageRfc822Data() should throw when public key data retrieval fails`() = runBlocking<Unit> {

        mockKeyManager.stub {
            on { getPublicKeyData(anyString()) } doThrow PublicKeyService.PublicKeyServiceException.KeyCreateException(
                "Mock PublicKey Service Exception"
            )
        }

        shouldThrow<SudoEmailClient.EmailMessageException.PublicKeyException> {
            client.getEmailMessageRfc822Data("messageId")
        }

        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
    }

    @Test
    fun `getEmailMessageRfc822Data() should throw when private key data retrieval fails`() = runBlocking<Unit> {

        mockKeyManager.stub {
            on { getPrivateKeyData(anyString()) } doThrow PublicKeyService.PublicKeyServiceException.KeyCreateException(
                "Mock PublicKey Service Exception"
            )
        }

        shouldThrow<SudoEmailClient.EmailMessageException.PublicKeyException> {
            client.getEmailMessageRfc822Data("messageId")
        }

        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
        verify(mockKeyManager).getPrivateKeyData(anyString())
    }

    @Test
    fun `getEmailMessageRfc822Data() should throw when http error occurs`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.getEmailMessageRfc822Data("messageId")
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

        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onHttpError(ApolloHttpException(forbidden))

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEmailMessageQuery>())
        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
        verify(mockKeyManager).getPrivateKeyData(anyString())
        verify(mockUserClient).getSubject()
    }

    @Test
    fun `getEmailMessageRfc822Data() should throw when unknown error occurs`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<GetEmailMessageQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.getEmailMessageRfc822Data("messageId")
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEmailMessageQuery>())
        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
        verify(mockKeyManager).getPrivateKeyData(anyString())
        verify(mockUserClient).getSubject()
    }

    @Test
    fun `getEmailMessageRfc822Data() should not suppress CancellationException`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<GetEmailMessageQuery>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<CancellationException> {
                client.getEmailMessageRfc822Data("messageId")
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEmailMessageQuery>())
        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
        verify(mockKeyManager).getPrivateKeyData(anyString())
        verify(mockUserClient).getSubject()
    }
}
