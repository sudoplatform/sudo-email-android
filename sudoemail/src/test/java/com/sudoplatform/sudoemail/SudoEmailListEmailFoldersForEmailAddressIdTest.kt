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
import com.sudoplatform.sudoemail.graphql.ListEmailFoldersForEmailAddressIdQuery
import com.sudoplatform.sudoemail.graphql.fragment.EmailFolder
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.inputs.ListEmailFoldersForEmailAddressIdInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
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
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.util.Date
import java.util.concurrent.CancellationException
import com.sudoplatform.sudoemail.graphql.type.ListEmailFoldersForEmailAddressIdInput as ListEmailFoldersForEmailAddressIdRequest

/**
 * Test the correct operation of [SudoEmailClient.listEmailFoldersForEmailAddressId]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListEmailFoldersForEmailAddressIdTest : BaseTests() {

    private val input by before {
        ListEmailFoldersForEmailAddressIdRequest.builder()
            .emailAddressId("emailAddressId")
            .limit(null)
            .nextToken(null)
            .build()
    }

    private val owners by before {
        listOf(EmailFolder.Owner("typename", "ownerId", "issuer"))
    }

    private val queryResult by before {
        ListEmailFoldersForEmailAddressIdQuery.ListEmailFoldersForEmailAddressId(
            "typename",
            listOf(
                ListEmailFoldersForEmailAddressIdQuery.Item(
                    "typename",
                    ListEmailFoldersForEmailAddressIdQuery.Item.Fragments(
                        EmailFolder(
                            "typename",
                            "folderId",
                            "owner",
                            owners,
                            1,
                            1.0,
                            1.0,
                            "emailAddressId",
                            "folderName",
                            0.0,
                            0.0,
                            1.0,
                        ),
                    ),
                ),
            ),
            null,
        )
    }

    private val queryResponse by before {
        Response.builder<ListEmailFoldersForEmailAddressIdQuery.Data>(ListEmailFoldersForEmailAddressIdQuery(input))
            .data(ListEmailFoldersForEmailAddressIdQuery.Data(queryResult))
            .build()
    }

    private val queryHolder = CallbackHolder<ListEmailFoldersForEmailAddressIdQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<ListEmailFoldersForEmailAddressIdQuery>()) } doReturn queryHolder.queryOperation
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

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        DefaultSealingService(
            mockDeviceKeyManager,
            mockLogger,
        )
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
            mockDeviceKeyManager,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
            "region",
            "identityBucket",
            "transientBucket",
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
    fun `listEmailFoldersForEmailAddressId() should return results when no error present`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val input = ListEmailFoldersForEmailAddressIdInput(
            emailAddressId = "emailAddressId",
            limit = 1,
            nextToken = null,
        )
        val deferredResult = async(Dispatchers.IO) {
            client.listEmailFoldersForEmailAddressId(input)
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
            id shouldBe "folderId"
            owner shouldBe "owner"
            owners.first().id shouldBe "ownerId"
            owners.first().issuer shouldBe "issuer"
            emailAddressId shouldBe "emailAddressId"
            folderName shouldBe "folderName"
            size shouldBe 0.0
            unseenCount shouldBe 0
            version shouldBe 1
            createdAt shouldBe Date(1L)
            updatedAt shouldBe Date(1L)
        }

        verify(mockAppSyncClient)
            .query<
                ListEmailFoldersForEmailAddressIdQuery.Data,
                ListEmailFoldersForEmailAddressIdQuery,
                ListEmailFoldersForEmailAddressIdQuery.Variables,
                >(
                check {
                    it.variables().input().emailAddressId() shouldBe "emailAddressId"
                    it.variables().input().limit() shouldBe 1
                    it.variables().input().nextToken() shouldBe null
                },
            )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should return results when populating nextToken`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val queryResultWithNextToken by before {
            ListEmailFoldersForEmailAddressIdQuery.ListEmailFoldersForEmailAddressId(
                "typename",
                listOf(
                    ListEmailFoldersForEmailAddressIdQuery.Item(
                        "typename",
                        ListEmailFoldersForEmailAddressIdQuery.Item.Fragments(
                            EmailFolder(
                                "typename",
                                "folderId",
                                "owner",
                                owners,
                                1,
                                1.0,
                                1.0,
                                "emailAddressId",
                                "folderName",
                                0.0,
                                0.0,
                                null,
                            ),
                        ),
                    ),
                ),
                "dummyNextToken",
            )
        }

        val inputWithNextToken by before {
            ListEmailFoldersForEmailAddressIdRequest.builder()
                .emailAddressId("emailAddressId")
                .limit(1)
                .nextToken("dummyNextToken")
                .build()
        }
        val responseWithNextToken by before {
            Response.builder<ListEmailFoldersForEmailAddressIdQuery.Data>(ListEmailFoldersForEmailAddressIdQuery(inputWithNextToken))
                .data(ListEmailFoldersForEmailAddressIdQuery.Data(queryResultWithNextToken))
                .build()
        }

        val input = ListEmailFoldersForEmailAddressIdInput(
            "emailAddressId",
            CachePolicy.REMOTE_ONLY,
            1,
            "dummyNextToken",
        )
        val deferredResult = async(Dispatchers.IO) {
            client.listEmailFoldersForEmailAddressId(input)
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
            id shouldBe "folderId"
            owner shouldBe "owner"
            owners.first().id shouldBe "ownerId"
            owners.first().issuer shouldBe "issuer"
            emailAddressId shouldBe "emailAddressId"
            folderName shouldBe "folderName"
            size shouldBe 0.0
            unseenCount shouldBe 0
            version shouldBe 1
            createdAt shouldBe Date(1L)
            updatedAt shouldBe Date(1L)
        }

        verify(mockAppSyncClient)
            .query<
                ListEmailFoldersForEmailAddressIdQuery.Data,
                ListEmailFoldersForEmailAddressIdQuery,
                ListEmailFoldersForEmailAddressIdQuery.Variables,
                >(
                check {
                    it.variables().input().emailAddressId() shouldBe "emailAddressId"
                    it.variables().input().limit() shouldBe 1
                    it.variables().input().nextToken() shouldBe "dummyNextToken"
                },
            )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should return empty list output when query result data is empty`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val queryResultWithEmptyList by before {
            ListEmailFoldersForEmailAddressIdQuery.ListEmailFoldersForEmailAddressId(
                "typename",
                emptyList(),
                null,
            )
        }

        val responseWithEmptyList by before {
            Response.builder<ListEmailFoldersForEmailAddressIdQuery.Data>(ListEmailFoldersForEmailAddressIdQuery(input))
                .data(ListEmailFoldersForEmailAddressIdQuery.Data(queryResultWithEmptyList))
                .build()
        }

        val input = ListEmailFoldersForEmailAddressIdInput("emailAddressId")
        val deferredResult = async(Dispatchers.IO) {
            client.listEmailFoldersForEmailAddressId(input)
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

        verify(mockAppSyncClient)
            .query<
                ListEmailFoldersForEmailAddressIdQuery.Data,
                ListEmailFoldersForEmailAddressIdQuery,
                ListEmailFoldersForEmailAddressIdQuery.Variables,
                >(
                check {
                    it.variables().input().emailAddressId() shouldBe "emailAddressId"
                    it.variables().input().limit() shouldBe 10
                    it.variables().input().nextToken() shouldBe null
                },
            )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should return empty list output when query response is null`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val nullQueryResponse by before {
            Response.builder<ListEmailFoldersForEmailAddressIdQuery.Data>(ListEmailFoldersForEmailAddressIdQuery(input))
                .data(null)
                .build()
        }

        val input = ListEmailFoldersForEmailAddressIdInput("emailAddressId")
        val deferredResult = async(Dispatchers.IO) {
            client.listEmailFoldersForEmailAddressId(input)
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

        verify(mockAppSyncClient)
            .query<
                ListEmailFoldersForEmailAddressIdQuery.Data,
                ListEmailFoldersForEmailAddressIdQuery,
                ListEmailFoldersForEmailAddressIdQuery.Variables,
                >(
                check {
                    it.variables().input().emailAddressId() shouldBe "emailAddressId"
                    it.variables().input().limit() shouldBe 10
                    it.variables().input().nextToken() shouldBe null
                },
            )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should throw when http error occurs`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val input = ListEmailFoldersForEmailAddressIdInput("emailAddressId")
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
                client.listEmailFoldersForEmailAddressId(input)
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

        verify(mockAppSyncClient)
            .query<
                ListEmailFoldersForEmailAddressIdQuery.Data,
                ListEmailFoldersForEmailAddressIdQuery,
                ListEmailFoldersForEmailAddressIdQuery.Variables,
                >(
                check {
                    it.variables().input().emailAddressId() shouldBe "emailAddressId"
                    it.variables().input().limit() shouldBe 10
                    it.variables().input().nextToken() shouldBe null
                },
            )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should throw when unknown error occurs`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<ListEmailFoldersForEmailAddressIdQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val input = ListEmailFoldersForEmailAddressIdInput("emailAddressId")
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailFolderException.UnknownException> {
                client.listEmailFoldersForEmailAddressId(input)
            }
        }
        deferredResult.start()

        delay(100L)
        deferredResult.await()

        verify(mockAppSyncClient)
            .query<
                ListEmailFoldersForEmailAddressIdQuery.Data,
                ListEmailFoldersForEmailAddressIdQuery,
                ListEmailFoldersForEmailAddressIdQuery.Variables,
                >(
                check {
                    it.variables().input().emailAddressId() shouldBe "emailAddressId"
                    it.variables().input().limit() shouldBe 10
                    it.variables().input().nextToken() shouldBe null
                },
            )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should not block coroutine cancellation exception`() = runBlocking<Unit> {
        mockAppSyncClient.stub {
            on { query(any<ListEmailFoldersForEmailAddressIdQuery>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        val input = ListEmailFoldersForEmailAddressIdInput("emailAddressId")
        shouldThrow<CancellationException> {
            client.listEmailFoldersForEmailAddressId(input)
        }

        verify(mockAppSyncClient).query(any<ListEmailFoldersForEmailAddressIdQuery>())
    }
}
