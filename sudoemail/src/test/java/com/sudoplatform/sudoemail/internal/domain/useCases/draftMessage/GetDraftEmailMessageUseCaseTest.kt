/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.GetDraftEmailMessageResponse
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailAttachmentEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SimplifiedEmailMessageEntity
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.secure.types.LEGACY_BODY_CONTENT_ID
import com.sudoplatform.sudoemail.secure.types.LEGACY_KEY_EXCHANGE_CONTENT_ID
import com.sudoplatform.sudoemail.secure.types.SecureEmailAttachmentType
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [GetDraftEmailMessageUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GetDraftEmailMessageUseCaseTest : BaseTests() {
    private val senderEmailAddressId = mockEmailAddressId
    private val mockS3Key = DefaultS3Client.constructS3KeyForDraftEmailMessage(senderEmailAddressId, mockDraftId)
    private val mockSealedData = mockSeal(DataFactory.unsealedHeaderDetailsString).toByteArray()
    private val rfc822Data = DataFactory.unsealedHeaderDetailsString.toByteArray()

    private val mockEmailAddress by before {
        EntityDataFactory.getSealedEmailAddressEntity(
            id = senderEmailAddressId,
        )
    }

    private val mockExternalSimplifiedEmailMessage by before {
        SimplifiedEmailMessageEntity(
            from = listOf(mockSenderAddress),
            to = listOf(mockExternalRecipientAddress),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Test Subject",
            body = "Test Body",
            isHtml = false,
            attachments = emptyList(),
            inlineAttachments = emptyList(),
        )
    }

    private val mockGetDraftEmailMessageResponse by before {
        GetDraftEmailMessageResponse(
            s3Key = mockS3Key,
            rfc822Data = mockSealedData,
            keyId = mockSymmetricKeyId,
            updatedAt = Date(1L),
        )
    }

    private val mockDraftEmailMessageService by before {
        mock<DraftEmailMessageService>().stub {
            onBlocking {
                get(any())
            } doReturn mockGetDraftEmailMessageResponse
        }
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { get(any()) } doReturn mockEmailAddress
        }
    }

    private val mockEmailMessageDataProcessor by before {
        mock<EmailMessageDataProcessor>().stub {
            on { parseInternetMessageData(any()) } doReturn mockExternalSimplifiedEmailMessage
        }
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>().stub {
            onBlocking { decrypt(any()) } doReturn ByteArray(42)
        }
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on {
                unsealString(any(), any())
            } doReturn rfc822Data
        }
    }

    private val useCase by before {
        GetDraftEmailMessageUseCase(
            draftEmailMessageService = mockDraftEmailMessageService,
            sealingService = mockSealingService,
            emailMessageDataProcessor = mockEmailMessageDataProcessor,
            emailCryptoService = mockEmailCryptoService,
            logger = mockLogger,
            emailAddressService = mockEmailAddressService,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockDraftEmailMessageService,
            mockEmailAddressService,
            mockEmailMessageDataProcessor,
            mockEmailCryptoService,
            mockSealingService,
        )
    }

    @Test
    fun `execute() should retrieve and return draft message successfully`() =
        runTest {
            val input =
                GetDraftEmailMessageUseCaseInput(
                    draftId = mockDraftId,
                    emailAddressId = senderEmailAddressId,
                )

            val result = useCase.execute(input)

            result.id shouldBe mockDraftId
            result.emailAddressId shouldBe senderEmailAddressId
            result.updatedAt shouldBe Date(1L)
            result.rfc822Data shouldBe rfc822Data

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).get(
                check {
                    it.s3Key shouldBe mockS3Key
                },
            )
            verify(mockSealingService).unsealString(any(), any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(any())
        }

    @Test
    fun `execute() should handle E2EE encrypted draft with key exchange attachment`() =
        runTest {
            val keyExchangeAttachment =
                EmailAttachmentEntity(
                    fileName = "keyExchange",
                    contentId = SecureEmailAttachmentType.KEY_EXCHANGE.contentId,
                    mimeType = "application/octet-stream",
                    inlineAttachment = false,
                    data = "keyData".toByteArray(),
                )
            val bodyAttachment =
                EmailAttachmentEntity(
                    fileName = "body",
                    contentId = SecureEmailAttachmentType.BODY.contentId,
                    mimeType = "application/octet-stream",
                    inlineAttachment = false,
                    data = "bodyData".toByteArray(),
                )
            val e2eeSimplifiedMessage =
                mockExternalSimplifiedEmailMessage.copy(
                    attachments = listOf(keyExchangeAttachment, bodyAttachment),
                )

            val decryptedRfc822Data = "Decrypted RFC822 data".toByteArray()

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn e2eeSimplifiedMessage
            }
            mockEmailCryptoService.stub {
                onBlocking { decrypt(any()) } doReturn decryptedRfc822Data
            }

            val input =
                GetDraftEmailMessageUseCaseInput(
                    draftId = mockDraftId,
                    emailAddressId = senderEmailAddressId,
                )

            val result = useCase.execute(input)

            result.id shouldBe mockDraftId
            result.emailAddressId shouldBe senderEmailAddressId
            result.rfc822Data shouldBe decryptedRfc822Data

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).get(any())
            verify(mockSealingService).unsealString(any(), any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockEmailCryptoService).decrypt(
                check {
                    it.keyAttachments.size shouldBe 1
                    it.bodyAttachment shouldBe bodyAttachment
                },
            )
        }

    @Test
    fun `execute() should handle E2EE encrypted draft with legacy key exchange attachment`() =
        runTest {
            val legacyKeyExchangeAttachment =
                EmailAttachmentEntity(
                    fileName = "keyExchange",
                    contentId = LEGACY_KEY_EXCHANGE_CONTENT_ID,
                    mimeType = "application/octet-stream",
                    inlineAttachment = false,
                    data = "keyData".toByteArray(),
                )
            val legacyBodyAttachment =
                EmailAttachmentEntity(
                    fileName = "body",
                    contentId = LEGACY_BODY_CONTENT_ID,
                    mimeType = "application/octet-stream",
                    inlineAttachment = false,
                    data = "bodyData".toByteArray(),
                )
            val e2eeSimplifiedMessage =
                mockExternalSimplifiedEmailMessage.copy(
                    attachments = listOf(legacyKeyExchangeAttachment, legacyBodyAttachment),
                )

            val decryptedRfc822Data = "Decrypted RFC822 data".toByteArray()

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn e2eeSimplifiedMessage
            }
            mockEmailCryptoService.stub {
                onBlocking { decrypt(any()) } doReturn decryptedRfc822Data
            }

            val input =
                GetDraftEmailMessageUseCaseInput(
                    draftId = mockDraftId,
                    emailAddressId = senderEmailAddressId,
                )

            val result = useCase.execute(input)

            result.rfc822Data shouldBe decryptedRfc822Data

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).get(any())
            verify(mockSealingService).unsealString(any(), any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockEmailCryptoService).decrypt(any())
        }

    @Test
    fun `execute() should skip address lookup if not passed an EmailAddressService`() =
        runTest {
            val useCase =
                GetDraftEmailMessageUseCase(
                    draftEmailMessageService = mockDraftEmailMessageService,
                    sealingService = mockSealingService,
                    emailMessageDataProcessor = mockEmailMessageDataProcessor,
                    emailCryptoService = mockEmailCryptoService,
                    logger = mockLogger,
                    emailAddressService = null,
                )

            val input =
                GetDraftEmailMessageUseCaseInput(
                    draftId = mockDraftId,
                    emailAddressId = senderEmailAddressId,
                )

            val result = useCase.execute(input)

            result.rfc822Data shouldBe rfc822Data

            verify(mockDraftEmailMessageService).get(
                check {
                    it.s3Key shouldBe mockS3Key
                },
            )
            verify(mockSealingService).unsealString(any(), any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(any())
        }

    @Test
    fun `execute() should throw when email address not found`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { get(any()) } doReturn null
            }

            val input =
                GetDraftEmailMessageUseCaseInput(
                    draftId = mockDraftId,
                    emailAddressId = senderEmailAddressId,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG

            verify(mockEmailAddressService).get(any())
        }

    @Test
    fun `execute() should throw FailedException when E2EE draft has no body attachment`() =
        runTest {
            val keyExchangeAttachment =
                EmailAttachmentEntity(
                    fileName = "keyExchange",
                    contentId = SecureEmailAttachmentType.KEY_EXCHANGE.contentId,
                    mimeType = "application/octet-stream",
                    inlineAttachment = false,
                    data = "keyData".toByteArray(),
                )
            val e2eeSimplifiedMessageWithoutBody =
                mockExternalSimplifiedEmailMessage.copy(
                    attachments = listOf(keyExchangeAttachment),
                )

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn e2eeSimplifiedMessageWithoutBody
            }

            val input =
                GetDraftEmailMessageUseCaseInput(
                    draftId = mockDraftId,
                    emailAddressId = senderEmailAddressId,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.BODY_ATTACHMENT_NOT_FOUND_ERROR_MSG

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).get(any())
            verify(mockSealingService).unsealString(any(), any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should throw EmailMessageNotFoundException when draft not found`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { get(any()) } doThrow SudoEmailClient.EmailMessageException.EmailMessageNotFoundException()
            }

            val input =
                GetDraftEmailMessageUseCaseInput(
                    draftId = mockDraftId,
                    emailAddressId = senderEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).get(any())
        }

    @Test
    fun `execute() should throw when unsealing fails`() =
        runTest {
            mockSealingService.stub {
                on { unsealString(any(), any()) } doThrow
                    DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException("Unsealing failed")
            }

            val input =
                GetDraftEmailMessageUseCaseInput(
                    draftId = mockDraftId,
                    emailAddressId = senderEmailAddressId,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailCryptographicKeysException.SecureKeyArchiveException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.KEY_ARCHIVE_ERROR_MSG

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).get(any())
            verify(mockSealingService).unsealString(any(), any())
        }

    @Test
    fun `execute() should throw when parsing message data fails`() =
        runTest {
            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doThrow RuntimeException("Parsing failed")
            }

            val input =
                GetDraftEmailMessageUseCaseInput(
                    draftId = mockDraftId,
                    emailAddressId = senderEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).get(any())
            verify(mockSealingService).unsealString(any(), any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should throw when E2EE decryption fails`() =
        runTest {
            val keyExchangeAttachment =
                EmailAttachmentEntity(
                    fileName = "keyExchange",
                    contentId = SecureEmailAttachmentType.KEY_EXCHANGE.contentId,
                    mimeType = "application/octet-stream",
                    inlineAttachment = false,
                    data = "keyData".toByteArray(),
                )
            val bodyAttachment =
                EmailAttachmentEntity(
                    fileName = "body",
                    contentId = SecureEmailAttachmentType.BODY.contentId,
                    mimeType = "application/octet-stream",
                    inlineAttachment = false,
                    data = "bodyData".toByteArray(),
                )
            val e2eeSimplifiedMessage =
                mockExternalSimplifiedEmailMessage.copy(
                    attachments = listOf(keyExchangeAttachment, bodyAttachment),
                )

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn e2eeSimplifiedMessage
            }
            mockEmailCryptoService.stub {
                onBlocking { decrypt(any()) } doThrow
                    EmailCryptoService.EmailCryptoServiceException.SecureDataDecryptionException("Decryption failed")
            }

            val input =
                GetDraftEmailMessageUseCaseInput(
                    draftId = mockDraftId,
                    emailAddressId = senderEmailAddressId,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.EMAIL_CRYPTO_ERROR_MSG

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).get(any())
            verify(mockSealingService).unsealString(any(), any())
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockEmailCryptoService).decrypt(any())
        }
}
