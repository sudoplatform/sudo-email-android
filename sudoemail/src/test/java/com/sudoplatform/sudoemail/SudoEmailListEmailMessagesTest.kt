/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.apollographql.apollo3.api.Optional
import com.sudoplatform.sudoemail.graphql.ListEmailMessagesQuery
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.DateRange
import com.sudoplatform.sudoemail.types.Direction
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EmailMessageDateRange
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.SortOrder
import com.sudoplatform.sudoemail.types.State
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesInput
import com.sudoplatform.sudoemail.types.transformers.Unsealer
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
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
import com.sudoplatform.sudoemail.graphql.type.ListEmailMessagesInput as ListEmailMessagesRequest
import com.sudoplatform.sudoemail.graphql.type.SortOrder as SortOrderEntity

/**
 * Test the correct operation of [SudoEmailClient.listEmailMessages] using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListEmailMessagesTest : BaseTests() {

    private val input by before {
        ListEmailMessagesInput(
            limit = 1,
            nextToken = null,
            dateRange = EmailMessageDateRange(
                sortDate = DateRange(Date(), Date()),
            ),
            sortOrder = SortOrder.DESC,
        )
    }

    private val queryResponse by before {
        JSONObject(
            """
                {
                    'listEmailMessages': {
                        'items': [{
                            '__typename': 'SealedEmailMessage',
                            'id': 'id',
                            'owner': 'owner',
                            'owners': [],
                            'emailAddressId': 'emailAddressId',
                            'version': 1,
                            'createdAtEpochMs': 1.0,
                            'updatedAtEpochMs': 1.0,
                            'sortDateEpochMs': 1.0,
                            'folderId': 'folderId',
                            'previousFolderId': 'previousFolderId',
                            'direction': 'INBOUND',
                            'seen': false,
                            'repliedTo': false,
                            'forwarded': false,
                            'state': 'DELIVERED',
                            'clientRefId': 'clientRefId',
                            'rfc822Header': {
                                'algorithm': 'algorithm',
                                'keyId': 'keyId',
                                'plainTextType': 'plainText',
                                'base64EncodedSealedData': '${mockSeal(unsealedHeaderDetailsString)}'
                             },
                            'size': 1.0,
                            'encryptionStatus': 'UNENCRYPTED'
                        }],
                        'nextToken': null
                    }
                }
            """.trimIndent(),
        )
    }

    private val queryResponseWithNextToken by before {
        JSONObject(
            """
                {
                    'listEmailMessages': {
                        'items': [{
                            '__typename': 'SealedEmailMessage',
                            'id': 'id',
                            'owner': 'owner',
                            'owners': [],
                            'emailAddressId': 'emailAddressId',
                            'version': 1,
                            'createdAtEpochMs': 1.0,
                            'updatedAtEpochMs': 1.0,
                            'sortDateEpochMs': 1.0,
                            'folderId': 'folderId',
                            'previousFolderId': 'previousFolderId',
                            'direction': 'INBOUND',
                            'seen': false,
                            'repliedTo': false,
                            'forwarded': false,
                            'state': 'DELIVERED',
                            'clientRefId': 'clientRefId',
                            'rfc822Header': {
                                'algorithm': 'algorithm',
                                'keyId': 'keyId',
                                'plainTextType': 'plainText',
                                'base64EncodedSealedData': '${mockSeal(unsealedHeaderDetailsString)}'
                             },
                            'size': 1.0,
                            'encryptionStatus': 'UNENCRYPTED'
                        }],
                        'nextToken': 'dummyNextToken'
                    }
                }
            """.trimIndent(),
        )
    }

    private val queryResponseWithEmptyList by before {
        JSONObject(
            """
                {
                    'listEmailMessages': {
                        'items': []
                    }
                }
            """.trimIndent(),
        )
    }

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            on {
                query<String>(
                    argThat { this.query.equals(ListEmailMessagesQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(queryResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
            }
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
            mockContext,
            GraphQLClient(mockApiCategory),
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

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockContext,
            mockUserClient,
            mockKeyManager,
            mockApiCategory,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `listEmailMessages() should return results when no error present`() = runTest {
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listEmailMessages(input)
        }
        deferredResult.start()
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
                    date shouldBe null
                }
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                val input = it.variables["input"] as ListEmailMessagesRequest
                input.limit shouldBe Optional.Present(1)
                input.nextToken shouldBe Optional.absent()
                input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                input.includeDeletedMessages shouldBe Optional.Present(false)
            },
            any(),
            any(),
        )
        verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
        verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
    }

    @Test
    fun `listEmailMessages() should return results when date is set`() = runTest {
        mockKeyManager.stub {
            on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                unsealedHeaderDetailsWithDateString.toByteArray()
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listEmailMessages(input)
        }
        deferredResult.start()
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
                    date shouldBe Date(2L)
                }
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                val input = it.variables["input"] as ListEmailMessagesRequest
                input.limit shouldBe Optional.Present(1)
                input.nextToken shouldBe Optional.absent()
                input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                input.includeDeletedMessages shouldBe Optional.Present(false)
            },
            any(),
            any(),
        )
        verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
        verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
    }

    @Test
    fun `listEmailMessages() should return results when hasAttachments is true`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    unsealedHeaderDetailsHasAttachmentsTrueString.toByteArray()
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessages(input)
            }
            deferredResult.start()
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
                        date shouldBe Date(2L)
                    }
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                    val input = it.variables["input"] as ListEmailMessagesRequest
                    input.limit shouldBe Optional.Present(1)
                    input.nextToken shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
                any(),
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessages() should return results when hasAttachments is unset`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    unsealedHeaderDetailsHasAttachmentsUnsetString.toByteArray()
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessages(input)
            }
            deferredResult.start()
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
                        date shouldBe Date(2L)
                    }
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                    val input = it.variables["input"] as ListEmailMessagesRequest
                    input.limit shouldBe Optional.Present(1)
                    input.nextToken shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
                any(),
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessages() should return results when updatedAt date range is specified`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    unsealedHeaderDetailsHasAttachmentsTrueString.toByteArray()
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessages(input)
            }
            deferredResult.start()
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
                        date shouldBe Date(2L)
                    }
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                    val input = it.variables["input"] as ListEmailMessagesRequest
                    input.limit shouldBe Optional.Present(1)
                    input.nextToken shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
                any(),
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessages() should return success result using default inputs when no error present`() =
        runTest {
            val input = ListEmailMessagesInput()
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessages(input)
            }
            deferredResult.start()
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

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                    val queryInput = it.variables["input"] as ListEmailMessagesRequest
                    queryInput.limit shouldBe Optional.Present(10)
                    queryInput.nextToken shouldBe Optional.absent()
                    queryInput.specifiedDateRange shouldBe Optional.absent()
                    queryInput.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    queryInput.includeDeletedMessages shouldBe Optional.Present(false)
                },
                any(),
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessages() should return success result when populating nextToken`() =
        runTest {
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(ListEmailMessagesQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(queryResponseWithNextToken.toString(), null),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val input = ListEmailMessagesInput(
                nextToken = "dummyNextToken",
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessages(input)
            }
            deferredResult.start()
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

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                    val queryInput = it.variables["input"] as ListEmailMessagesRequest
                    queryInput.limit shouldBe Optional.Present(10)
                    queryInput.nextToken shouldBe Optional.Present("dummyNextToken")
                    queryInput.specifiedDateRange shouldBe Optional.absent()
                    queryInput.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    queryInput.includeDeletedMessages shouldBe Optional.Present(false)
                },
                any(),
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessages() should return success empty list result when query result data is empty`() =
        runTest {
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(ListEmailMessagesQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(queryResponseWithEmptyList.toString(), null),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val input = ListEmailMessagesInput()
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessages(input)
            }
            deferredResult.start()
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

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                    val queryInput = it.variables["input"] as ListEmailMessagesRequest
                    queryInput.limit shouldBe Optional.Present(10)
                    queryInput.nextToken shouldBe Optional.absent()
                    queryInput.specifiedDateRange shouldBe Optional.absent()
                    queryInput.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    queryInput.includeDeletedMessages shouldBe Optional.Present(false)
                },
                any(),
                any(),
            )
        }

    @Test
    fun `listEmailMessages() should return success empty list result when query result data is null`() =
        runTest {
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(ListEmailMessagesQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(null, null),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val input = ListEmailMessagesInput()
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessages(input)
            }
            deferredResult.start()
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

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                    val queryInput = it.variables["input"] as ListEmailMessagesRequest
                    queryInput.limit shouldBe Optional.Present(10)
                    queryInput.nextToken shouldBe Optional.absent()
                    queryInput.specifiedDateRange shouldBe Optional.absent()
                    queryInput.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    queryInput.includeDeletedMessages shouldBe Optional.Present(false)
                },
                any(),
                any(),
            )
        }

    @Test
    fun `listEmailMessages() should return partial results when unsealing fails`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithPrivateKey(anyString(), any(), any()) } doThrow KeyManagerException(
                    "KeyManagerException",
                )
            }

            val input = ListEmailMessagesInput()
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessages(input)
            }
            deferredResult.start()
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

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                    val queryInput = it.variables["input"] as ListEmailMessagesRequest
                    queryInput.limit shouldBe Optional.Present(10)
                    queryInput.nextToken shouldBe Optional.absent()
                    queryInput.specifiedDateRange shouldBe Optional.absent()
                    queryInput.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    queryInput.includeDeletedMessages shouldBe Optional.Present(false)
                },
                any(),
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
        }

    @Test
    fun `listEmailMessages() should throw when unsealing fails`() = runTest {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(ListEmailMessagesQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doThrow Unsealer.UnsealerException.SealedDataTooShortException("Mock Unsealer Exception")
        }

        val input = ListEmailMessagesInput()
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                client.listEmailMessages(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                val queryInput = it.variables["input"] as ListEmailMessagesRequest
                queryInput.limit shouldBe Optional.Present(10)
                queryInput.nextToken shouldBe Optional.absent()
                queryInput.specifiedDateRange shouldBe Optional.absent()
                queryInput.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                queryInput.includeDeletedMessages shouldBe Optional.Present(false)
            },
            any(),
            any(),
        )
    }

    @Test
    fun `listEmailMessages() should throw when http error occurs`() = runTest {
        val testError = GraphQLResponse.Error(
            "mock",
            null,
            null,
            mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
        )
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(ListEmailMessagesQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            }.thenAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(null, listOf(testError)),
                )
                mock<GraphQLOperation<String>>()
            }
        }

        val input = ListEmailMessagesInput()
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.listEmailMessages(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                val queryInput = it.variables["input"] as ListEmailMessagesRequest
                queryInput.limit shouldBe Optional.Present(10)
                queryInput.nextToken shouldBe Optional.absent()
                queryInput.specifiedDateRange shouldBe Optional.absent()
                queryInput.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                queryInput.includeDeletedMessages shouldBe Optional.Present(false)
            },
            any(),
            any(),
        )
    }

    @Test
    fun `listEmailMessages() should throw when unknown error occurs()`() = runTest {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(ListEmailMessagesQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doThrow RuntimeException("Mock Runtime Exception")
        }

        val input = ListEmailMessagesInput()
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.listEmailMessages(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                val queryInput = it.variables["input"] as ListEmailMessagesRequest
                queryInput.limit shouldBe Optional.Present(10)
                queryInput.nextToken shouldBe Optional.absent()
                queryInput.specifiedDateRange shouldBe Optional.absent()
                queryInput.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                queryInput.includeDeletedMessages shouldBe Optional.Present(false)
            },
            any(),
            any(),
        )
    }

    @Test
    fun `listEmailMessages() should not block coroutine cancellation exception`() =
        runTest {
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(ListEmailMessagesQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doThrow CancellationException("Mock Runtime Exception")
            }

            val input = ListEmailMessagesInput()
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<CancellationException> {
                    client.listEmailMessages(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                    val queryInput = it.variables["input"] as ListEmailMessagesRequest
                    queryInput.limit shouldBe Optional.Present(10)
                    queryInput.nextToken shouldBe Optional.absent()
                    queryInput.specifiedDateRange shouldBe Optional.absent()
                    queryInput.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    queryInput.includeDeletedMessages shouldBe Optional.Present(false)
                },
                any(),
                any(),
            )
        }

    @Test
    fun `listEmailMessages() should pass includeDeletedMessages flag properly`() = runTest {
        val input = ListEmailMessagesInput(
            limit = 1,
            nextToken = null,
            includeDeletedMessages = true,
        )
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listEmailMessages(input)
        }
        deferredResult.start()
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
                    date shouldBe null
                }
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListEmailMessagesQuery.OPERATION_DOCUMENT
                val queryInput = it.variables["input"] as ListEmailMessagesRequest
                queryInput.limit shouldBe Optional.Present(1)
                queryInput.nextToken shouldBe Optional.absent()
                queryInput.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                queryInput.includeDeletedMessages shouldBe Optional.Present(true)
            },
            any(),
            any(),
        )
        verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
        verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
    }
}
