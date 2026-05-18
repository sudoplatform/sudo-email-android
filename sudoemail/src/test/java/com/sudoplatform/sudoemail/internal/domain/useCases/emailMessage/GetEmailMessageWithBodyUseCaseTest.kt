/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailAttachmentEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.GetEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SimplifiedEmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.CacheGetResult
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.EmailMessageBodyCache
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.s3.S3Exception
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.types.LEGACY_BODY_CONTENT_ID
import com.sudoplatform.sudoemail.secure.types.LEGACY_KEY_EXCHANGE_CONTENT_ID
import com.sudoplatform.sudoemail.secure.types.SecureEmailAttachmentType
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Test the correct operation of [GetEmailMessageWithBodyUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GetEmailMessageWithBodyUseCaseTest : BaseTests() {
    private val rfc822Data = DataFactory.unsealedHeaderDetailsString.toByteArray()
    private val sealedRfc822Data = mockSeal("sealed RFC822 data")
    private val mockRfc822Metadata: ObjectMetadata = ObjectMetadata()
    private val messageBody = "This is the message body"
    private val htmlBody = "<html><body>This is the HTML message body</body></html>"

    private val mockAttachment =
        EmailAttachmentEntity(
            fileName = "document.pdf",
            contentId = "attachment1",
            mimeType = "application/pdf",
            inlineAttachment = false,
            data = ByteArray(100),
        )

    private val mockInlineAttachment =
        EmailAttachmentEntity(
            fileName = "image.png",
            contentId = "inline1",
            mimeType = "image/png",
            inlineAttachment = true,
            data = ByteArray(50),
        )

    private val keyExchangeAttachment =
        EmailAttachmentEntity(
            fileName = "keyExchange",
            contentId = SecureEmailAttachmentType.KEY_EXCHANGE.contentId,
            mimeType = "application/octet-stream",
            inlineAttachment = false,
            data = ByteArray(256),
        )

    private val legacyKeyExchangeAttachment =
        EmailAttachmentEntity(
            fileName = "keyExchange",
            contentId = LEGACY_KEY_EXCHANGE_CONTENT_ID,
            mimeType = "application/octet-stream",
            inlineAttachment = false,
            data = ByteArray(256),
        )

    private val bodyAttachment =
        EmailAttachmentEntity(
            fileName = "body",
            contentId = SecureEmailAttachmentType.BODY.contentId,
            mimeType = "application/octet-stream",
            inlineAttachment = false,
            data = messageBody.toByteArray(),
        )

    private val legacyBodyAttachment =
        EmailAttachmentEntity(
            fileName = "body",
            contentId = LEGACY_BODY_CONTENT_ID,
            mimeType = "application/octet-stream",
            inlineAttachment = false,
            data = messageBody.toByteArray(),
        )

    private val mockSimplifiedEmailMessage by before {
        SimplifiedEmailMessageEntity(
            from = listOf(mockSenderAddress),
            to = listOf(mockExternalRecipientAddress),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Test Subject",
            body = messageBody,
            isHtml = false,
            attachments = listOf(mockAttachment),
            inlineAttachments = listOf(mockInlineAttachment),
        )
    }

    private val mockEncryptedSimplifiedEmailMessage by before {
        SimplifiedEmailMessageEntity(
            from = listOf(mockSenderAddress),
            to = listOf(mockExternalRecipientAddress),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Test Subject",
            body = null,
            isHtml = false,
            attachments = listOf(keyExchangeAttachment, bodyAttachment, mockAttachment),
            inlineAttachments = emptyList(),
        )
    }

    private val mockDecryptedSimplifiedEmailMessage by before {
        SimplifiedEmailMessageEntity(
            from = listOf(mockSenderAddress),
            to = listOf(mockExternalRecipientAddress),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Test Subject",
            body = messageBody,
            isHtml = false,
            attachments = listOf(mockAttachment),
            inlineAttachments = emptyList(),
        )
    }

    private val sealedEmailMessageEntity by before {
        EntityDataFactory.getSealedEmailMessageEntity(
            id = mockEmailMessageId,
            emailAddressId = mockEmailAddressId,
            rfc822Header =
                SealedAttributeEntity(
                    keyId = mockKeyId,
                    algorithm = mockAlgorithm,
                    base64EncodedSealedData = sealedRfc822Data,
                    plainTextType = "string",
                ),
            encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
        )
    }

    private val encryptedEmailMessageEntity by before {
        EntityDataFactory.getSealedEmailMessageEntity(
            id = mockEmailMessageId,
            emailAddressId = mockEmailAddressId,
            rfc822Header =
                SealedAttributeEntity(
                    keyId = mockKeyId,
                    algorithm = mockAlgorithm,
                    base64EncodedSealedData = sealedRfc822Data,
                    plainTextType = "string",
                ),
            encryptionStatus = EncryptionStatusEntity.ENCRYPTED,
        )
    }

    override val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { decryptWithPrivateKey(anyString(), any(), any()) } doReturn ByteArray(42)
            on {
                decryptWithSymmetricKey(
                    any<ByteArray>(),
                    any<ByteArray>(),
                )
            } doReturn rfc822Data
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

    private val mockS3EmailClient by before {
        mock<S3Client>().stub {
            onBlocking { download(any(), any()) } doReturn sealedRfc822Data.toByteArray()
            onBlocking { getObjectMetadata(any(), any()) } doReturn
                mockRfc822Metadata.apply {
                    contentEncoding = "${StringConstants.BINARY_DATA_CONTENT_ENCODING},${StringConstants.CRYPTO_CONTENT_ENCODING}"
                }
        }
    }

    private val mockEmailMessageDataProcessor by before {
        mock<EmailMessageDataProcessor>().stub {
            on { parseInternetMessageData(any()) } doReturn mockSimplifiedEmailMessage
        }
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>().stub {
            onBlocking { decrypt(any()) } doReturn messageBody.toByteArray()
        }
    }

    private val mockEmailMessageService by before {
        mock<EmailMessageService>().stub {
            onBlocking { get(any()) } doReturn sealedEmailMessageEntity
        }
    }

    private val mockEmailMessageBodyCache by before {
        mock<EmailMessageBodyCache>().stub {
            onBlocking { get(any()) } doReturn null
            onBlocking { getCacheSizeLimit() } doReturn 300L * 1024 * 1024
        }
    }

    private val useCase by before {
        GetEmailMessageWithBodyUseCase(
            emailMessageService = mockEmailMessageService,
            s3EmailClient = mockS3EmailClient,
            serviceKeyManager = mockServiceKeyManager,
            emailMessageDataProcessor = mockEmailMessageDataProcessor,
            emailCryptoService = mockEmailCryptoService,
            logger = mockLogger,
            emailMessageBodyCache = mockEmailMessageBodyCache,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockEmailMessageService,
            mockS3EmailClient,
            mockEmailMessageDataProcessor,
            mockEmailCryptoService,
            mockKeyManager,
            mockEmailMessageBodyCache,
        )
    }

    // --- Basic retrieval tests ---

    @Test
    fun `execute() should return email message with body when message exists`() =
        runTest {
            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.id shouldBe mockEmailMessageId
            result?.body shouldBe messageBody
            result?.isHtml shouldBe false
            result?.attachments?.size shouldBe 1
            result?.attachments?.first() shouldBe mockAttachment
            result?.inlineAttachments?.size shouldBe 1
            result?.inlineAttachments?.first() shouldBe mockInlineAttachment

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe
                        "${StringConstants.BINARY_DATA_CONTENT_ENCODING},${StringConstants.CRYPTO_CONTENT_ENCODING}"
                },
            )
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should return null when emailAddressId does not match returned message`() =
        runTest {
            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = "different-email-address-id",
                )

            val result = useCase.execute(input)

            result shouldBe null

            verify(mockEmailMessageService).get(GetEmailMessageRequest(id = mockEmailMessageId))
        }

    @Test
    fun `execute() should return null when email message does not exist`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking { get(any()) } doReturn null
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = "non-existent-id",
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldBe null

            verify(mockEmailMessageService).get(any())
        }

    @Test
    fun `execute() should handle HTML body`() =
        runTest {
            val htmlMessage =
                mockSimplifiedEmailMessage.copy(
                    body = htmlBody,
                    isHtml = true,
                )

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn htmlMessage
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.body shouldBe htmlBody
            result?.isHtml shouldBe true

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(any())
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should handle message with null body`() =
        runTest {
            val messageWithNullBody =
                mockSimplifiedEmailMessage.copy(body = null)

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn messageWithNullBody
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.body shouldBe ""

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(any())
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should handle message with no attachments`() =
        runTest {
            val messageWithNoAttachments =
                mockSimplifiedEmailMessage.copy(
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                )

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn messageWithNoAttachments
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.attachments shouldBe emptyList()
            result?.inlineAttachments shouldBe emptyList()

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(any())
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    // --- Encrypted message tests ---

    @Test
    fun `execute() should handle encrypted email message`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking { get(any()) } doReturn encryptedEmailMessageEntity
            }

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(rfc822Data) } doReturn mockEncryptedSimplifiedEmailMessage
                on { parseInternetMessageData(messageBody.toByteArray()) } doReturn mockDecryptedSimplifiedEmailMessage
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.id shouldBe mockEmailMessageId
            result?.body shouldBe messageBody
            result?.attachments?.size shouldBe 1
            result?.attachments?.first() shouldBe mockAttachment

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(any())
            verify(mockS3EmailClient).download(encryptedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(encryptedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockEmailCryptoService).decrypt(any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(messageBody.toByteArray())
        }

    @Test
    fun `execute() should handle encrypted email with legacy attachments`() =
        runTest {
            val legacyEncryptedMessage =
                mockEncryptedSimplifiedEmailMessage.copy(
                    attachments = listOf(legacyKeyExchangeAttachment, legacyBodyAttachment, mockAttachment),
                )

            mockEmailMessageService.stub {
                onBlocking { get(any()) } doReturn encryptedEmailMessageEntity
            }

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(rfc822Data) } doReturn legacyEncryptedMessage
                on { parseInternetMessageData(messageBody.toByteArray()) } doReturn mockDecryptedSimplifiedEmailMessage
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.body shouldBe messageBody

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(any())
            verify(mockS3EmailClient).download(encryptedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(encryptedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockEmailCryptoService).decrypt(any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(messageBody.toByteArray())
        }

    @Test
    fun `execute() should throw FailedException when key attachments not found in encrypted message`() =
        runTest {
            val messageWithoutKeyAttachments =
                mockEncryptedSimplifiedEmailMessage.copy(
                    attachments = listOf(bodyAttachment, mockAttachment),
                )

            mockEmailMessageService.stub {
                onBlocking { get(any()) } doReturn encryptedEmailMessageEntity
            }

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn messageWithoutKeyAttachments
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.KEY_ATTACHMENTS_NOT_FOUND_ERROR_MSG

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(any())
            verify(mockS3EmailClient).download(encryptedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(encryptedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should throw FailedException when body attachment not found in encrypted message`() =
        runTest {
            val messageWithoutBodyAttachment =
                mockEncryptedSimplifiedEmailMessage.copy(
                    attachments = listOf(keyExchangeAttachment, mockAttachment),
                )

            mockEmailMessageService.stub {
                onBlocking { get(any()) } doReturn encryptedEmailMessageEntity
            }

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn messageWithoutBodyAttachment
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.BODY_ATTACHMENT_NOT_FOUND_ERROR_MSG

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(any())
            verify(mockS3EmailClient).download(encryptedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(encryptedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    // --- Error handling tests ---

    @Test
    fun `execute() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking { get(any()) } doThrow NotAuthorizedException("Not authorized")
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
                useCase.execute(input)
            }

            verify(mockEmailMessageService).get(any())
        }

    @Test
    fun `execute() should throw when S3 download fails`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { download(any(), any()) } doThrow
                    S3Exception.DownloadException(
                        StringConstants.S3_NOT_FOUND_ERROR_CODE,
                        AmazonS3Exception(StringConstants.S3_NOT_FOUND_ERROR_CODE),
                    )
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                useCase.execute(input)
            }

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
        }

    @Test
    fun `execute() should throw when getObjectMetadata fails`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { getObjectMetadata(any(), any()) } doThrow RuntimeException("Metadata retrieval failed")
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
        }

    @Test
    fun `execute() should throw when unsealing fails`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithPrivateKey(anyString(), any(), any()) } doThrow RuntimeException("Decryption failed")
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe
                        "${StringConstants.BINARY_DATA_CONTENT_ENCODING},${StringConstants.CRYPTO_CONTENT_ENCODING}"
                },
            )
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
        }

    @Test
    fun `execute() should throw when parsing fails`() =
        runTest {
            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doThrow RuntimeException("Parse failed")
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(any())
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should throw when decryption fails for encrypted message`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking { get(any()) } doReturn encryptedEmailMessageEntity
            }

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(rfc822Data) } doReturn mockEncryptedSimplifiedEmailMessage
            }

            mockEmailCryptoService.stub {
                onBlocking { decrypt(any()) } doThrow RuntimeException("Decryption failed")
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(any())
            verify(mockS3EmailClient).download(encryptedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(encryptedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockEmailCryptoService).decrypt(any())
        }

    @Test
    fun `execute() should throw UnsealingException for invalid content encoding`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = "invalid-encoding"
                    }
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                    useCase.execute(input)
                }

            exception.message shouldBe "Invalid Content-Encoding value invalid-encoding"

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe "invalid-encoding"
                },
            )
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
        }

    // --- Content encoding tests (from RetrieveAndDecodeEmailMessageUseCaseTest) ---

    @Test
    fun `execute() should retrieve and decode with explicit crypto and binary encoding`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = "${StringConstants.CRYPTO_CONTENT_ENCODING},${StringConstants.BINARY_DATA_CONTENT_ENCODING}"
                    }
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.body shouldBe messageBody

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe
                        "${StringConstants.CRYPTO_CONTENT_ENCODING},${StringConstants.BINARY_DATA_CONTENT_ENCODING}"
                },
            )
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should retrieve and decode with compression encoding`() =
        runTest {
            // Create compressed data
            val compressedData =
                ByteArrayOutputStream().use { byteStream ->
                    GZIPOutputStream(byteStream).use { gzipStream ->
                        gzipStream.write(rfc822Data)
                    }
                    Base64.encode(byteStream.toByteArray())
                }

            mockS3EmailClient.stub {
                onBlocking { download(any(), any()) } doReturn compressedData
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = StringConstants.COMPRESSION_CONTENT_ENCODING
                    }
            }

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(rfc822Data) } doReturn mockSimplifiedEmailMessage
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.body shouldBe messageBody

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe compressedData
                    input.contentEncoding shouldBe StringConstants.COMPRESSION_CONTENT_ENCODING
                },
            )
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should retrieve and decode with compression then crypto encoding`() =
        runTest {
            // Create compressed data
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos)
                .bufferedWriter(Charsets.UTF_8)
                .use { it.write(DataFactory.unsealedHeaderDetailsString) }
            val compressedBytes = bos.toByteArray()
            val encodedBytes = Base64.encode(compressedBytes)

            // Mock decompression then decryption
            mockKeyManager.stub {
                on {
                    decryptWithSymmetricKey(
                        any<ByteArray>(),
                        any<ByteArray>(),
                    )
                } doReturn encodedBytes
            }

            mockS3EmailClient.stub {
                onBlocking { download(any(), any()) } doReturn sealedRfc822Data.toByteArray()
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = "${StringConstants.COMPRESSION_CONTENT_ENCODING},${StringConstants.CRYPTO_CONTENT_ENCODING}"
                    }
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.body shouldBe messageBody

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe
                        "${StringConstants.COMPRESSION_CONTENT_ENCODING},${StringConstants.CRYPTO_CONTENT_ENCODING}"
                },
            )
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should handle binary encoding as no-op`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { download(any(), any()) } doReturn rfc822Data
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = StringConstants.BINARY_DATA_CONTENT_ENCODING
                    }
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.body shouldBe messageBody

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe rfc822Data
                    input.contentEncoding shouldBe StringConstants.BINARY_DATA_CONTENT_ENCODING
                },
            )
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should handle null content encoding with default values`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = null
                    }
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.body shouldBe messageBody

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe null
                },
            )
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should handle compression with whitespace in content encoding`() =
        runTest {
            // Create compressed data
            val compressedData =
                ByteArrayOutputStream().use { byteStream ->
                    GZIPOutputStream(byteStream).use { gzipStream ->
                        gzipStream.write(rfc822Data)
                    }
                    Base64.encode(byteStream.toByteArray())
                }

            mockS3EmailClient.stub {
                onBlocking { download(any(), any()) } doReturn compressedData
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = " ${StringConstants.COMPRESSION_CONTENT_ENCODING} "
                    }
            }

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(rfc822Data) } doReturn mockSimplifiedEmailMessage
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.body shouldBe messageBody

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe compressedData
                    input.contentEncoding shouldBe " ${StringConstants.COMPRESSION_CONTENT_ENCODING} "
                },
            )
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should handle uppercase content encoding values`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = "SUDOPLATFORM-CRYPTO,SUDOPLATFORM-BINARY-DATA"
                    }
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.body shouldBe messageBody

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe "SUDOPLATFORM-CRYPTO,SUDOPLATFORM-BINARY-DATA"
                },
            )
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    // --- Cache integration tests (from RetrieveAndDecodeEmailMessageUseCaseTest) ---

    @Test
    fun `execute() should return cached blob without calling S3 on cache hit`() =
        runTest {
            val cachedBlob = sealedRfc822Data.toByteArray()
            val contentEncoding = "${StringConstants.BINARY_DATA_CONTENT_ENCODING},${StringConstants.CRYPTO_CONTENT_ENCODING}"

            mockEmailMessageBodyCache.stub {
                onBlocking { get(any()) } doReturn
                    CacheGetResult(
                        messageId = mockEmailMessageId,
                        sudoId = "mockSudoId",
                        emailAddressId = mockEmailAddressId,
                        sealedBlob = cachedBlob,
                        contentEncoding = contentEncoding,
                    )
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.body shouldBe messageBody

            // Cache was consulted
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)

            // S3 was NOT called
            verify(mockS3EmailClient, never()).download(any(), any())
            verify(mockS3EmailClient, never()).getObjectMetadata(any(), any())

            // Decryption still happened (unsealing occurs regardless of source)
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())

            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should call S3 and populate cache on cache miss`() =
        runTest {
            // Cache returns null (miss) — this is the default stub behaviour
            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.body shouldBe messageBody

            // Cache was consulted
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)

            // S3 was called
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))

            // Cache was populated
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                },
            )

            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should fall back to S3 when cache get throws and re-populate cache`() =
        runTest {
            mockEmailMessageBodyCache.stub {
                onBlocking { get(any()) } doThrow RuntimeException("Cache corrupted")
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.body shouldBe messageBody

            // Cache get was attempted
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)

            // Fell back to S3
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))

            // Cache was re-populated
            verify(mockEmailMessageBodyCache).put(any())

            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should fall back to S3 when cache put throws without propagating error`() =
        runTest {
            mockEmailMessageBodyCache.stub {
                onBlocking { get(any()) } doReturn null
                onBlocking { put(any()) } doThrow RuntimeException("Cache write failed")
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            // Should still succeed — cache put error is swallowed
            result shouldNotBe null
            result?.body shouldBe messageBody

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockS3EmailClient).download(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(sealedEmailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockEmailMessageBodyCache).put(any())
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should extract sudoId from owners with sudoplatform sudoservice issuer`() =
        runTest {
            val sudoId = "testSudoId"
            val emailMessageWithSudoOwner =
                EntityDataFactory.getSealedEmailMessageEntity(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                    owners =
                        listOf(
                            OwnerEntity(id = "accountOwner", issuer = "sudoplatform"),
                            OwnerEntity(id = sudoId, issuer = "sudoplatform.sudoservice"),
                        ),
                    rfc822Header =
                        SealedAttributeEntity(
                            keyId = mockKeyId,
                            algorithm = mockAlgorithm,
                            base64EncodedSealedData = sealedRfc822Data,
                            plainTextType = "string",
                        ),
                    encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
                )

            mockEmailMessageService.stub {
                onBlocking { get(any()) } doReturn emailMessageWithSudoOwner
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            useCase.execute(input)

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.sudoId shouldBe sudoId
                    input.emailAddressId shouldBe mockEmailAddressId
                },
            )
            verify(mockS3EmailClient).download(any(), any())
            verify(mockS3EmailClient).getObjectMetadata(any(), any())
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should set sudoId to null when no sudoservice owner exists`() =
        runTest {
            val emailMessageWithoutSudoOwner =
                EntityDataFactory.getSealedEmailMessageEntity(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                    owners =
                        listOf(
                            OwnerEntity(id = "accountOwner", issuer = "sudoplatform"),
                        ),
                    rfc822Header =
                        SealedAttributeEntity(
                            keyId = mockKeyId,
                            algorithm = mockAlgorithm,
                            base64EncodedSealedData = sealedRfc822Data,
                            plainTextType = "string",
                        ),
                    encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
                )

            mockEmailMessageService.stub {
                onBlocking { get(any()) } doReturn emailMessageWithoutSudoOwner
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            useCase.execute(input)

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.sudoId shouldBe null
                },
            )
            verify(mockS3EmailClient).download(any(), any())
            verify(mockS3EmailClient).getObjectMetadata(any(), any())
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should preserve contentEncoding from cache hit`() =
        runTest {
            val cachedBlob = sealedRfc822Data.toByteArray()
            val contentEncoding = "${StringConstants.CRYPTO_CONTENT_ENCODING},${StringConstants.BINARY_DATA_CONTENT_ENCODING}"

            mockEmailMessageBodyCache.stub {
                onBlocking { get(any()) } doReturn
                    CacheGetResult(
                        messageId = mockEmailMessageId,
                        sudoId = null,
                        emailAddressId = mockEmailAddressId,
                        sealedBlob = cachedBlob,
                        contentEncoding = contentEncoding,
                    )
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.body shouldBe messageBody

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockS3EmailClient, never()).download(any(), any())
            verify(mockS3EmailClient, never()).getObjectMetadata(any(), any())
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should use default content encoding when cache hit has null contentEncoding`() =
        runTest {
            val cachedBlob = sealedRfc822Data.toByteArray()

            mockEmailMessageBodyCache.stub {
                onBlocking { get(any()) } doReturn
                    CacheGetResult(
                        messageId = mockEmailMessageId,
                        sudoId = null,
                        emailAddressId = mockEmailAddressId,
                        sealedBlob = cachedBlob,
                        contentEncoding = null,
                    )
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            // Default encoding is crypto + binary-data, which triggers unsealing
            result shouldNotBe null
            result?.body shouldBe messageBody

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockS3EmailClient, never()).download(any(), any())
            verify(mockS3EmailClient, never()).getObjectMetadata(any(), any())
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should construct correct S3 key from email message entity`() =
        runTest {
            val customEmailAddressId = "customEmailAddressId"
            val customEmailMessageId = "customEmailMessageId"
            val customKeyId = "customKeyId"

            val customEmailMessage =
                EntityDataFactory.getSealedEmailMessageEntity(
                    id = customEmailMessageId,
                    emailAddressId = customEmailAddressId,
                    rfc822Header =
                        SealedAttributeEntity(
                            keyId = customKeyId,
                            algorithm = mockAlgorithm,
                            base64EncodedSealedData = sealedRfc822Data,
                            plainTextType = "string",
                        ),
                    encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
                )

            val expectedS3Key = customEmailMessage.rfc822DataAttributes.key

            mockEmailMessageService.stub {
                onBlocking { get(any()) } doReturn customEmailMessage
            }

            val input =
                GetEmailMessageWithBodyUseCaseInput(
                    id = customEmailMessageId,
                    emailAddressId = customEmailAddressId,
                )

            useCase.execute(input)

            verify(mockEmailMessageBodyCache).get(customEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe customEmailMessageId
                    input.emailAddressId shouldBe customEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe
                        "${StringConstants.BINARY_DATA_CONTENT_ENCODING},${StringConstants.CRYPTO_CONTENT_ENCODING}"
                },
            )
            verify(mockS3EmailClient).download(expectedS3Key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(expectedS3Key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockEmailMessageService).get(any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }
}
