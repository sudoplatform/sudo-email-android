/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
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

/**
 * Test the correct operation of [SudoEmailClient.getEmailMessage]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetEmailMessageTest : BaseTests() {
    private val input by before {
        GetEmailMessageInput(id = "emailMessageId")
    }

    private val queryResponse by before {
        DataFactory.getEmailMessageQueryResponse(
            mockSeal(DataFactory.unsealedHeaderDetailsString),
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
                getEmailMessageQuery(
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
    fun `getEmailMessage() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null

            val addresses = listOf(EmailMessage.EmailAddress("foobar@unittest.org"))
            with(result!!) {
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

            verify(mockApiClient).getEmailMessageQuery(
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `getEmailMessage() should return results when date is set`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    DataFactory.unsealedHeaderDetailsWithDateString.toByteArray()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()
            result shouldNotBe null

            val addresses = listOf(EmailMessage.EmailAddress("foobar@unittest.org"))
            with(result!!) {
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

            verify(mockApiClient).getEmailMessageQuery(
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `getEmailMessage() should return results when hasAttachments is true`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    DataFactory.unsealedHeaderDetailsHasAttachmentsTrueString.toByteArray()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null

            val addresses = listOf(EmailMessage.EmailAddress("foobar@unittest.org"))
            with(result!!) {
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

            verify(mockApiClient).getEmailMessageQuery(
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `getEmailMessage() should return results when hasAttachments is unset`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn
                    DataFactory.unsealedHeaderDetailsHasAttachmentsUnsetString.toByteArray()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null

            val addresses = listOf(EmailMessage.EmailAddress("foobar@unittest.org"))
            with(result!!) {
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

            verify(mockApiClient).getEmailMessageQuery(
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `getEmailMessage() should return null result when query response is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getEmailMessageQuery(
                        any(),
                    )
                } doAnswer {
                    GraphQLResponse(null, null)
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe null

            verify(mockApiClient).getEmailMessageQuery(
                any(),
            )
        }

    @Test
    fun `getEmailMessage() should throw when http error occurs`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "mock",
                    null,
                    null,
                    mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailMessageQuery(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.getEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailMessageQuery(
                any(),
            )
        }

    @Test
    fun `getEmailMessage() should throw when unknown error occurs`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "mock",
                    null,
                    null,
                    mapOf("httpStatus" to "blah"),
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailMessageQuery(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                        client.getEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailMessageQuery(
                any(),
            )
        }

    @Test
    fun `getEmailMessage() should not suppress CancellationException`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getEmailMessageQuery(
                        any(),
                    )
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<CancellationException> {
                        client.getEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailMessageQuery(
                any(),
            )
        }
}
