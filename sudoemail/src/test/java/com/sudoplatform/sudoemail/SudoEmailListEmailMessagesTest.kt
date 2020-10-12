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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.ListEmailMessagesQuery
import com.sudoplatform.sudoemail.graphql.type.EmailMessageDirection
import com.sudoplatform.sudoemail.graphql.type.EmailMessageState
import com.sudoplatform.sudoemail.graphql.type.ListEmailMessagesInput
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.keys.DefaultPublicKeyService
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.EmailMessage.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.inputs.filters.filterEmailMessagesBy
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudoprofiles.S3Client
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
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
import org.apache.commons.codec.binary.Base64
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
 * Test the correct operation of [SudoEmailClient.listEmailMessages] using mocks
 * and spies.
 *
 * @since 2020-08-12
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListEmailMessagesTest : BaseTests() {

    private fun mockSeal(value: String): String {
        val valueBytes = value.toByteArray()
        val data = ByteArray(256)
        valueBytes.copyInto(data)
        return Base64.encodeBase64String(data)
    }

    private val input by before {
        ListEmailMessagesInput.builder()
            .emailAddressId(null)
            .sudoId(null)
            .filter(null)
            .limit(null)
            .nextToken(null)
            .build()
    }

    private val queryResult by before {
        ListEmailMessagesQuery.ListEmailMessages(
            "typename",
            listOf(
                ListEmailMessagesQuery.Item(
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
            ),
            null
        )
    }

    private val queryResponse by before {
        Response.builder<ListEmailMessagesQuery.Data>(ListEmailMessagesQuery(input))
            .data(ListEmailMessagesQuery.Data(queryResult))
            .build()
    }

    private val queryHolder = CallbackHolder<ListEmailMessagesQuery.Data>()

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
            on { query(any<ListEmailMessagesQuery>()) } doReturn queryHolder.queryOperation
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
        mock<S3Client>()
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
    fun `listEmailMessages() should return results when no error present`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.listEmailMessages {
                filterEmailMessagesBy {
                    oneOf(
                        direction equalTo outbound,
                        allOf(
                            not(seen),
                            direction notEqualTo outbound
                        )
                    )
                }
            }
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

        val addresses = listOf(EmailAddress("42"))

        with(result.items[0]) {
            messageId shouldBe "messageId"
            userId shouldBe "userId"
            sudoId shouldBe "sudoId"
            emailAddressId shouldBe "emailAddressId"
            clientRefId shouldBe "clientRefId"
            from.shouldContainExactlyInAnyOrder(addresses)
            replyTo.shouldContainExactlyInAnyOrder(addresses)
            to.shouldContainExactlyInAnyOrder(addresses)
            cc.shouldContainExactlyInAnyOrder(addresses)
            bcc.shouldContainExactlyInAnyOrder(addresses)
            direction shouldBe EmailMessage.Direction.INBOUND
            subject shouldBe "42"
            seen shouldBe false
            state shouldBe EmailMessage.State.DELIVERED
            createdAt shouldBe Date(1L)
            updatedAt shouldBe Date(1L)
        }

        verify(mockAppSyncClient).query(any<ListEmailMessagesQuery>())
        verify(mockKeyManager, times(6)).decryptWithPrivateKey(anyString(), any(), any())
        verify(mockKeyManager, times(6)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
    }

    @Test
    fun `listEmailMessages() should return results when populating nextToken`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val queryResultWithNextToken by before {
            ListEmailMessagesQuery.ListEmailMessages(
                "typename",
                listOf(
                    ListEmailMessagesQuery.Item(
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
                ),
                "dummyNextToken"
            )
        }

        val inputWithNextToken by before {
            ListEmailMessagesInput.builder()
                .emailAddressId(null)
                .sudoId(null)
                .filter(null)
                .limit(1)
                .nextToken("dummyNextToken")
                .build()
        }
        val responseWithNextToken by before {
            Response.builder<ListEmailMessagesQuery.Data>(ListEmailMessagesQuery(inputWithNextToken))
                .data(ListEmailMessagesQuery.Data(queryResultWithNextToken))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listEmailMessages(null, null, 1, "dummyNextToken", CachePolicy.REMOTE_ONLY)
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

        val addresses = listOf(EmailAddress("42"))

        with(result.items[0]) {
            messageId shouldBe "messageId"
            userId shouldBe "userId"
            sudoId shouldBe "sudoId"
            emailAddressId shouldBe "emailAddressId"
            clientRefId shouldBe "clientRefId"
            from.shouldContainExactlyInAnyOrder(addresses)
            replyTo.shouldContainExactlyInAnyOrder(addresses)
            to.shouldContainExactlyInAnyOrder(addresses)
            cc.shouldContainExactlyInAnyOrder(addresses)
            bcc.shouldContainExactlyInAnyOrder(addresses)
            direction shouldBe EmailMessage.Direction.INBOUND
            subject shouldBe "42"
            seen shouldBe false
            state shouldBe EmailMessage.State.DELIVERED
            createdAt shouldBe Date(1L)
            updatedAt shouldBe Date(1L)
        }

        verify(mockAppSyncClient).query(any<ListEmailMessagesQuery>())
        verify(mockKeyManager, times(6)).decryptWithPrivateKey(anyString(), any(), any())
        verify(mockKeyManager, times(6)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
    }

    @Test
    fun `listEmailMessages() should return results when providing sudoId`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val inputWithSudoId by before {
            ListEmailMessagesInput.builder()
                .emailAddressId(null)
                .sudoId("sudoId")
                .filter(null)
                .limit(1)
                .nextToken(null)
                .build()
        }
        val responseWithSudoId by before {
            Response.builder<ListEmailMessagesQuery.Data>(ListEmailMessagesQuery(inputWithSudoId))
                .data(ListEmailMessagesQuery.Data(queryResult))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listEmailMessages(null, "sudoId", 1, null, CachePolicy.REMOTE_ONLY)
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

        val addresses = listOf(EmailAddress("42"))

        with(result.items[0]) {
            messageId shouldBe "messageId"
            userId shouldBe "userId"
            sudoId shouldBe "sudoId"
            emailAddressId shouldBe "emailAddressId"
            clientRefId shouldBe "clientRefId"
            from.shouldContainExactlyInAnyOrder(addresses)
            replyTo.shouldContainExactlyInAnyOrder(addresses)
            to.shouldContainExactlyInAnyOrder(addresses)
            cc.shouldContainExactlyInAnyOrder(addresses)
            bcc.shouldContainExactlyInAnyOrder(addresses)
            direction shouldBe EmailMessage.Direction.INBOUND
            subject shouldBe "42"
            seen shouldBe false
            state shouldBe EmailMessage.State.DELIVERED
            createdAt shouldBe Date(1L)
            updatedAt shouldBe Date(1L)
        }

        verify(mockAppSyncClient).query(any<ListEmailMessagesQuery>())
        verify(mockKeyManager, times(6)).decryptWithPrivateKey(anyString(), any(), any())
        verify(mockKeyManager, times(6)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
    }

    @Test
    fun `listEmailMessages() should return results when providing emailAddressId and sudoId`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val inputWithEmailAddressId by before {
            ListEmailMessagesInput.builder()
                .emailAddressId("emailAddressId")
                .sudoId("sudoId")
                .filter(null)
                .limit(1)
                .nextToken(null)
                .build()
        }
        val responseWithEmailAddressId by before {
            Response.builder<ListEmailMessagesQuery.Data>(ListEmailMessagesQuery(inputWithEmailAddressId))
                .data(ListEmailMessagesQuery.Data(queryResult))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listEmailMessages("emailAddressId", "sudoId", 1, null, CachePolicy.REMOTE_ONLY)
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithEmailAddressId)

        val result = deferredResult.await()
        result shouldNotBe null
        result.items.isEmpty() shouldBe false
        result.items.size shouldBe 1
        result.nextToken shouldBe null

        val addresses = listOf(EmailAddress("42"))

        with(result.items[0]) {
            messageId shouldBe "messageId"
            userId shouldBe "userId"
            sudoId shouldBe "sudoId"
            emailAddressId shouldBe "emailAddressId"
            clientRefId shouldBe "clientRefId"
            from.shouldContainExactlyInAnyOrder(addresses)
            replyTo.shouldContainExactlyInAnyOrder(addresses)
            to.shouldContainExactlyInAnyOrder(addresses)
            cc.shouldContainExactlyInAnyOrder(addresses)
            bcc.shouldContainExactlyInAnyOrder(addresses)
            direction shouldBe EmailMessage.Direction.INBOUND
            subject shouldBe "42"
            seen shouldBe false
            state shouldBe EmailMessage.State.DELIVERED
            createdAt shouldBe Date(1L)
            updatedAt shouldBe Date(1L)
        }

        verify(mockAppSyncClient).query(any<ListEmailMessagesQuery>())
        verify(mockKeyManager, times(6)).decryptWithPrivateKey(anyString(), any(), any())
        verify(mockKeyManager, times(6)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
    }

    @Test
    fun `listEmailMessages() should return empty list output when query result data is empty`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val queryResultWithEmptyList by before {
            ListEmailMessagesQuery.ListEmailMessages(
                "typename",
                emptyList(),
                null
            )
        }

        val responseWithEmptyList by before {
            Response.builder<ListEmailMessagesQuery.Data>(ListEmailMessagesQuery(input))
                .data(ListEmailMessagesQuery.Data(queryResultWithEmptyList))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listEmailMessages()
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

        verify(mockAppSyncClient).query(any<ListEmailMessagesQuery>())
    }

    @Test
    fun `listEmailMessages() should return empty list output when query response is null`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val nullQueryResponse by before {
            Response.builder<ListEmailMessagesQuery.Data>(ListEmailMessagesQuery(input))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listEmailMessages()
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

        verify(mockAppSyncClient).query(any<ListEmailMessagesQuery>())
    }

    @Test
    fun `listEmailMessages() should throw when http error occurs`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.listEmailMessages()
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

        verify(mockAppSyncClient).query(any<ListEmailMessagesQuery>())
    }

    @Test
    fun `listEmailMessages() should throw when unknown error occurs`() = runBlocking<Unit> {

        queryHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<ListEmailMessagesQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.listEmailMessages()
            }
        }
        deferredResult.start()

        delay(100L)
        deferredResult.await()

        verify(mockAppSyncClient).query(any<ListEmailMessagesQuery>())
    }

    @Test
    fun `listEmailMessages() should not block coroutine cancellation exception`() = runBlocking<Unit> {

        mockAppSyncClient.stub {
            on { query(any<ListEmailMessagesQuery>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        shouldThrow<CancellationException> {
            client.listEmailMessages()
        }

        verify(mockAppSyncClient).query(any<ListEmailMessagesQuery>())
    }
}
