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
import com.sudoplatform.sudoemail.graphql.ListEmailAddressesQuery
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddress
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.fragment.EmailFolder
import com.sudoplatform.sudoemail.graphql.fragment.SealedAttribute
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.sealing.DefaultSealingService
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesInput
import com.sudoplatform.sudoemail.types.transformers.Unsealer
import com.sudoplatform.sudokeymanager.KeyManagerException
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
import com.sudoplatform.sudoemail.graphql.type.ListEmailAddressesInput as ListEmailAddressesRequest

/**
 * Test the correct operation of [SudoEmailClient.listEmailAddresses]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListEmailAddressesTest : BaseTests() {

    private val input by before {
        ListEmailAddressesRequest.builder()
            .limit(null)
            .nextToken(null)
            .build()
    }

    private val owners by before {
        listOf(EmailAddressWithoutFolders.Owner("typename", "ownerId", "issuer"))
    }

    private val folderOwners by before {
        listOf(EmailFolder.Owner("typename", "ownerId", "issuer"))
    }

    private val folders by before {
        listOf(
            EmailAddress.Folder(
                "typename",
                EmailAddress.Folder.Fragments(
                    EmailFolder(
                        "EmailFolder",
                        "folderId",
                        "owner",
                        folderOwners,
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
        )
    }

    private val queryResult by before {
        ListEmailAddressesQuery.ListEmailAddresses(
            "typename",
            listOf(
                ListEmailAddressesQuery.Item(
                    "typename",
                    ListEmailAddressesQuery.Item.Fragments(
                        EmailAddress(
                            "typename",
                            folders,
                            EmailAddress.Fragments(
                                EmailAddressWithoutFolders(
                                    "typename",
                                    "emailAddressId",
                                    "owner",
                                    owners,
                                    "identityId",
                                    "keyRingId",
                                    emptyList(),
                                    1,
                                    1.0,
                                    1.0,
                                    1.0,
                                    "example@sudoplatform.com",
                                    0.0,
                                    null,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            null,
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
        queryHolder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockContext, mockUserClient, mockKeyManager, mockAppSyncClient, mockS3Client)
    }

    @Test
    fun `listEmailAddresses() should return results when no error present`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        val deferredResult = async(Dispatchers.IO) {
            client.listEmailAddresses(input)
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(queryResponse)

        val listEmailAddresses = deferredResult.await()
        listEmailAddresses shouldNotBe null

        when (listEmailAddresses) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.size shouldBe 1
                listEmailAddresses.result.nextToken shouldBe null

                with(listEmailAddresses.result.items[0]) {
                    id shouldBe "emailAddressId"
                    owner shouldBe "owner"
                    owners.first().id shouldBe "ownerId"
                    owners.first().issuer shouldBe "issuer"
                    emailAddress shouldBe "example@sudoplatform.com"
                    size shouldBe 0.0
                    version shouldBe 1
                    createdAt shouldBe Date(1L)
                    updatedAt shouldBe Date(1L)
                    lastReceivedAt shouldBe Date(1L)
                    folders.size shouldBe 1
                    with(folders[0]) {
                        id shouldBe "folderId"
                        owner shouldBe "owner"
                        owners.first().id shouldBe "ownerId"
                        owners.first().issuer shouldBe "issuer"
                        emailAddressId shouldBe "emailAddressId"
                        folderName shouldBe "folderName"
                        size shouldBe 0.0
                        unseenCount shouldBe 0.0
                        version shouldBe 1
                        createdAt shouldBe Date(1L)
                        updatedAt shouldBe Date(1L)
                    }
                }
            }
            else -> { fail("Unexpected ListAPIResult") }
        }

        verify(mockAppSyncClient).query<ListEmailAddressesQuery.Data, ListEmailAddressesQuery, ListEmailAddressesQuery.Variables>(
            check {
                it.variables().input().limit() shouldBe 10
                it.variables().input().nextToken() shouldBe null
            },
        )
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
                        ListEmailAddressesQuery.Item.Fragments(
                            EmailAddress(
                                "typename",
                                folders,
                                EmailAddress.Fragments(
                                    EmailAddressWithoutFolders(
                                        "typename",
                                        "emailAddressId",
                                        "owner",
                                        emptyList(),
                                        "identityId",
                                        "keyRingId",
                                        emptyList(),
                                        1,
                                        1.0,
                                        1.0,
                                        1.0,
                                        "example@sudoplatform.com",
                                        0.0,
                                        null,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                "dummyNextToken",
            )
        }

        val inputWithNextToken by before {
            ListEmailAddressesRequest.builder()
                .limit(1)
                .nextToken("dummyNextToken")
                .build()
        }
        val responseWithNextToken by before {
            Response.builder<ListEmailAddressesQuery.Data>(ListEmailAddressesQuery(inputWithNextToken))
                .data(ListEmailAddressesQuery.Data(queryResultWithNextToken))
                .build()
        }

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY, 1, "dummyNextToken")
        val deferredResult = async(Dispatchers.IO) {
            client.listEmailAddresses(input)
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithNextToken)

        val listEmailAddresses = deferredResult.await()
        listEmailAddresses shouldNotBe null

        when (listEmailAddresses) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.size shouldBe 1
                listEmailAddresses.result.nextToken shouldBe "dummyNextToken"

                with(listEmailAddresses.result.items[0]) {
                    id shouldBe "emailAddressId"
                    owner shouldBe "owner"
                    owners shouldBe emptyList()
                    emailAddress shouldBe "example@sudoplatform.com"
                    size shouldBe 0.0
                    version shouldBe 1
                    createdAt shouldBe Date(1L)
                    updatedAt shouldBe Date(1L)
                    lastReceivedAt shouldBe Date(1L)
                }
            }
            else -> { fail("Unexpected ListAPIResult") }
        }

        verify(mockAppSyncClient).query<ListEmailAddressesQuery.Data, ListEmailAddressesQuery, ListEmailAddressesQuery.Variables>(
            check {
                it.variables().input().limit() shouldBe 1
                it.variables().input().nextToken() shouldBe "dummyNextToken"
            },
        )
    }

    @Test
    fun `listEmailAddresses() should return empty list output when query result data is empty`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val queryResultWithEmptyList by before {
            ListEmailAddressesQuery.ListEmailAddresses(
                "typename",
                emptyList(),
                null,
            )
        }

        val responseWithEmptyList by before {
            Response.builder<ListEmailAddressesQuery.Data>(ListEmailAddressesQuery(input))
                .data(ListEmailAddressesQuery.Data(queryResultWithEmptyList))
                .build()
        }

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        val deferredResult = async(Dispatchers.IO) {
            client.listEmailAddresses(input)
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithEmptyList)

        val result = deferredResult.await()
        result shouldNotBe null

        when (result) {
            is ListAPIResult.Success -> {
                result.result.items.isEmpty() shouldBe true
                result.result.items.size shouldBe 0
                result.result.nextToken shouldBe null
            }
            else -> { fail("Unexpected ListAPIResult") }
        }

        verify(mockAppSyncClient).query<ListEmailAddressesQuery.Data, ListEmailAddressesQuery, ListEmailAddressesQuery.Variables>(
            check {
                it.variables().input().limit() shouldBe 10
                it.variables().input().nextToken() shouldBe null
            },
        )
    }

    @Test
    fun `listEmailAddresses() should return empty list output when query response is null`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val nullQueryResponse by before {
            Response.builder<ListEmailAddressesQuery.Data>(ListEmailAddressesQuery(input))
                .data(null)
                .build()
        }

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        val deferredResult = async(Dispatchers.IO) {
            client.listEmailAddresses(input)
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(nullQueryResponse)

        val result = deferredResult.await()
        result shouldNotBe null

        when (result) {
            is ListAPIResult.Success -> {
                result.result.items.isEmpty() shouldBe true
                result.result.items.size shouldBe 0
                result.result.nextToken shouldBe null
            }
            else -> { fail("Unexpected ListAPIResult") }
        }

        verify(mockAppSyncClient).query<ListEmailAddressesQuery.Data, ListEmailAddressesQuery, ListEmailAddressesQuery.Variables>(
            check {
                it.variables().input().limit() shouldBe 10
                it.variables().input().nextToken() shouldBe null
            },
        )
    }

    @Test
    fun `listEmailAddresses() should return partial results when unsealing fails`() = runBlocking<Unit> {
        mockKeyManager.stub {
            on { decryptWithPrivateKey(anyString(), any(), any()) } doThrow KeyManagerException("KeyManagerException")
        }

        val queryResultWithAlias by before {
            ListEmailAddressesQuery.ListEmailAddresses(
                "typename",
                listOf(
                    ListEmailAddressesQuery.Item(
                        "typename",
                        ListEmailAddressesQuery.Item.Fragments(
                            EmailAddress(
                                "typename",
                                folders,
                                EmailAddress.Fragments(
                                    EmailAddressWithoutFolders(
                                        "typename",
                                        "emailAddressId",
                                        "owner",
                                        owners,
                                        "identityId",
                                        "keyRingId",
                                        emptyList(),
                                        1,
                                        1.0,
                                        1.0,
                                        1.0,
                                        "example@sudoplatform.com",
                                        0.0,
                                        EmailAddressWithoutFolders.Alias(
                                            "typename",
                                            EmailAddressWithoutFolders.Alias.Fragments(
                                                SealedAttribute(
                                                    "typename",
                                                    "algorithm",
                                                    "keyId",
                                                    "string",
                                                    "alias",
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                null,
            )
        }

        val responseWithAlias by before {
            Response.builder<ListEmailAddressesQuery.Data>(ListEmailAddressesQuery(input))
                .data(ListEmailAddressesQuery.Data(queryResultWithAlias))
                .build()
        }

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        val deferredResult = async(Dispatchers.IO) {
            client.listEmailAddresses(input)
        }
        deferredResult.start()

        delay(100L)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(responseWithAlias)

        val listEmailAddresses = deferredResult.await()
        listEmailAddresses shouldNotBe null

        when (listEmailAddresses) {
            is ListAPIResult.Partial -> {
                listEmailAddresses.result.items.size shouldBe 0
                listEmailAddresses.result.failed.size shouldBe 1
                listEmailAddresses.result.nextToken shouldBe null

                with(listEmailAddresses.result.failed[0].partial) {
                    id shouldBe "emailAddressId"
                    owner shouldBe "owner"
                    owners.first().id shouldBe "ownerId"
                    owners.first().issuer shouldBe "issuer"
                    emailAddress shouldBe "example@sudoplatform.com"
                    size shouldBe 0.0
                    version shouldBe 1
                    createdAt shouldBe Date(1L)
                    updatedAt shouldBe Date(1L)
                    lastReceivedAt shouldBe Date(1L)
                    folders.size shouldBe 1
                    with(folders[0]) {
                        id shouldBe "folderId"
                        owner shouldBe "owner"
                        owners.first().id shouldBe "ownerId"
                        owners.first().issuer shouldBe "issuer"
                        emailAddressId shouldBe "emailAddressId"
                        folderName shouldBe "folderName"
                        size shouldBe 0.0
                        unseenCount shouldBe 0.0
                        version shouldBe 1
                        createdAt shouldBe Date(1L)
                        updatedAt shouldBe Date(1L)
                    }
                }
            }
            else -> { fail("Unexpected ListAPIResult") }
        }

        verify(mockAppSyncClient).query<ListEmailAddressesQuery.Data, ListEmailAddressesQuery, ListEmailAddressesQuery.Variables>(
            check {
                it.variables().input().limit() shouldBe 10
                it.variables().input().nextToken() shouldBe null
            },
        )
    }

    @Test
    fun `listEmailAddresses() should throw when unsealing fails`() = runBlocking<Unit> {
        mockAppSyncClient.stub {
            on { query(any<ListEmailAddressesQuery>()) } doThrow
                Unsealer.UnsealerException.SealedDataTooShortException("Mock Unsealer Exception")
        }

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        shouldThrow<SudoEmailClient.EmailAddressException.UnsealingException> {
            client.listEmailAddresses(input)
        }

        verify(mockAppSyncClient).query<ListEmailAddressesQuery.Data, ListEmailAddressesQuery, ListEmailAddressesQuery.Variables>(
            check {
                it.variables().input().limit() shouldBe 10
                it.variables().input().nextToken() shouldBe null
            },
        )
    }

    @Test
    fun `listEmailAddresses() should throw when http error occurs`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                client.listEmailAddresses(input)
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

        verify(mockAppSyncClient).query<ListEmailAddressesQuery.Data, ListEmailAddressesQuery, ListEmailAddressesQuery.Variables>(
            check {
                it.variables().input().limit() shouldBe 10
                it.variables().input().nextToken() shouldBe null
            },
        )
    }

    @Test
    fun `listEmailAddresses() should throw when unknown error occurs`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<ListEmailAddressesQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.UnknownException> {
                client.listEmailAddresses(input)
            }
        }
        deferredResult.start()

        delay(100L)
        deferredResult.await()

        verify(mockAppSyncClient).query<ListEmailAddressesQuery.Data, ListEmailAddressesQuery, ListEmailAddressesQuery.Variables>(
            check {
                it.variables().input().limit() shouldBe 10
                it.variables().input().nextToken() shouldBe null
            },
        )
    }

    @Test
    fun `listEmailAddresses() should not block coroutine cancellation exception`() = runBlocking<Unit> {
        mockAppSyncClient.stub {
            on { query(any<ListEmailAddressesQuery>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        shouldThrow<CancellationException> {
            client.listEmailAddresses(input)
        }

        verify(mockAppSyncClient).query<ListEmailAddressesQuery.Data, ListEmailAddressesQuery, ListEmailAddressesQuery.Variables>(
            check {
                it.variables().input().limit() shouldBe 10
                it.variables().input().nextToken() shouldBe null
            },
        )
    }
}
