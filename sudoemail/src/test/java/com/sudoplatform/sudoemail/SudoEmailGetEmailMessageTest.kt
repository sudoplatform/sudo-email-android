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
import com.sudoplatform.sudoemail.graphql.GetEmailMessageQuery
import com.sudoplatform.sudoemail.graphql.fragment.SealedEmailMessage
import com.sudoplatform.sudoemail.graphql.type.EmailMessageDirection
import com.sudoplatform.sudoemail.graphql.type.EmailMessageEncryptionStatus
import com.sudoplatform.sudoemail.graphql.type.EmailMessageState
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.Direction
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.State
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
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

/**
 * Test the correct operation of [SudoEmailClient.getEmailMessage]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetEmailMessageTest : BaseTests() {

    private val queryResult by before {
        GetEmailMessageQuery.GetEmailMessage(
            "typename",
            GetEmailMessageQuery.GetEmailMessage.Fragments(
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

    private val response by before {
        Response.builder<GetEmailMessageQuery.Data>(GetEmailMessageQuery("emailMessageId"))
            .data(GetEmailMessageQuery.Data(queryResult))
            .build()
    }

    private var holder = CallbackHolder<GetEmailMessageQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<GetEmailMessageQuery>()) } doReturn holder.queryOperation
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
            mockAppSyncClient,
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

    @Before
    fun init() {
        holder.callback = null
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
    fun `getEmailMessage() should return results when no error present`() = runTest {
        holder.callback shouldBe null

        val input = GetEmailMessageInput(id = "emailMessageId")
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.getEmailMessage(input)
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        val result = deferredResult.await()
        result shouldNotBe null

        val addresses = listOf(EmailMessage.EmailAddress("foobar@unittest.org"))
        with(result!!) {
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

        verify(mockAppSyncClient).query<GetEmailMessageQuery.Data, GetEmailMessageQuery, GetEmailMessageQuery.Variables>(
            check {
                it.variables().id() shouldBe "emailMessageId"
            },
        )
        verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
        verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
    }

    @Test
    fun `getEmailMessage() should return results when date is set`() = runTest {
        mockKeyManager.stub {
            on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                unsealedHeaderDetailsWithDateString.toByteArray()
        }

        holder.callback shouldBe null

        val input = GetEmailMessageInput(id = "emailMessageId")
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.getEmailMessage(input)
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        val result = deferredResult.await()
        result shouldNotBe null

        val addresses = listOf(EmailMessage.EmailAddress("foobar@unittest.org"))
        with(result!!) {
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

        verify(mockAppSyncClient).query<GetEmailMessageQuery.Data, GetEmailMessageQuery, GetEmailMessageQuery.Variables>(
            check {
                it.variables().id() shouldBe "emailMessageId"
            },
        )
        verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
        verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
    }

    @Test
    fun `getEmailMessage() should return results when hasAttachments is true`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    unsealedHeaderDetailsHasAttachmentsTrueString.toByteArray()
            }

            holder.callback shouldBe null

            val input = GetEmailMessageInput(id = "emailMessageId")
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailMessage(input)
            }
            deferredResult.start()

            delay(100L)
            holder.callback shouldNotBe null
            holder.callback?.onResponse(response)

            val result = deferredResult.await()
            result shouldNotBe null

            val addresses = listOf(EmailMessage.EmailAddress("foobar@unittest.org"))
            with(result!!) {
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

            verify(mockAppSyncClient).query<GetEmailMessageQuery.Data, GetEmailMessageQuery, GetEmailMessageQuery.Variables>(
                check {
                    it.variables().id() shouldBe "emailMessageId"
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `getEmailMessage() should return results when hasAttachments is unset`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    unsealedHeaderDetailsHasAttachmentsUnsetString.toByteArray()
            }

            holder.callback shouldBe null

            val input = GetEmailMessageInput(id = "emailMessageId")
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailMessage(input)
            }
            deferredResult.start()

            delay(100L)
            holder.callback shouldNotBe null
            holder.callback?.onResponse(response)

            val result = deferredResult.await()
            result shouldNotBe null

            val addresses = listOf(EmailMessage.EmailAddress("foobar@unittest.org"))
            with(result!!) {
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

            verify(mockAppSyncClient).query<GetEmailMessageQuery.Data, GetEmailMessageQuery, GetEmailMessageQuery.Variables>(
                check {
                    it.variables().id() shouldBe "emailMessageId"
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `getEmailMessage() should return null result when query result data is null`() =
        runTest {
            holder.callback shouldBe null

            val responseWithNullResult by before {
                Response.builder<GetEmailMessageQuery.Data>(GetEmailMessageQuery("emailMessageId"))
                    .data(GetEmailMessageQuery.Data(null))
                    .build()
            }

            val input = GetEmailMessageInput(id = "emailMessageId")
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailMessage(input)
            }
            deferredResult.start()

            delay(100L)
            holder.callback shouldNotBe null
            holder.callback?.onResponse(responseWithNullResult)

            val result = deferredResult.await()
            result shouldBe null

            verify(mockAppSyncClient).query<GetEmailMessageQuery.Data, GetEmailMessageQuery, GetEmailMessageQuery.Variables>(
                check {
                    it.variables().id() shouldBe "emailMessageId"
                },
            )
        }

    @Test
    fun `getEmailMessage() should return null result when query response is null`() =
        runTest {
            holder.callback shouldBe null

            val nullResponse by before {
                Response.builder<GetEmailMessageQuery.Data>(GetEmailMessageQuery("emailMessageId"))
                    .data(null)
                    .build()
            }

            val input = GetEmailMessageInput(id = "emailMessageId")
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailMessage(input)
            }
            deferredResult.start()

            delay(100L)
            holder.callback shouldNotBe null
            holder.callback?.onResponse(nullResponse)

            val result = deferredResult.await()
            result shouldBe null

            verify(mockAppSyncClient).query<GetEmailMessageQuery.Data, GetEmailMessageQuery, GetEmailMessageQuery.Variables>(
                check {
                    it.variables().id() shouldBe "emailMessageId"
                },
            )
        }

    @Test
    fun `getEmailMessage() should throw when http error occurs`() = runTest {
        holder.callback shouldBe null

        val input = GetEmailMessageInput(id = "emailMessageId")
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.getEmailMessage(input)
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

        verify(mockAppSyncClient).query<GetEmailMessageQuery.Data, GetEmailMessageQuery, GetEmailMessageQuery.Variables>(
            check {
                it.variables().id() shouldBe "emailMessageId"
            },
        )
    }

    @Test
    fun `getEmailMessage() should throw when unknown error occurs`() = runTest {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<GetEmailMessageQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val input = GetEmailMessageInput(id = "emailMessageId")
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.getEmailMessage(input)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).query<GetEmailMessageQuery.Data, GetEmailMessageQuery, GetEmailMessageQuery.Variables>(
            check {
                it.variables().id() shouldBe "emailMessageId"
            },
        )
    }

    @Test
    fun `getEmailMessage() should not suppress CancellationException`() = runTest {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<GetEmailMessageQuery>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        val input = GetEmailMessageInput(id = "emailMessageId")
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<CancellationException> {
                client.getEmailMessage(input)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEmailMessageQuery>())
    }
}
