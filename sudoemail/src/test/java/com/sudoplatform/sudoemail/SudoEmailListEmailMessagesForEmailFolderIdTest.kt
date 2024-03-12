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
import com.sudoplatform.sudoemail.graphql.ListEmailMessagesForEmailFolderIdQuery
import com.sudoplatform.sudoemail.graphql.fragment.SealedEmailMessage
import com.sudoplatform.sudoemail.graphql.type.EmailMessageDirection
import com.sudoplatform.sudoemail.graphql.type.EmailMessageEncryptionStatus
import com.sudoplatform.sudoemail.graphql.type.EmailMessageState
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.sealing.DefaultSealingService
import com.sudoplatform.sudoemail.types.DateRange
import com.sudoplatform.sudoemail.types.Direction
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EmailMessageDateRange
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.SortOrder
import com.sudoplatform.sudoemail.types.State
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoemail.types.transformers.Unsealer
import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.doubles.shouldBeLessThan
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
import java.util.Date
import java.util.concurrent.CancellationException
import com.sudoplatform.sudoemail.graphql.type.ListEmailMessagesForEmailFolderIdInput as ListEmailMessagesForEmailFolderIdRequest
import com.sudoplatform.sudoemail.graphql.type.SortOrder as SortOrderEntity

/**
 * Test the correct operation of [SudoEmailClient.listEmailMessagesForEmailFolderId] using mocks
 * and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListEmailMessagesForEmailFolderIdTest : BaseTests() {

    private fun mockSeal(value: String): String {
        val valueBytes = value.toByteArray()
        val data = ByteArray(256)
        valueBytes.copyInto(data)
        return Base64.encodeBase64String(data)
    }

    private val input by before {
        ListEmailMessagesForEmailFolderIdRequest.builder()
            .folderId("folderId")
            .build()
    }

    private val unsealedHeaderDetailsString =
        "{\"bcc\":[],\"to\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"from\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"cc\":" +
            "[{\"emailAddress\":\"foobar@unittest.org\"}],\"replyTo\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"subject\":" +
            "\"testSubject\",\"hasAttachments\":false}"
    private val unsealedHeaderDetailsHasAttachmentsTrueString =
        "{\"bcc\":[],\"to\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"from\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"cc\":" +
            "[],\"replyTo\":[],\"subject\":\"testSubject\",\"hasAttachments\":true}"
    private val unsealedHeaderDetailsHasAttachmentsUnsetString =
        "{\"bcc\":[],\"to\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"from\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"cc\":" +
            "[],\"replyTo\":[],\"subject\":\"testSubject\"}"

    private val queryResultItem by before {
        ListEmailMessagesForEmailFolderIdQuery.Item(
            "typename",
            ListEmailMessagesForEmailFolderIdQuery.Item.Fragments(
                SealedEmailMessage(
                    "typename",
                    "id",
                    "owner",
                    emptyList(),
                    "emailAddressId",
                    1,
                    1.0,
                    1.0,
                    1.0,
                    "folderId",
                    "previousFolderId",
                    EmailMessageDirection.INBOUND,
                    false,
                    EmailMessageState.DELIVERED,
                    "clientRefId",
                    SealedEmailMessage.Rfc822Header(
                        "typename",
                        "algorithm",
                        "keyId",
                        "plainText",
                        mockSeal(unsealedHeaderDetailsString),
                    ),
                    1.0,
                    EmailMessageEncryptionStatus.UNENCRYPTED,
                ),
            ),
        )
    }

    private val queryResult by before {
        ListEmailMessagesForEmailFolderIdQuery.ListEmailMessagesForEmailFolderId(
            "typename",
            listOf(queryResultItem),
            null,
        )
    }

    private val queryResponse by before {
        Response.builder<ListEmailMessagesForEmailFolderIdQuery.Data>(
            ListEmailMessagesForEmailFolderIdQuery(input),
        )
            .data(ListEmailMessagesForEmailFolderIdQuery.Data(queryResult))
            .build()
    }

    private val queryHolder = CallbackHolder<ListEmailMessagesForEmailFolderIdQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<ListEmailMessagesForEmailFolderIdQuery>()) } doReturn queryHolder.queryOperation
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { decryptWithPrivateKey(anyString(), any(), any()) } doReturn ByteArray(42)
            on {
                decryptWithSymmetricKey(
                    any<ByteArray>(),
                    any<ByteArray>(),
                )
            } doReturn unsealedHeaderDetailsString.toByteArray()
        }
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
        verifyNoMoreInteractions(
            mockContext,
            mockUserClient,
            mockKeyManager,
            mockAppSyncClient,
            mockS3Client,
        )
    }

    @Test
    fun `listEmailMessagesForEmailFolderId() should return results when no error present`() =
        runBlocking<Unit> {
            queryHolder.callback shouldBe null

            val input = ListEmailMessagesForEmailFolderIdInput(
                folderId = "folderId",
                limit = 1,
                nextToken = null,
                dateRange = EmailMessageDateRange(
                    sortDate = DateRange(Date(), Date()),
                ),
                sortOrder = SortOrder.DESC,
            )
            val deferredResult = async(Dispatchers.IO) {
                client.listEmailMessagesForEmailFolderId(
                    input,
                )
            }
            deferredResult.start()

            delay(100L)
            queryHolder.callback shouldNotBe null
            queryHolder.callback?.onResponse(queryResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            val listEmailMessages = deferredResult.await()
            listEmailMessages shouldNotBe null

            when (listEmailMessages) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.isEmpty() shouldBe false
                    listEmailMessages.result.items.size shouldBe 1
                    listEmailMessages.result.nextToken shouldBe null

                    val addresses = listOf(EmailMessage.EmailAddress("foobar@unittest.org"))
                    with(listEmailMessages.result.items[0]) {
                        id shouldBe "id"
                        owner shouldBe "owner"
                        owners shouldBe emptyList()
                        emailAddressId shouldBe "emailAddressId"
                        clientRefId shouldBe "clientRefId"
                        from.shouldContainExactlyInAnyOrder(addresses)
                        to.shouldContainExactlyInAnyOrder(addresses)
                        cc.shouldContainExactlyInAnyOrder(addresses)
                        replyTo.shouldContainExactlyInAnyOrder(addresses)
                        bcc.isEmpty() shouldBe true
                        direction shouldBe Direction.INBOUND
                        subject shouldBe "testSubject"
                        hasAttachments shouldBe false
                        seen shouldBe false
                        state shouldBe State.DELIVERED
                        createdAt shouldBe Date(1L)
                        updatedAt shouldBe Date(1L)
                    }
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockAppSyncClient)
                .query<
                    ListEmailMessagesForEmailFolderIdQuery.Data,
                    ListEmailMessagesForEmailFolderIdQuery,
                    ListEmailMessagesForEmailFolderIdQuery.Variables,
                    >(
                    check {
                        it.variables().input().folderId() shouldBe "folderId"
                        it.variables().input().limit() shouldBe 1
                        it.variables().input().nextToken() shouldBe null
                        it.variables().input().specifiedDateRange()?.sortDateEpochMs()
                            ?.startDateEpochMs()?.shouldBeLessThan(
                                Date().time.toDouble(),
                            )
                        it.variables().input().specifiedDateRange()?.sortDateEpochMs()
                            ?.endDateEpochMs()?.shouldBeLessThan(
                                Date().time.toDouble(),
                            )
                        it.variables().input().sortOrder() shouldBe SortOrderEntity.DESC
                    },
                )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should return results when hasAttachments is true`() =
        runBlocking<Unit> {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    unsealedHeaderDetailsHasAttachmentsTrueString.toByteArray()
            }

            queryHolder.callback shouldBe null

            val input = ListEmailMessagesForEmailFolderIdInput(
                folderId = "folderId",
                limit = 1,
                nextToken = null,
                dateRange = EmailMessageDateRange(
                    sortDate = DateRange(Date(), Date()),
                ),
                sortOrder = SortOrder.DESC,
            )
            val deferredResult = async(Dispatchers.IO) {
                client.listEmailMessagesForEmailFolderId(
                    input,
                )
            }
            deferredResult.start()

            delay(100L)
            queryHolder.callback shouldNotBe null
            queryHolder.callback?.onResponse(queryResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            val listEmailMessages = deferredResult.await()
            listEmailMessages shouldNotBe null

            when (listEmailMessages) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.isEmpty() shouldBe false
                    listEmailMessages.result.items.size shouldBe 1
                    listEmailMessages.result.nextToken shouldBe null

                    val addresses = listOf(EmailMessage.EmailAddress("foobar@unittest.org"))
                    with(listEmailMessages.result.items[0]) {
                        id shouldBe "id"
                        owner shouldBe "owner"
                        owners shouldBe emptyList()
                        emailAddressId shouldBe "emailAddressId"
                        clientRefId shouldBe "clientRefId"
                        from.shouldContainExactlyInAnyOrder(addresses)
                        to.shouldContainExactlyInAnyOrder(addresses)
                        cc.isEmpty() shouldBe true
                        replyTo.isEmpty() shouldBe true
                        bcc.isEmpty() shouldBe true
                        direction shouldBe Direction.INBOUND
                        subject shouldBe "testSubject"
                        hasAttachments shouldBe true
                        seen shouldBe false
                        state shouldBe State.DELIVERED
                        createdAt shouldBe Date(1L)
                        updatedAt shouldBe Date(1L)
                    }
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockAppSyncClient)
                .query<
                    ListEmailMessagesForEmailFolderIdQuery.Data,
                    ListEmailMessagesForEmailFolderIdQuery,
                    ListEmailMessagesForEmailFolderIdQuery.Variables,
                    >(
                    check {
                        it.variables().input().folderId() shouldBe "folderId"
                        it.variables().input().limit() shouldBe 1
                        it.variables().input().nextToken() shouldBe null
                        it.variables().input().specifiedDateRange()?.sortDateEpochMs()
                            ?.startDateEpochMs()?.shouldBeLessThan(
                                Date().time.toDouble(),
                            )
                        it.variables().input().specifiedDateRange()?.sortDateEpochMs()
                            ?.endDateEpochMs()?.shouldBeLessThan(
                                Date().time.toDouble(),
                            )
                        it.variables().input().sortOrder() shouldBe SortOrderEntity.DESC
                    },
                )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should return results when hasAttachments is unset`() =
        runBlocking<Unit> {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    unsealedHeaderDetailsHasAttachmentsUnsetString.toByteArray()
            }

            queryHolder.callback shouldBe null

            val input = ListEmailMessagesForEmailFolderIdInput(
                folderId = "folderId",
                limit = 1,
                nextToken = null,
                dateRange = EmailMessageDateRange(
                    sortDate = DateRange(Date(), Date()),
                ),
                sortOrder = SortOrder.ASC,
            )
            val deferredResult = async(Dispatchers.IO) {
                client.listEmailMessagesForEmailFolderId(
                    input,
                )
            }
            deferredResult.start()

            delay(100L)
            queryHolder.callback shouldNotBe null
            queryHolder.callback?.onResponse(queryResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            val listEmailMessages = deferredResult.await()
            listEmailMessages shouldNotBe null

            when (listEmailMessages) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.isEmpty() shouldBe false
                    listEmailMessages.result.items.size shouldBe 1
                    listEmailMessages.result.nextToken shouldBe null

                    val addresses = listOf(EmailMessage.EmailAddress("foobar@unittest.org"))
                    with(listEmailMessages.result.items[0]) {
                        id shouldBe "id"
                        owner shouldBe "owner"
                        owners shouldBe emptyList()
                        emailAddressId shouldBe "emailAddressId"
                        clientRefId shouldBe "clientRefId"
                        from.shouldContainExactlyInAnyOrder(addresses)
                        to.shouldContainExactlyInAnyOrder(addresses)
                        cc.isEmpty() shouldBe true
                        replyTo.isEmpty() shouldBe true
                        bcc.isEmpty() shouldBe true
                        direction shouldBe Direction.INBOUND
                        subject shouldBe "testSubject"
                        hasAttachments shouldBe false
                        seen shouldBe false
                        state shouldBe State.DELIVERED
                        createdAt shouldBe Date(1L)
                        updatedAt shouldBe Date(1L)
                    }
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockAppSyncClient)
                .query<
                    ListEmailMessagesForEmailFolderIdQuery.Data,
                    ListEmailMessagesForEmailFolderIdQuery,
                    ListEmailMessagesForEmailFolderIdQuery.Variables,
                    >(
                    check {
                        it.variables().input().folderId() shouldBe "folderId"
                        it.variables().input().limit() shouldBe 1
                        it.variables().input().nextToken() shouldBe null
                        it.variables().input().specifiedDateRange()?.sortDateEpochMs()
                            ?.startDateEpochMs()?.shouldBeLessThan(
                                Date().time.toDouble(),
                            )
                        it.variables().input().specifiedDateRange()?.sortDateEpochMs()
                            ?.endDateEpochMs()?.shouldBeLessThan(
                                Date().time.toDouble(),
                            )
                        it.variables().input().sortOrder() shouldBe SortOrderEntity.ASC
                    },
                )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should return results when updatedAt date range is specified`() =
        runBlocking<Unit> {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    unsealedHeaderDetailsHasAttachmentsTrueString.toByteArray()
            }

            queryHolder.callback shouldBe null

            val input = ListEmailMessagesForEmailFolderIdInput(
                folderId = "folderId",
                limit = 1,
                nextToken = null,
                dateRange = EmailMessageDateRange(
                    updatedAt = DateRange(Date(), Date()),
                ),
                sortOrder = SortOrder.DESC,
            )
            val deferredResult = async(Dispatchers.IO) {
                client.listEmailMessagesForEmailFolderId(
                    input,
                )
            }
            deferredResult.start()

            delay(100L)
            queryHolder.callback shouldNotBe null
            queryHolder.callback?.onResponse(queryResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            val listEmailMessages = deferredResult.await()
            listEmailMessages shouldNotBe null

            when (listEmailMessages) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.isEmpty() shouldBe false
                    listEmailMessages.result.items.size shouldBe 1
                    listEmailMessages.result.nextToken shouldBe null

                    val addresses = listOf(EmailMessage.EmailAddress("foobar@unittest.org"))
                    with(listEmailMessages.result.items[0]) {
                        id shouldBe "id"
                        owner shouldBe "owner"
                        owners shouldBe emptyList()
                        emailAddressId shouldBe "emailAddressId"
                        clientRefId shouldBe "clientRefId"
                        from.shouldContainExactlyInAnyOrder(addresses)
                        to.shouldContainExactlyInAnyOrder(addresses)
                        cc.isEmpty() shouldBe true
                        replyTo.isEmpty() shouldBe true
                        bcc.isEmpty() shouldBe true
                        direction shouldBe Direction.INBOUND
                        subject shouldBe "testSubject"
                        hasAttachments shouldBe true
                        seen shouldBe false
                        state shouldBe State.DELIVERED
                        createdAt shouldBe Date(1L)
                        updatedAt shouldBe Date(1L)
                    }
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockAppSyncClient)
                .query<
                    ListEmailMessagesForEmailFolderIdQuery.Data,
                    ListEmailMessagesForEmailFolderIdQuery,
                    ListEmailMessagesForEmailFolderIdQuery.Variables,
                    >(
                    check {
                        it.variables().input().folderId() shouldBe "folderId"
                        it.variables().input().limit() shouldBe 1
                        it.variables().input().nextToken() shouldBe null
                        it.variables().input().specifiedDateRange()?.updatedAtEpochMs()
                            ?.startDateEpochMs()?.shouldBeLessThan(
                                Date().time.toDouble(),
                            )
                        it.variables().input().specifiedDateRange()?.updatedAtEpochMs()
                            ?.endDateEpochMs()?.shouldBeLessThan(
                                Date().time.toDouble(),
                            )
                        it.variables().input().sortOrder() shouldBe SortOrderEntity.DESC
                    },
                )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should return success result using default inputs when no error present`() =
        runBlocking<Unit>
        {
            queryHolder.callback shouldBe null

            val input = ListEmailMessagesForEmailFolderIdInput(
                folderId = "folderId",
            )
            val deferredResult = async(Dispatchers.IO) {
                client.listEmailMessagesForEmailFolderId(input)
            }
            deferredResult.start()

            delay(100L)
            queryHolder.callback shouldNotBe null
            queryHolder.callback?.onResponse(queryResponse)

            val listEmailMessages = deferredResult.await()
            listEmailMessages shouldNotBe null

            when (listEmailMessages) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.isEmpty() shouldBe false
                    listEmailMessages.result.items.size shouldBe 1
                    listEmailMessages.result.nextToken shouldBe null

                    val addresses = listOf(EmailMessage.EmailAddress("foobar@unittest.org"))
                    with(listEmailMessages.result.items[0]) {
                        id shouldBe "id"
                        owner shouldBe "owner"
                        owners shouldBe emptyList()
                        emailAddressId shouldBe "emailAddressId"
                        clientRefId shouldBe "clientRefId"
                        from.shouldContainExactlyInAnyOrder(addresses)
                        to.shouldContainExactlyInAnyOrder(addresses)
                        cc.shouldContainExactlyInAnyOrder(addresses)
                        replyTo.shouldContainExactlyInAnyOrder(addresses)
                        bcc.isEmpty() shouldBe true
                        direction shouldBe Direction.INBOUND
                        subject shouldBe "testSubject"
                        hasAttachments shouldBe false
                        seen shouldBe false
                        state shouldBe State.DELIVERED
                        createdAt shouldBe Date(1L)
                        updatedAt shouldBe Date(1L)
                    }
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockAppSyncClient)
                .query<
                    ListEmailMessagesForEmailFolderIdQuery.Data,
                    ListEmailMessagesForEmailFolderIdQuery,
                    ListEmailMessagesForEmailFolderIdQuery.Variables,
                    >(
                    check {
                        it.variables().input().folderId() shouldBe "folderId"
                        it.variables().input().limit() shouldBe 10
                        it.variables().input().nextToken() shouldBe null
                        it.variables().input().specifiedDateRange() shouldBe null
                        it.variables().input().sortOrder() shouldBe SortOrderEntity.DESC
                    },
                )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should return success result when populating nextToken`() =
        runBlocking<Unit> {
            queryHolder.callback shouldBe null

            val queryResultWithNextToken by before {
                ListEmailMessagesForEmailFolderIdQuery.ListEmailMessagesForEmailFolderId(
                    "typename",
                    listOf(queryResultItem),
                    "dummyNextToken",
                )
            }
            val queryInput by before {
                ListEmailMessagesForEmailFolderIdRequest.builder()
                    .folderId("emailFolderId")
                    .nextToken("dummyNextToken")
                    .build()
            }
            val responseWithNextToken by before {
                Response.builder<ListEmailMessagesForEmailFolderIdQuery.Data>(
                    ListEmailMessagesForEmailFolderIdQuery(
                        queryInput,
                    ),
                )
                    .data(ListEmailMessagesForEmailFolderIdQuery.Data(queryResultWithNextToken))
                    .build()
            }

            val input = ListEmailMessagesForEmailFolderIdInput(
                folderId = "folderId",
                nextToken = "dummyNextToken",
            )
            val deferredResult = async(Dispatchers.IO) {
                client.listEmailMessagesForEmailFolderId(input)
            }
            deferredResult.start()

            delay(100L)
            queryHolder.callback shouldNotBe null
            queryHolder.callback?.onResponse(responseWithNextToken)

            val listEmailMessages = deferredResult.await()
            listEmailMessages shouldNotBe null

            when (listEmailMessages) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.isEmpty() shouldBe false
                    listEmailMessages.result.items.size shouldBe 1
                    listEmailMessages.result.nextToken shouldBe "dummyNextToken"

                    val addresses = listOf(EmailMessage.EmailAddress("foobar@unittest.org"))
                    with(listEmailMessages.result.items[0]) {
                        id shouldBe "id"
                        owner shouldBe "owner"
                        owners shouldBe emptyList()
                        emailAddressId shouldBe "emailAddressId"
                        clientRefId shouldBe "clientRefId"
                        from.shouldContainExactlyInAnyOrder(addresses)
                        to.shouldContainExactlyInAnyOrder(addresses)
                        cc.shouldContainExactlyInAnyOrder(addresses)
                        replyTo.shouldContainExactlyInAnyOrder(addresses)
                        bcc.isEmpty() shouldBe true
                        direction shouldBe Direction.INBOUND
                        subject shouldBe "testSubject"
                        hasAttachments shouldBe false
                        seen shouldBe false
                        state shouldBe State.DELIVERED
                        createdAt shouldBe Date(1L)
                        updatedAt shouldBe Date(1L)
                    }
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockAppSyncClient)
                .query<
                    ListEmailMessagesForEmailFolderIdQuery.Data,
                    ListEmailMessagesForEmailFolderIdQuery,
                    ListEmailMessagesForEmailFolderIdQuery.Variables,
                    >(
                    check {
                        it.variables().input().folderId() shouldBe "folderId"
                        it.variables().input().limit() shouldBe 10
                        it.variables().input().nextToken() shouldBe "dummyNextToken"
                        it.variables().input().specifiedDateRange() shouldBe null
                        it.variables().input().sortOrder() shouldBe SortOrderEntity.DESC
                    },
                )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should return success empty list result when query result data is empty`() =
        runBlocking<Unit>
        {
            queryHolder.callback shouldBe null

            val queryResultWithEmptyList by before {
                ListEmailMessagesForEmailFolderIdQuery.ListEmailMessagesForEmailFolderId(
                    "typename",
                    emptyList(),
                    null,
                )
            }

            val responseWithEmptyList by before {
                Response.builder<ListEmailMessagesForEmailFolderIdQuery.Data>(
                    ListEmailMessagesForEmailFolderIdQuery(input),
                )
                    .data(ListEmailMessagesForEmailFolderIdQuery.Data(queryResultWithEmptyList))
                    .build()
            }

            val input = ListEmailMessagesForEmailFolderIdInput(
                folderId = "folderId",
            )
            val deferredResult = async(Dispatchers.IO) {
                client.listEmailMessagesForEmailFolderId(input)
            }
            deferredResult.start()

            delay(100L)
            queryHolder.callback shouldNotBe null
            queryHolder.callback?.onResponse(responseWithEmptyList)

            val listEmailMessages = deferredResult.await()
            listEmailMessages shouldNotBe null

            when (listEmailMessages) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.isEmpty() shouldBe true
                    listEmailMessages.result.items.size shouldBe 0
                    listEmailMessages.result.nextToken shouldBe null
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockAppSyncClient)
                .query<
                    ListEmailMessagesForEmailFolderIdQuery.Data,
                    ListEmailMessagesForEmailFolderIdQuery,
                    ListEmailMessagesForEmailFolderIdQuery.Variables,
                    >(
                    check {
                        it.variables().input().folderId() shouldBe "folderId"
                        it.variables().input().limit() shouldBe 10
                        it.variables().input().nextToken() shouldBe null
                        it.variables().input().specifiedDateRange() shouldBe null
                        it.variables().input().sortOrder() shouldBe SortOrderEntity.DESC
                    },
                )
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should return success empty list result when query result data is null`() =
        runBlocking<Unit>
        {
            queryHolder.callback shouldBe null

            val responseWithNullData by before {
                Response.builder<ListEmailMessagesForEmailFolderIdQuery.Data>(
                    ListEmailMessagesForEmailFolderIdQuery(input),
                )
                    .data(null)
                    .build()
            }

            val input = ListEmailMessagesForEmailFolderIdInput(
                folderId = "folderId",
            )
            val deferredResult = async(Dispatchers.IO) {
                client.listEmailMessagesForEmailFolderId(input)
            }
            deferredResult.start()

            delay(100L)
            queryHolder.callback shouldNotBe null
            queryHolder.callback?.onResponse(responseWithNullData)

            val listEmailMessages = deferredResult.await()
            listEmailMessages shouldNotBe null

            when (listEmailMessages) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.isEmpty() shouldBe true
                    listEmailMessages.result.items.size shouldBe 0
                    listEmailMessages.result.nextToken shouldBe null
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockAppSyncClient)
                .query<
                    ListEmailMessagesForEmailFolderIdQuery.Data,
                    ListEmailMessagesForEmailFolderIdQuery,
                    ListEmailMessagesForEmailFolderIdQuery.Variables,
                    >(
                    check {
                        it.variables().input().folderId() shouldBe "folderId"
                        it.variables().input().limit() shouldBe 10
                        it.variables().input().nextToken() shouldBe null
                        it.variables().input().specifiedDateRange() shouldBe null
                        it.variables().input().sortOrder() shouldBe SortOrderEntity.DESC
                    },
                )
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should return partial results when unsealing fails`() =
        runBlocking<Unit> {
            mockKeyManager.stub {
                on { decryptWithPrivateKey(anyString(), any(), any()) } doThrow KeyManagerException(
                    "KeyManagerException",
                )
            }

            val input = ListEmailMessagesForEmailFolderIdInput(
                folderId = "folderId",
            )
            val deferredResult = async(Dispatchers.IO) {
                client.listEmailMessagesForEmailFolderId(input)
            }
            deferredResult.start()

            delay(100L)
            queryHolder.callback shouldNotBe null
            queryHolder.callback?.onResponse(queryResponse)

            val listEmailMessages = deferredResult.await()
            listEmailMessages shouldNotBe null

            when (listEmailMessages) {
                is ListAPIResult.Partial -> {
                    listEmailMessages.result.items.isEmpty() shouldBe true
                    listEmailMessages.result.items.size shouldBe 0
                    listEmailMessages.result.failed.isEmpty() shouldBe false
                    listEmailMessages.result.failed.size shouldBe 1
                    listEmailMessages.result.nextToken shouldBe null

                    with(listEmailMessages.result.failed[0].partial) {
                        id shouldBe "id"
                        owner shouldBe "owner"
                        owners shouldBe emptyList()
                        emailAddressId shouldBe "emailAddressId"
                        clientRefId shouldBe "clientRefId"
                        direction shouldBe Direction.INBOUND
                        seen shouldBe false
                        state shouldBe State.DELIVERED
                        createdAt shouldBe Date(1L)
                        updatedAt shouldBe Date(1L)
                    }
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockAppSyncClient).query(any<ListEmailMessagesForEmailFolderIdQuery>())
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should throw when unsealing fails`() =
        runBlocking<Unit> {
            mockAppSyncClient.stub {
                on { query(any<ListEmailMessagesForEmailFolderIdQuery>()) } doThrow
                    Unsealer.UnsealerException.SealedDataTooShortException("Mock Unsealer Exception")
            }

            val input = ListEmailMessagesForEmailFolderIdInput(
                folderId = "folderId",
            )
            val deferredResult = async(Dispatchers.IO) {
                shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                    client.listEmailMessagesForEmailFolderId(input)
                }
            }
            deferredResult.start()
            delay(100L)

            verify(mockAppSyncClient)
                .query<
                    ListEmailMessagesForEmailFolderIdQuery.Data,
                    ListEmailMessagesForEmailFolderIdQuery,
                    ListEmailMessagesForEmailFolderIdQuery.Variables,
                    >(
                    check {
                        it.variables().input().folderId() shouldBe "folderId"
                        it.variables().input().limit() shouldBe 10
                        it.variables().input().nextToken() shouldBe null
                        it.variables().input().specifiedDateRange() shouldBe null
                        it.variables().input().sortOrder() shouldBe SortOrderEntity.DESC
                    },
                )
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should throw when http error occurs`() =
        runBlocking<Unit> {
            queryHolder.callback shouldBe null

            val input = ListEmailMessagesForEmailFolderIdInput(
                folderId = "folderId",
            )
            val deferredResult = async(Dispatchers.IO) {
                shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                    client.listEmailMessagesForEmailFolderId(input)
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
                    ListEmailMessagesForEmailFolderIdQuery.Data,
                    ListEmailMessagesForEmailFolderIdQuery,
                    ListEmailMessagesForEmailFolderIdQuery.Variables,
                    >(
                    check {
                        it.variables().input().folderId() shouldBe "folderId"
                        it.variables().input().limit() shouldBe 10
                        it.variables().input().nextToken() shouldBe null
                        it.variables().input().specifiedDateRange() shouldBe null
                        it.variables().input().sortOrder() shouldBe SortOrderEntity.DESC
                    },
                )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should throw when unknown error occurs()`() =
        runBlocking<Unit> {
            queryHolder.callback shouldBe null

            mockAppSyncClient.stub {
                on { query(any<ListEmailMessagesForEmailFolderIdQuery>()) } doThrow RuntimeException(
                    "Mock Runtime Exception",
                )
            }

            val input = ListEmailMessagesForEmailFolderIdInput(
                folderId = "folderId",
            )
            val deferredResult = async(Dispatchers.IO) {
                shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                    client.listEmailMessagesForEmailFolderId(input)
                }
            }
            deferredResult.start()

            delay(100L)
            deferredResult.await()

            verify(mockAppSyncClient)
                .query<
                    ListEmailMessagesForEmailFolderIdQuery.Data,
                    ListEmailMessagesForEmailFolderIdQuery,
                    ListEmailMessagesForEmailFolderIdQuery.Variables,
                    >(
                    check {
                        it.variables().input().folderId() shouldBe "folderId"
                        it.variables().input().limit() shouldBe 10
                        it.variables().input().nextToken() shouldBe null
                        it.variables().input().specifiedDateRange() shouldBe null
                        it.variables().input().sortOrder() shouldBe SortOrderEntity.DESC
                    },
                )
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should not block coroutine cancellation exception`() =
        runBlocking<Unit> {
            mockAppSyncClient.stub {
                on { query(any<ListEmailMessagesForEmailFolderIdQuery>()) } doThrow CancellationException(
                    "Mock Cancellation Exception",
                )
            }

            val input = ListEmailMessagesForEmailFolderIdInput(
                folderId = "folderId",
            )
            val deferredResult = async(Dispatchers.IO) {
                shouldThrow<CancellationException> {
                    client.listEmailMessagesForEmailFolderId(input)
                }
            }
            deferredResult.start()
            delay(100L)

            verify(mockAppSyncClient)
                .query<
                    ListEmailMessagesForEmailFolderIdQuery.Data,
                    ListEmailMessagesForEmailFolderIdQuery,
                    ListEmailMessagesForEmailFolderIdQuery.Variables,
                    >(
                    check {
                        it.variables().input().folderId() shouldBe "folderId"
                        it.variables().input().limit() shouldBe 10
                        it.variables().input().nextToken() shouldBe null
                        it.variables().input().specifiedDateRange() shouldBe null
                        it.variables().input().sortOrder() shouldBe SortOrderEntity.DESC
                    },
                )
        }
}
