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
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataEntity
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailAttachmentEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SimplifiedEmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.LookupEmailAddressesPublicInfoUseCase
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.secure.types.SecurePackage
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.apache.commons.codec.binary.Base64
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
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

/**
 * Test the correct operation of [SaveDraftEmailMessageUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SaveDraftEmailMessageUseCaseTest : BaseTests() {
    private val rfc822Data = DataFactory.unsealedHeaderDetailsString.toByteArray()
    private val mockSealedData = mockSeal(DataFactory.unsealedHeaderDetailsString).toByteArray()

    private val mockConfigurationData by before {
        ConfigurationDataEntity(
            deleteEmailMessagesLimit = 10,
            updateEmailMessagesLimit = 10,
            emailMessageMaxInboundMessageSize = 10240000,
            emailMessageMaxOutboundMessageSize = 10240000,
            emailMessageRecipientsLimit = 10,
            encryptedEmailMessageRecipientsLimit = 10,
            prohibitedFileExtensions = emptyList(),
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

    private val mockInternalSimplifiedEmailMessage by before {
        SimplifiedEmailMessageEntity(
            from = listOf(mockSenderAddress),
            to = listOf(mockInternalRecipientAddress),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Test Subject",
            body = "Test Body",
            isHtml = false,
            attachments = emptyList(),
            inlineAttachments = emptyList(),
        )
    }

    private val publicInfoEntities by before {
        listOf(
            EntityDataFactory.getEmailAddressPublicInfoEntity(
                emailAddress = mockSenderAddress,
            ),
            EntityDataFactory.getEmailAddressPublicInfoEntity(
                emailAddress = mockInternalRecipientAddress,
            ),
        )
    }

    private val emailAttachment =
        EmailAttachmentEntity(
            "fileName.jpg",
            "contentId",
            "mimeType",
            false,
            ByteArray(1),
        )

    private val mockEmailMessageDataProcessor by before {
        mock<EmailMessageDataProcessor>().stub {
            on { parseInternetMessageData(any()) } doReturn mockExternalSimplifiedEmailMessage
            on {
                encodeToInternetMessageData(
                    anyString(),
                    any(),
                    any(),
                    any(),
                    anyString(),
                    anyString(),
                    any(),
                    any(),
                    any(),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                )
            } doReturn rfc822Data
        }
    }

    private val mockDraftEmailMessageService by before {
        mock<DraftEmailMessageService>().stub {
            onBlocking { save(any()) } doReturn "s3Key"
        }
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>()
    }

    private val mockLookupEmailAddressesPublicInfoUseCase by before {
        mock<LookupEmailAddressesPublicInfoUseCase>().stub {
            onBlocking {
                execute(
                    any(),
                )
            } doAnswer {
                publicInfoEntities
            }
        }
    }

    private val mockConfigurationDataService by before {
        mock<ConfigurationDataService>().stub {
            onBlocking { getConfigurationData() } doReturn mockConfigurationData
            onBlocking { getConfiguredEmailDomains() } doReturn listOf(mockInternalDomain)
        }
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>().stub {
            onBlocking { encrypt(any<ByteArray>(), any()) } doReturn
                SecurePackage(
                    setOf(
                        emailAttachment,
                    ),
                    emailAttachment,
                )
        }
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on {
                sealString(any(), any())
            } doReturn mockSealedData
        }
    }

    private val useCase by before {
        SaveDraftEmailMessageUseCase(
            draftEmailMessageService = mockDraftEmailMessageService,
            emailAddressService = mockEmailAddressService,
            emailMessageDataProcessor = mockEmailMessageDataProcessor,
            configurationDataService = mockConfigurationDataService,
            sealingService = mockSealingService,
            logger = mockLogger,
            lookupEmailAddressesPublicInfoUseCase = mockLookupEmailAddressesPublicInfoUseCase,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockDraftEmailMessageService,
            mockEmailAddressService,
            mockLookupEmailAddressesPublicInfoUseCase,
            mockConfigurationDataService,
            mockEmailMessageDataProcessor,
            mockEmailCryptoService,
            mockSealingService,
        )
    }

    @Test
    fun `execute() should save external draft and return s3Key`() =
        runTest {
            val input =
                SaveDraftEmailMessageUseCaseInput(
                    rfc822Data = rfc822Data,
                    symmetricKeyId = mockSymmetricKeyId,
                    s3Key = "s3Key",
                )

            val result = useCase.execute(input)

            result shouldBe "s3Key"

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockSealingService).sealString(any(), any())
            verify(mockDraftEmailMessageService).save(
                check {
                    it.uploadData shouldBe Base64.encodeBase64(mockSealedData)
                    it.s3Key shouldBe "s3Key"
                    it.metadataObject[StringConstants.DRAFT_METADATA_KEY_ID_NAME] shouldBe mockSymmetricKeyId
                },
            )
        }

    @Test
    fun `execute() should save internal draft with encryption and return s3Key`() =
        runTest {
            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn mockInternalSimplifiedEmailMessage
                onBlocking {
                    processMessageData(
                        any(),
                        any(),
                        any(),
                    )
                } doReturn rfc822Data
            }

            val input =
                SaveDraftEmailMessageUseCaseInput(
                    rfc822Data = rfc822Data,
                    symmetricKeyId = mockSymmetricKeyId,
                    s3Key = "s3Key",
                )

            val result = useCase.execute(input)

            result shouldBe "s3Key"

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockLookupEmailAddressesPublicInfoUseCase).execute(any())
            verify(mockEmailMessageDataProcessor).processMessageData(any(), any(), any())
            verify(mockSealingService).sealString(any(), any())
            verify(mockDraftEmailMessageService).save(
                check {
                    it.uploadData shouldBe Base64.encodeBase64(mockSealedData)
                    it.s3Key shouldBe "s3Key"
                    it.metadataObject[StringConstants.DRAFT_METADATA_KEY_ID_NAME] shouldBe mockSymmetricKeyId
                },
            )
        }

    @Test
    fun `execute() should throw when internal recipient limit exceeded`() =
        runTest {
            val tooManyRecipients =
                (1..11).map { "recipient$it@$mockInternalDomain" }
            val mockMessageWithTooManyRecipients =
                SimplifiedEmailMessageEntity(
                    from = listOf(mockSenderAddress),
                    to = tooManyRecipients,
                    cc = emptyList(),
                    bcc = emptyList(),
                    subject = "Test Subject",
                    body = "Test Body",
                    isHtml = false,
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                )

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn mockMessageWithTooManyRecipients
            }

            val input =
                SaveDraftEmailMessageUseCaseInput(
                    rfc822Data = rfc822Data,
                    symmetricKeyId = mockSymmetricKeyId,
                    s3Key = "s3Key",
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
                    useCase.execute(input)
                }

            exception.message shouldBe "${StringConstants.RECIPIENT_LIMIT_EXCEEDED_ERROR_MSG}10"

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
        }

    @Test
    fun `execute() should handle mixed recipients with cc and bcc`() =
        runTest {
            val mockMixedMessage =
                SimplifiedEmailMessageEntity(
                    from = listOf(mockSenderAddress),
                    to = listOf(mockExternalRecipientAddress),
                    cc = listOf("cc@$mockExternalDomain"),
                    bcc = listOf("bcc@$mockExternalDomain"),
                    subject = "Test Subject",
                    body = "Test Body",
                    isHtml = false,
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                )

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn mockMixedMessage
            }

            val input =
                SaveDraftEmailMessageUseCaseInput(
                    rfc822Data = rfc822Data,
                    symmetricKeyId = mockSymmetricKeyId,
                    s3Key = "s3Key",
                )

            val result = useCase.execute(input)

            result shouldBe "s3Key"

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockSealingService).sealString(any(), any())
            verify(mockDraftEmailMessageService).save(any())
        }

    @Test
    fun `execute() should handle internal recipients with cc and bcc`() =
        runTest {
            val mockInternalMixedMessage =
                SimplifiedEmailMessageEntity(
                    from = listOf(mockSenderAddress),
                    to = listOf(mockInternalRecipientAddress),
                    cc = listOf("cc@$mockInternalDomain"),
                    bcc = listOf("bcc@$mockInternalDomain"),
                    subject = "Test Subject",
                    body = "Test Body",
                    isHtml = false,
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                )

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn mockInternalMixedMessage
                onBlocking {
                    processMessageData(
                        any(),
                        any(),
                        any(),
                    )
                } doReturn rfc822Data
            }

            mockLookupEmailAddressesPublicInfoUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doAnswer {
                    listOf(
                        EntityDataFactory.getEmailAddressPublicInfoEntity(emailAddress = mockSenderAddress),
                        EntityDataFactory.getEmailAddressPublicInfoEntity(emailAddress = mockInternalRecipientAddress),
                        EntityDataFactory.getEmailAddressPublicInfoEntity(emailAddress = "cc@$mockInternalDomain"),
                        EntityDataFactory.getEmailAddressPublicInfoEntity(emailAddress = "bcc@$mockInternalDomain"),
                    )
                }
            }

            val input =
                SaveDraftEmailMessageUseCaseInput(
                    rfc822Data = rfc822Data,
                    symmetricKeyId = mockSymmetricKeyId,
                    s3Key = "s3Key",
                )

            val result = useCase.execute(input)

            result shouldBe "s3Key"

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockLookupEmailAddressesPublicInfoUseCase).execute(any())
            verify(mockEmailMessageDataProcessor).processMessageData(any(), any(), any())
            verify(mockSealingService).sealString(any(), any())
            verify(mockDraftEmailMessageService).save(any())
        }

    @Test
    fun `execute() should handle empty recipients list`() =
        runTest {
            val mockNoRecipientsMessage =
                SimplifiedEmailMessageEntity(
                    from = listOf(mockSenderAddress),
                    to = emptyList(),
                    cc = emptyList(),
                    bcc = emptyList(),
                    subject = "Test Subject",
                    body = "Test Body",
                    isHtml = false,
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                )

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn mockNoRecipientsMessage
            }

            val input =
                SaveDraftEmailMessageUseCaseInput(
                    rfc822Data = rfc822Data,
                    symmetricKeyId = mockSymmetricKeyId,
                    s3Key = "s3Key",
                )

            val result = useCase.execute(input)

            result shouldBe "s3Key"

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockSealingService).sealString(any(), any())
            verify(mockDraftEmailMessageService).save(any())
        }

    @Test
    fun `execute() should throw when save draft fails`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { save(any()) } doThrow RuntimeException("S3 upload failed")
            }

            val input =
                SaveDraftEmailMessageUseCaseInput(
                    rfc822Data = rfc822Data,
                    symmetricKeyId = mockSymmetricKeyId,
                    s3Key = "s3Key",
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockSealingService).sealString(any(), any())
            verify(mockDraftEmailMessageService).save(any())
        }

    @Test
    fun `execute() should throw when sealing fails`() =
        runTest {
            mockSealingService.stub {
                on { sealString(any(), any()) } doThrow RuntimeException("Sealing failed")
            }

            val input =
                SaveDraftEmailMessageUseCaseInput(
                    rfc822Data = rfc822Data,
                    symmetricKeyId = mockSymmetricKeyId,
                    s3Key = "s3Key",
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockSealingService).sealString(any(), any())
        }

    @Test
    fun `execute() should throw when parsing message fails`() =
        runTest {
            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doThrow RuntimeException("Parsing failed")
            }

            val input =
                SaveDraftEmailMessageUseCaseInput(
                    rfc822Data = rfc822Data,
                    symmetricKeyId = mockSymmetricKeyId,
                    s3Key = "s3Key",
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
        }

    @Test
    fun `execute() should throw when configuration data retrieval fails`() =
        runTest {
            mockConfigurationDataService.stub {
                onBlocking { getConfigurationData() } doThrow RuntimeException("Config retrieval failed")
            }

            val input =
                SaveDraftEmailMessageUseCaseInput(
                    rfc822Data = rfc822Data,
                    symmetricKeyId = mockSymmetricKeyId,
                    s3Key = "s3Key",
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockConfigurationDataService).getConfigurationData()
        }

    @Test
    fun `execute() should throw when lookupPublicInfo fails`() =
        runTest {
            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn mockInternalSimplifiedEmailMessage
            }

            mockLookupEmailAddressesPublicInfoUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doThrow RuntimeException("Lookup failed")
            }

            val input =
                SaveDraftEmailMessageUseCaseInput(
                    rfc822Data = rfc822Data,
                    symmetricKeyId = mockSymmetricKeyId,
                    s3Key = "s3Key",
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockLookupEmailAddressesPublicInfoUseCase).execute(any())
        }

    @Test
    fun `execute() should throw when processMessageData fails`() =
        runTest {
            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn mockInternalSimplifiedEmailMessage
                onBlocking {
                    processMessageData(
                        any(),
                        any(),
                        any(),
                    )
                } doThrow RuntimeException("Processing failed")
            }

            val input =
                SaveDraftEmailMessageUseCaseInput(
                    rfc822Data = rfc822Data,
                    symmetricKeyId = mockSymmetricKeyId,
                    s3Key = "s3Key",
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockLookupEmailAddressesPublicInfoUseCase).execute(any())
            verify(mockEmailMessageDataProcessor).processMessageData(any(), any(), any())
        }

    @Test
    fun `execute() should handle attachments validation`() =
        runTest {
            val messageWithAttachments =
                SimplifiedEmailMessageEntity(
                    from = listOf(mockSenderAddress),
                    to = listOf(mockExternalRecipientAddress),
                    cc = emptyList(),
                    bcc = emptyList(),
                    subject = "Test Subject",
                    body = "Test Body",
                    isHtml = false,
                    attachments = listOf(emailAttachment),
                    inlineAttachments = listOf(emailAttachment),
                )

            mockEmailMessageDataProcessor.stub {
                on { parseInternetMessageData(any()) } doReturn messageWithAttachments
            }

            val input =
                SaveDraftEmailMessageUseCaseInput(
                    rfc822Data = rfc822Data,
                    symmetricKeyId = mockSymmetricKeyId,
                    s3Key = "s3Key",
                )

            val result = useCase.execute(input)

            result shouldBe "s3Key"

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageDataProcessor).parseInternetMessageData(rfc822Data)
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockSealingService).sealString(any(), any())
            verify(mockDraftEmailMessageService).save(any())
        }
}
