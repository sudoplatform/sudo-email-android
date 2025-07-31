/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amplifyframework.api.graphql.GraphQLResponse
import com.apollographql.apollo3.api.Optional
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
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
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.transformers.Unsealer
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
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
import com.sudoplatform.sudoemail.graphql.type.SortOrder as SortOrderEntity

/**
 * Test the correct operation of [SudoEmailClient.listEmailMessagesForEmailAddressId] using mocks
 * and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListEmailMessagesForEmailAddressIdTest : BaseTests() {

    private val input by before {
        ListEmailMessagesForEmailAddressIdInput(
            emailAddressId = "emailAddressId",
            limit = 1,
            nextToken = null,
            dateRange = EmailMessageDateRange(
                sortDate = DateRange(Date(), Date()),
            ),
            sortOrder = SortOrder.DESC,
        )
    }

    private val queryResponse by before {
        DataFactory.listEmailMessagesForEmailAddressIdQueryResponse(
            listOf(
                DataFactory.getSealedEmailMessage(
                    sealedData = mockSeal(DataFactory.unsealedHeaderDetailsString),
                ),
            ),
        )
    }

    private val queryResponseWithNextToken by before {
        DataFactory.listEmailMessagesForEmailAddressIdQueryResponse(
            listOf(
                DataFactory.getSealedEmailMessage(
                    sealedData = mockSeal(DataFactory.unsealedHeaderDetailsString),
                ),
            ),
            "dummyNextToken",
        )
    }

    private val queryResponseWithEmptyList by before {
        DataFactory.listEmailMessagesForEmailAddressIdQueryResponse(
            emptyList(),
        )
    }

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                listEmailMessagesForEmailAddressIdQuery(
                    any(),
                )
            } doAnswer {
                queryResponse
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
            } doReturn DataFactory.unsealedHeaderDetailsString.toByteArray()
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
            mockApiClient,
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
            mockApiClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return results when no error present`() =
        runTest {
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessagesForEmailAddressId(
                    input,
                )
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
                        owners.size shouldBe 1
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

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(1)
                    input.nextToken shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return results when date is set`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    DataFactory.unsealedHeaderDetailsWithDateString.toByteArray()
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessagesForEmailAddressId(
                    input,
                )
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
                        owners.size shouldBe 1
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

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(1)
                    input.nextToken shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return results when hasAttachments is true`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    DataFactory.unsealedHeaderDetailsHasAttachmentsTrueString.toByteArray()
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessagesForEmailAddressId(
                    input,
                )
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
                        owners.size shouldBe 1
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

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(1)
                    input.nextToken shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return results when hasAttachments is unset`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    DataFactory.unsealedHeaderDetailsHasAttachmentsUnsetString.toByteArray()
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessagesForEmailAddressId(
                    input,
                )
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
                        owners.size shouldBe 1
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

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(1)
                    input.nextToken shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return results when updatedAt date range is specified`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    DataFactory.unsealedHeaderDetailsHasAttachmentsTrueString.toByteArray()
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessagesForEmailAddressId(
                    input,
                )
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
                        owners.size shouldBe 1
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

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(1)
                    input.nextToken shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return success result using default inputs when no error present`() =
        runTest {
            val input = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = "emailAddressId",
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessagesForEmailAddressId(input)
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
                        owners.size shouldBe 1
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

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(10)
                    input.nextToken shouldBe Optional.absent()
                    input.specifiedDateRange shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return success result when populating nextToken`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(
                        any(),
                    )
                } doAnswer {
                    queryResponseWithNextToken
                }
            }

            val input = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = "emailAddressId",
                nextToken = "dummyNextToken",
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessagesForEmailAddressId(input)
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
                        owners.size shouldBe 1
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

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(10)
                    input.nextToken shouldBe Optional.Present("dummyNextToken")
                    input.specifiedDateRange shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return success empty list result when query result data is empty`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(
                        any(),
                    )
                } doAnswer {
                    queryResponseWithEmptyList
                }
            }

            val input = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = "emailAddressId",
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessagesForEmailAddressId(input)
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

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(10)
                    input.nextToken shouldBe Optional.absent()
                    input.specifiedDateRange shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return success empty list result when query result data is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(
                        any(),
                    )
                } doAnswer {
                    GraphQLResponse(null, null)
                }
            }

            val input = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = "emailAddressId",
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessagesForEmailAddressId(input)
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

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(10)
                    input.nextToken shouldBe Optional.absent()
                    input.specifiedDateRange shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return partial results when unsealing fails`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithPrivateKey(anyString(), any(), any()) } doThrow KeyManagerException(
                    "KeyManagerException",
                )
            }

            val input = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = "emailAddressId",
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessagesForEmailAddressId(input)
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
                        owners.size shouldBe 1
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

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(10)
                    input.nextToken shouldBe Optional.absent()
                    input.specifiedDateRange shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should throw when unsealing fails`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(
                        any(),
                    )
                } doThrow Unsealer.UnsealerException.SealedDataTooShortException("Mock Unsealer Exception")
            }

            val input = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = "emailAddressId",
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                    client.listEmailMessagesForEmailAddressId(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(10)
                    input.nextToken shouldBe Optional.absent()
                    input.specifiedDateRange shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should throw when http error occurs`() =
        runTest {
            val testError = GraphQLResponse.Error(
                "mock",
                null,
                null,
                mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
            )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }

            val input = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = "emailAddressId",
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                    client.listEmailMessagesForEmailAddressId(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(10)
                    input.nextToken shouldBe Optional.absent()
                    input.specifiedDateRange shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should throw when unknown error occurs()`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(
                        any(),
                    )
                } doThrow RuntimeException("Mock Runtime Exception")
            }

            val input = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = "emailAddressId",
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                    client.listEmailMessagesForEmailAddressId(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(10)
                    input.nextToken shouldBe Optional.absent()
                    input.specifiedDateRange shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should not block coroutine cancellation exception`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(
                        any(),
                    )
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            val input = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = "emailAddressId",
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<CancellationException> {
                    client.listEmailMessagesForEmailAddressId(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(10)
                    input.nextToken shouldBe Optional.absent()
                    input.specifiedDateRange shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(false)
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should pass includeDeletedMessages flag properly`() =
        runTest {
            val input = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = "emailAddressId",
                limit = 10,
                nextToken = null,
                dateRange = null,
                includeDeletedMessages = true,
            )

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listEmailMessagesForEmailAddressId(
                    input,
                )
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
                        owners.size shouldBe 1
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

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                    input.limit shouldBe Optional.Present(10)
                    input.nextToken shouldBe Optional.absent()
                    input.specifiedDateRange shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.Present(SortOrderEntity.DESC)
                    input.includeDeletedMessages shouldBe Optional.Present(true)
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }
}
