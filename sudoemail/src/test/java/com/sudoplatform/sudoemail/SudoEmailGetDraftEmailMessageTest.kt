/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.util.Base64
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.secure.types.SecureEmailAttachmentType
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudoemail.util.SimplifiedEmailMessage
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date
import java.util.UUID

/**
 * Test the correct operation of [SudoEmailClient.getDraftEmailMessage]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetDraftEmailMessageTest : BaseTests() {
    private val mockUserMetadata =
        listOf(
            "key-id" to "keyId",
            "algorithm" to "algorithm",
        ).toMap()

    private val mockUserMetadataWithLegacyKeyId =
        listOf(
            "keyId" to "keyId",
            "algorithm" to "algorithm",
        ).toMap()

    private val mockS3ObjectMetadata = ObjectMetadata()

    private val secureKeyAttachment =
        EmailAttachment(
            SecureEmailAttachmentType.KEY_EXCHANGE.fileName,
            SecureEmailAttachmentType.KEY_EXCHANGE.contentId,
            SecureEmailAttachmentType.KEY_EXCHANGE.mimeType,
            true,
            ByteArray(1),
        )

    private val secureBodyAttachment =
        EmailAttachment(
            SecureEmailAttachmentType.BODY.fileName,
            SecureEmailAttachmentType.BODY.contentId,
            SecureEmailAttachmentType.BODY.mimeType,
            true,
            ByteArray(1),
        )

    private val outNetworkEmailMessage =
        SimplifiedEmailMessage(
            listOf("from@internal.com"),
            listOf("to@external.com"),
            listOf("cc@external.com"),
            listOf("bcc@external.com"),
            "email message subject",
            "email message body",
            false,
        )

    private val inNetworkEmailMessage =
        SimplifiedEmailMessage(
            listOf("from@internal.com"),
            listOf("to@internal.com"),
            emptyList(),
            emptyList(),
            "email message subject",
            "email message body",
            false,
            listOf(secureBodyAttachment, secureKeyAttachment),
        )

    private val mockDraftId = UUID.randomUUID()
    private val input by before {
        GetDraftEmailMessageInput(mockDraftId.toString(), "emailAddressId")
    }

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
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

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                getEmailAddressQuery(
                    any(),
                )
            } doAnswer {
                DataFactory.getEmailAddressQueryResponse()
            }
        }
    }

    private val timestamp by before {
        Date()
    }

    private val mockUploadResponse by before {
        "foobar"
    }

    private val mockDownloadResponse by before {
        mockSeal("foobar").toByteArray(Charsets.UTF_8)
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking {
                upload(any(), any(), anyOrNull())
            } doReturn mockUploadResponse
            onBlocking {
                download(any())
            } doReturn mockDownloadResponse
            onBlocking {
                getObjectMetadata(any())
            } doReturn mockS3ObjectMetadata
        }
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>().stub {
            on { parseInternetMessageData(any()) } doReturn outNetworkEmailMessage
        }
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on { unsealString(any(), any()) } doReturn Base64.encode(ByteArray(256))
        }
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>().stub {
            onBlocking { decrypt(any()) } doReturn ByteArray(42)
        }
    }

    private val client by before {
        DefaultSudoEmailClient(
            context,
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

    @Before
    fun init() {
        mockS3ObjectMetadata.lastModified = timestamp
        mockS3ObjectMetadata.userMetadata = mockUserMetadata
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockUserClient,
            mockKeyManager,
            mockApiClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `getDraftEmailMessage() should log and throw an error if sender address not found`() =
        runTest {
            val error =
                GraphQLResponse.Error(
                    "mock",
                    null,
                    null,
                    mapOf("errorType" to "AddressNotFound"),
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailAddressQuery(
                        any(),
                    )
                } doAnswer {
                    GraphQLResponse(null, listOf(error))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                        client.getDraftEmailMessage(input)
                    }
                }

            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
        }

    @Test
    fun `getDraftEmailMessage() should log and throw an error if draft message is not found`() =
        runTest {
            val error = AmazonS3Exception("Not found")
            error.errorCode = "404 Not Found"
            mockS3Client.stub {
                onBlocking {
                    download(anyString())
                } doThrow error
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                        client.getDraftEmailMessage(input)
                    }
                }

            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockS3Client).getObjectMetadata(
                check {
                    it shouldContain mockDraftId.toString()
                },
            )
            verify(mockS3Client).download(
                check {
                    it shouldContain mockDraftId.toString()
                },
            )
        }

    @Test
    fun `getDraftEmailMessage() should throw error if no keyId is found in s3Object`() =
        runTest {
            val mockBadObjectUserMetadata = listOf("algorithm" to "algorithm").toMap()
            val mockBadObjectMetadata = ObjectMetadata()
            mockBadObjectMetadata.lastModified = timestamp
            mockBadObjectMetadata.userMetadata = mockBadObjectUserMetadata

            mockS3Client.stub {
                onBlocking {
                    getObjectMetadata(any())
                } doReturn mockBadObjectMetadata
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                        client.getDraftEmailMessage(input)
                    }
                }

            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockS3Client).getObjectMetadata(
                check {
                    it shouldContain mockDraftId.toString()
                },
            )
        }

    @Test
    fun `getDraftEmailMessage() should return proper data if no errors`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getDraftEmailMessage(input)
                }

            deferredResult.start()
            val result = deferredResult.await()

            result.id shouldBe mockDraftId.toString()
            result.emailAddressId shouldBe "emailAddressId"
            result.updatedAt shouldBe timestamp

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockS3Client).getObjectMetadata(
                check {
                    it shouldContain mockDraftId.toString()
                },
            )
            verify(mockS3Client).download(
                check {
                    it shouldContain mockDraftId.toString()
                },
            )
            verify(mockSealingService).unsealString(anyString(), any())

            verify(mockEmailMessageProcessor).parseInternetMessageData(any())
        }

    @Test
    fun `getDraftEmailMessage() should migrate message with legacy key id metadata key`() =
        runTest {
            val emailAddressId = "emailAddressId"
            mockS3ObjectMetadata.userMetadata = mockUserMetadataWithLegacyKeyId
            mockS3Client.stub {
                onBlocking {
                    getObjectMetadata(anyString())
                } doReturn mockS3ObjectMetadata
                onBlocking {
                    updateObjectMetadata(anyString(), any())
                } doReturn Unit
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getDraftEmailMessage(input)
                }

            deferredResult.start()
            val result = deferredResult.await()

            result.id shouldBe mockDraftId.toString()
            result.emailAddressId shouldBe emailAddressId
            result.updatedAt shouldBe timestamp

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockS3Client).getObjectMetadata(
                check {
                    it shouldContain emailAddressId
                },
            )
            verify(mockS3Client).download(
                check {
                    it shouldContain emailAddressId
                },
            )

            verify(mockEmailMessageProcessor).parseInternetMessageData(any())
            verify(mockS3Client).updateObjectMetadata(
                check {
                    it shouldContain emailAddressId
                },
                check {
                    it["key-id"] shouldBe "keyId"
                },
            )
            verify(mockSealingService).unsealString(anyString(), any())
        }

    // E2EE path
    @Test
    fun `getDraftEmailMessage() should throw FailedException if message has keyAttachments but no bodyAttachment`() =
        runTest {
            val inNetworkEmailMessage =
                SimplifiedEmailMessage(
                    listOf("from@internal.com"),
                    listOf("to@internal.com"),
                    emptyList(),
                    emptyList(),
                    "email message subject",
                    "email message body",
                    false,
                    listOf(secureKeyAttachment),
                )
            mockEmailMessageProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn inNetworkEmailMessage
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.getDraftEmailMessage(input)
                    }
                }

            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockS3Client).getObjectMetadata(
                check {
                    it shouldContain mockDraftId.toString()
                },
            )
            verify(mockS3Client).download(
                check {
                    it shouldContain mockDraftId.toString()
                },
            )
            verify(mockSealingService).unsealString(anyString(), any())
            verify(mockEmailMessageProcessor).parseInternetMessageData(any())
        }

    @Test
    fun `getDraftEmailMessage() should return proper data for E2EE message if no errors`() =
        runTest {
            mockEmailMessageProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn inNetworkEmailMessage
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getDraftEmailMessage(input)
                }

            deferredResult.start()
            val result = deferredResult.await()

            result.id shouldBe mockDraftId.toString()
            result.emailAddressId shouldBe "emailAddressId"
            result.updatedAt shouldBe timestamp

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockS3Client).getObjectMetadata(
                check {
                    it shouldContain mockDraftId.toString()
                },
            )
            verify(mockS3Client).download(
                check {
                    it shouldContain mockDraftId.toString()
                },
            )
            verify(mockSealingService).unsealString(anyString(), any())

            verify(mockEmailMessageProcessor).parseInternetMessageData(any())
            verify(mockEmailCryptoService).decrypt(any())
        }
}
