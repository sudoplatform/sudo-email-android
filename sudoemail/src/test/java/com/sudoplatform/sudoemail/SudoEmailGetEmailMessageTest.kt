/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudoemail.graphql.GetEmailMessageQuery
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
import com.sudoplatform.sudouser.amplify.GraphQLClient
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
        JSONObject(
            """
                {
                    'getEmailMessage': {
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
                    argThat { this.query.equals(GetEmailMessageQuery.OPERATION_DOCUMENT) },
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
    fun `getEmailMessage() should return results when no error present`() = runTest {
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.getEmailMessage(input)
        }
        deferredResult.start()
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

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe GetEmailMessageQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
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

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.getEmailMessage(input)
        }
        deferredResult.start()
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

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe GetEmailMessageQuery.OPERATION_DOCUMENT
            },
            any(),
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
                    unsealedHeaderDetailsHasAttachmentsTrueString.toByteArray()
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailMessage(input)
            }
            deferredResult.start()
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

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailMessageQuery.OPERATION_DOCUMENT
                },
                any(),
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
                    unsealedHeaderDetailsHasAttachmentsUnsetString.toByteArray()
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailMessage(input)
            }
            deferredResult.start()
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

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailMessageQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `getEmailMessage() should return null result when query response is null`() =
        runTest {
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(GetEmailMessageQuery.OPERATION_DOCUMENT) },
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

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailMessage(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe null

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailMessageQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
        }

    @Test
    fun `getEmailMessage() should throw when http error occurs`() = runTest {
        val testError = GraphQLResponse.Error(
            "mock",
            null,
            null,
            mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
        )
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(GetEmailMessageQuery.OPERATION_DOCUMENT) },
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
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.getEmailMessage(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe GetEmailMessageQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
    }

    @Test
    fun `getEmailMessage() should throw when unknown error occurs`() = runTest {
        val testError = GraphQLResponse.Error(
            "mock",
            null,
            null,
            mapOf("httpStatus" to "blah"),
        )
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(GetEmailMessageQuery.OPERATION_DOCUMENT) },
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

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.getEmailMessage(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe GetEmailMessageQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
    }

    @Test
    fun `getEmailMessage() should not suppress CancellationException`() = runTest {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(GetEmailMessageQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doThrow CancellationException("Mock Cancellation Exception")
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<CancellationException> {
                client.getEmailMessage(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe GetEmailMessageQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
    }
}
