/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailAttachmentEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SimplifiedEmailMessageEntity
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [GetEmailMessageWithBodyUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GetEmailMessageWithBodyUseCaseTest : BaseTests() {
    private val rfc822Data = DataFactory.unsealedHeaderDetailsString.toByteArray()
    private val sealedRfc822Data = mockSeal("sealed RFC822 data")
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
        mock<S3Client>()
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

    private val mockRetrieveAndDecodeEmailMessageUseCase by before {
        mock<RetrieveAndDecodeEmailMessageUseCase>().stub {
            onBlocking { execute(any()) } doReturn rfc822Data
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
            retrieveAndDecodeEmailMessageUseCase = mockRetrieveAndDecodeEmailMessageUseCase,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockEmailMessageService,
            mockS3EmailClient,
            mockEmailMessageDataProcessor,
            mockEmailCryptoService,
            mockRetrieveAndDecodeEmailMessageUseCase,
        )
    }

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
            verify(mockRetrieveAndDecodeEmailMessageUseCase).execute(sealedEmailMessageEntity)
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
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
            verify(mockRetrieveAndDecodeEmailMessageUseCase).execute(sealedEmailMessageEntity)
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

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
            verify(mockRetrieveAndDecodeEmailMessageUseCase).execute(encryptedEmailMessageEntity)
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
            verify(mockRetrieveAndDecodeEmailMessageUseCase).execute(encryptedEmailMessageEntity)
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
            verify(mockRetrieveAndDecodeEmailMessageUseCase).execute(encryptedEmailMessageEntity)
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
            verify(mockRetrieveAndDecodeEmailMessageUseCase).execute(encryptedEmailMessageEntity)
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

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
    fun `execute() should throw when retrieve and decode fails`() =
        runTest {
            mockRetrieveAndDecodeEmailMessageUseCase.stub {
                onBlocking { execute(any()) } doThrow RuntimeException("Decode failed")
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
            verify(mockRetrieveAndDecodeEmailMessageUseCase).execute(sealedEmailMessageEntity)
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
            verify(mockRetrieveAndDecodeEmailMessageUseCase).execute(sealedEmailMessageEntity)
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
            verify(mockRetrieveAndDecodeEmailMessageUseCase).execute(encryptedEmailMessageEntity)
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockEmailCryptoService).decrypt(any())
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
            verify(mockRetrieveAndDecodeEmailMessageUseCase).execute(sealedEmailMessageEntity)
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
            verify(mockRetrieveAndDecodeEmailMessageUseCase).execute(sealedEmailMessageEntity)
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }
}
