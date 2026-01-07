/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailAttachmentEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.InternetMessageFormatHeaderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SendEmailMessageResultEntity
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.s3.S3Client
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
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

/**
 * Test the correct operation of [SendEmailMessageUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SendEmailMessageUseCaseTest : BaseTests() {
    private val mockS3Key =
        DefaultS3Client.constructS3KeyForDraftEmailMessage(
            emailAddressId = mockEmailAddressId,
            mockEmailMessageId,
        )
    private val internalDomain = "internal.example.com"
    private val mixedCaseInternalDomain = "InTeRnAl.ExAmPlE.CoM"
    private val externalDomain = "external.com"
    private val region = "us-east-1"
    private val transientBucket = "transient-bucket"
    private val configurationDataEntity by before {
        EntityDataFactory.getConfigurationDataEntity()
    }
    private val publicInfoEntities by before {
        listOf(
            EntityDataFactory.getEmailAddressPublicInfoEntity(
                emailAddress = "sender@$internalDomain",
            ),
            EntityDataFactory.getEmailAddressPublicInfoEntity(
                emailAddress = "recipient@$internalDomain",
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

    private val sendResult by before {
        SendEmailMessageResultEntity(
            id = "message-id-123",
            createdAt = Date(1000),
        )
    }

    private val mockEmailMessageService by before {
        mock<EmailMessageService>().stub {
            onBlocking { send(any()) } doReturn sendResult
            onBlocking { sendEncrypted(any()) } doReturn sendResult
        }
    }

    private val mockConfigurationDataService by before {
        mock<ConfigurationDataService>().stub {
            onBlocking {
                getConfigurationData()
            } doReturn configurationDataEntity
            onBlocking { getConfiguredEmailDomains() } doReturn listOf(internalDomain)
        }
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking {
                lookupPublicInfo(
                    any(),
                )
            } doAnswer {
                publicInfoEntities
            }
        }
    }

    private val mockEmailMessageDataProcessor by before {
        mock<EmailMessageDataProcessor>().stub {
            onBlocking {
                processMessageData(
                    any(),
                    any(),
                    any(),
                )
            } doReturn ByteArray(42)
        }
    }

    private val mockS3TransientClient by before {
        mock<S3Client>().stub {
            onBlocking { upload(any(), any(), anyOrNull()) } doReturn mockS3Key
        }
    }

    private val useCase by before {
        SendEmailMessageUseCase(
            mockEmailMessageService,
            mockConfigurationDataService,
            mockEmailAddressService,
            mockEmailMessageDataProcessor,
            mockS3TransientClient,
            region,
            transientBucket,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockEmailMessageService,
            mockConfigurationDataService,
            mockEmailAddressService,
            mockEmailMessageDataProcessor,
            mockS3TransientClient,
        )
    }

    /** Begin SendOutOfNetworkEmailMessageTests */

    @Test
    fun `execute() should send out-of-network email when all recipients are external`() =
        runTest {
            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@$internalDomain"),
                    to = listOf(EmailMessageAddressEntity("external@$externalDomain")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val input =
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId = mockEmailAddressId,
                    emailMessageHeader = header,
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                    body = "Test body",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.id shouldBe sendResult.id

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockEmailMessageDataProcessor).processMessageData(
                any(),
                any(),
                any(),
            )
            verify(mockS3TransientClient).upload(any(), any(), anyOrNull())
            verify(mockEmailMessageService).send(
                check { serviceInput ->
                    serviceInput.transientBucket shouldBe transientBucket
                    serviceInput.region shouldBe region
                    serviceInput.emailAddressId shouldBe mockEmailAddressId
                    serviceInput.s3ObjectKey shouldBe mockS3Key
                },
            )
        }

    @Test
    fun `execute() should send in-network email when all recipients are internal`() =
        runTest {
            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@$internalDomain"),
                    to = listOf(EmailMessageAddressEntity("recipient@$internalDomain")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val input =
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId = mockEmailAddressId,
                    emailMessageHeader = header,
                    body = "Test body",
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.id shouldBe sendResult.id

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockEmailAddressService).lookupPublicInfo(any())
            verify(mockEmailMessageDataProcessor).processMessageData(
                any(),
                any(),
                any(),
            )
            verify(mockS3TransientClient).upload(any(), any(), anyOrNull())
            verify(mockEmailMessageService).sendEncrypted(
                check { serviceInput ->
                    serviceInput.emailAddressId shouldBe mockEmailAddressId
                    serviceInput.s3ObjectKey shouldBe mockS3Key
                    serviceInput.region shouldBe region
                    serviceInput.transientBucket shouldBe transientBucket
                    serviceInput.emailMessageHeader shouldBe header
                    serviceInput.inlineAttachments shouldBe emptyList()
                    serviceInput.attachments shouldBe emptyList()
                    serviceInput.forwardingMessageId shouldBe null
                    serviceInput.replyingMessageId shouldBe null
                },
            )
        }

    @Test
    fun `execute() should identify mixed-cased internal recipients as internal and send in-network email`() =
        runTest {
            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@$internalDomain"),
                    to = listOf(EmailMessageAddressEntity("rEcIpIeNt@$mixedCaseInternalDomain")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val input =
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId = mockEmailAddressId,
                    emailMessageHeader = header,
                    body = "Test body",
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.id shouldBe sendResult.id

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockEmailAddressService).lookupPublicInfo(any())
            verify(mockEmailMessageDataProcessor).processMessageData(
                any(),
                any(),
                any(),
            )
            verify(mockS3TransientClient).upload(any(), any(), anyOrNull())
            verify(mockEmailMessageService).sendEncrypted(
                check { serviceInput ->
                    serviceInput.emailAddressId shouldBe mockEmailAddressId
                    serviceInput.s3ObjectKey shouldBe mockS3Key
                    serviceInput.region shouldBe region
                    serviceInput.transientBucket shouldBe transientBucket
                    serviceInput.emailMessageHeader shouldBe header
                    serviceInput.inlineAttachments shouldBe emptyList()
                    serviceInput.attachments shouldBe emptyList()
                    serviceInput.forwardingMessageId shouldBe null
                    serviceInput.replyingMessageId shouldBe null
                },
            )
        }

    @Test
    fun `execute() should throw LimitExceededException when out-of-network recipients exceed limit`() =
        runTest {
            val limitedConfig =
                EntityDataFactory.getConfigurationDataEntity(
                    emailMessageRecipientsLimit = 2,
                )
            mockConfigurationDataService.stub {
                onBlocking { getConfigurationData() } doReturn limitedConfig
            }

            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@internal.example.com"),
                    to =
                        listOf(
                            EmailMessageAddressEntity("external1@external.com"),
                            EmailMessageAddressEntity("external2@external.com"),
                            EmailMessageAddressEntity("external3@external.com"),
                        ),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val input =
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId = mockEmailAddressId,
                    emailMessageHeader = header,
                    body = "Test body",
                )

            shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
                useCase.execute(input)
            }

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
        }

    @Test
    fun `execute() should throw LimitExceededException when in-network recipients exceed limit`() =
        runTest {
            val limitedConfig =
                EntityDataFactory.getConfigurationDataEntity(
                    encryptedEmailMessageRecipientsLimit = 2,
                )
            mockConfigurationDataService.stub {
                onBlocking { getConfigurationData() } doReturn limitedConfig
            }

            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@internal.example.com"),
                    to =
                        listOf(
                            EmailMessageAddressEntity("recipient1@internal.example.com"),
                            EmailMessageAddressEntity("recipient2@internal.example.com"),
                            EmailMessageAddressEntity("recipient3@internal.example.com"),
                        ),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val input =
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId = mockEmailAddressId,
                    emailMessageHeader = header,
                    body = "Test body",
                )

            shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
                useCase.execute(input)
            }

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
        }

    @Test
    fun `execute() should handle multiple external recipients in different fields`() =
        runTest {
            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@internal.example.com"),
                    to = listOf(EmailMessageAddressEntity("to@external.com")),
                    cc = listOf(EmailMessageAddressEntity("cc@external.com")),
                    bcc = listOf(EmailMessageAddressEntity("bcc@external.com")),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val input =
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId = mockEmailAddressId,
                    emailMessageHeader = header,
                    body = "Test body",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.id shouldBe sendResult.id

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockEmailMessageDataProcessor).processMessageData(
                any(),
                any(),
                any(),
            )
            verify(mockS3TransientClient).upload(any(), any(), anyOrNull())
            verify(mockEmailMessageService).send(
                check { serviceInput ->
                    serviceInput.transientBucket shouldBe transientBucket
                    serviceInput.region shouldBe region
                    serviceInput.emailAddressId shouldBe mockEmailAddressId
                    serviceInput.s3ObjectKey shouldBe mockS3Key
                },
            )
        }

    @Test
    fun `execute() should handle replying to message`() =
        runTest {
            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@internal.example.com"),
                    to = listOf(EmailMessageAddressEntity("recipient@external.com")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Re: Original Subject",
                )

            val input =
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId = mockEmailAddressId,
                    emailMessageHeader = header,
                    body = "Reply body",
                    replyingMessageId = "original-message-id",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.id shouldBe sendResult.id

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockEmailMessageDataProcessor).processMessageData(
                any(),
                any(),
                any(),
            )
            verify(mockS3TransientClient).upload(any(), any(), anyOrNull())
            verify(mockEmailMessageService).send(
                check { serviceInput ->
                    serviceInput.transientBucket shouldBe transientBucket
                    serviceInput.region shouldBe region
                    serviceInput.emailAddressId shouldBe mockEmailAddressId
                    serviceInput.s3ObjectKey shouldBe mockS3Key
                },
            )
        }

    @Test
    fun `execute() should handle forwarding message`() =
        runTest {
            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@internal.example.com"),
                    to = listOf(EmailMessageAddressEntity("recipient@external.com")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Fwd: Original Subject",
                )

            val input =
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId = mockEmailAddressId,
                    emailMessageHeader = header,
                    body = "Forward body",
                    forwardingMessageId = "forwarded-message-id",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.id shouldBe sendResult.id

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockEmailMessageDataProcessor).processMessageData(
                any(),
                any(),
                any(),
            )
            verify(mockS3TransientClient).upload(any(), any(), anyOrNull())
            verify(mockEmailMessageService).send(
                check { serviceInput ->
                    serviceInput.transientBucket shouldBe transientBucket
                    serviceInput.region shouldBe region
                    serviceInput.emailAddressId shouldBe mockEmailAddressId
                    serviceInput.s3ObjectKey shouldBe mockS3Key
                },
            )
        }

    @Test
    fun `execute() should clean up S3 object on failure`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking { send(any()) } doThrow RuntimeException("Send failed")
            }

            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@internal.example.com"),
                    to = listOf(EmailMessageAddressEntity("recipient@external.com")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val input =
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId = mockEmailAddressId,
                    emailMessageHeader = header,
                    body = "Test body",
                )

            shouldThrow<SudoEmailClient.EmailMessageException> {
                useCase.execute(input)
            }

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockEmailMessageDataProcessor).processMessageData(
                any(),
                any(),
                any(),
            )
            verify(mockS3TransientClient).upload(any(), any(), anyOrNull())
            verify(mockEmailMessageService).send(any())
        }

    @Test
    fun `execute() should verify attachment validity`() =
        runTest {
            val configWithProhibitedExtensions =
                EntityDataFactory.getConfigurationDataEntity(
                    prohibitedFileExtensions = listOf(".exe", ".bat"),
                )
            mockConfigurationDataService.stub {
                onBlocking { getConfigurationData() } doReturn configWithProhibitedExtensions
            }

            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@internal.example.com"),
                    to = listOf(EmailMessageAddressEntity("recipient@external.com")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val badAttachment =
                EmailAttachmentEntity(
                    fileName = "virus.exe",
                    contentId = "content-1",
                    mimeType = "application/octet-stream",
                    inlineAttachment = false,
                    data = ByteArray(0),
                )

            val input =
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId = mockEmailAddressId,
                    emailMessageHeader = header,
                    body = "Test body",
                    attachments = listOf(badAttachment),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
                useCase.execute(input)
            }

            verify(mockConfigurationDataService).getConfigurationData()
        }

    @Test
    fun `execute() should handle display names without special characters`() =
        runTest {
            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@$internalDomain", "John Doe"),
                    to =
                        listOf(
                            EmailMessageAddressEntity("recipient1@$externalDomain", "Jane Smith"),
                            EmailMessageAddressEntity("recipient2@$externalDomain", "Bob Johnson"),
                        ),
                    cc = listOf(EmailMessageAddressEntity("cc@$externalDomain", "Alice Brown")),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val input =
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId = mockEmailAddressId,
                    emailMessageHeader = header,
                    body = "Test body",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.id shouldBe sendResult.id

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockEmailMessageDataProcessor).processMessageData(
                any(),
                any(),
                any(),
            )
            verify(mockS3TransientClient).upload(any(), any(), anyOrNull())
            verify(mockEmailMessageService).send(
                check { serviceInput ->
                    serviceInput.transientBucket shouldBe transientBucket
                    serviceInput.region shouldBe region
                    serviceInput.emailAddressId shouldBe mockEmailAddressId
                    serviceInput.s3ObjectKey shouldBe mockS3Key
                },
            )
        }

    @Test
    fun `execute() should handle display names with special characters`() =
        runTest {
            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@$internalDomain", "John \"The Boss\" O'Brien"),
                    to =
                        listOf(
                            EmailMessageAddressEntity("recipient1@$externalDomain", "Jane & Co."),
                            EmailMessageAddressEntity("recipient2@$externalDomain", "Bob's <Business>"),
                        ),
                    cc = listOf(EmailMessageAddressEntity("cc@$externalDomain", "Alice @ Work")),
                    bcc = listOf(EmailMessageAddressEntity("bcc@$externalDomain", "Charlie\\Brown")),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val input =
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId = mockEmailAddressId,
                    emailMessageHeader = header,
                    body = "Test body",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.id shouldBe sendResult.id

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockEmailMessageDataProcessor).processMessageData(
                any(),
                any(),
                any(),
            )
            verify(mockS3TransientClient).upload(any(), any(), anyOrNull())
            verify(mockEmailMessageService).send(
                check { serviceInput ->
                    serviceInput.transientBucket shouldBe transientBucket
                    serviceInput.region shouldBe region
                    serviceInput.emailAddressId shouldBe mockEmailAddressId
                    serviceInput.s3ObjectKey shouldBe mockS3Key
                },
            )
        }

    @Test
    fun `execute() should send out-of-network email when recipients are mix of internal and external`() =
        runTest {
            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@$internalDomain"),
                    to =
                        listOf(
                            EmailMessageAddressEntity("internal1@$internalDomain"),
                            EmailMessageAddressEntity("external1@$externalDomain"),
                        ),
                    cc = listOf(EmailMessageAddressEntity("internal2@$internalDomain")),
                    bcc = listOf(EmailMessageAddressEntity("external2@$externalDomain")),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val input =
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId = mockEmailAddressId,
                    emailMessageHeader = header,
                    body = "Test body",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.id shouldBe sendResult.id

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockEmailMessageDataProcessor).processMessageData(
                any(),
                any(),
                any(),
            )
            verify(mockS3TransientClient).upload(any(), any(), anyOrNull())
            verify(mockEmailMessageService).send(
                check { serviceInput ->
                    serviceInput.transientBucket shouldBe transientBucket
                    serviceInput.region shouldBe region
                    serviceInput.emailAddressId shouldBe mockEmailAddressId
                    serviceInput.s3ObjectKey shouldBe mockS3Key
                },
            )
        }

    @Test
    fun `execute() should send out-of-network email when mix includes display names with special characters`() =
        runTest {
            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@$internalDomain", "Sender \"Name\""),
                    to =
                        listOf(
                            EmailMessageAddressEntity("internal1@$internalDomain", "Internal & User"),
                            EmailMessageAddressEntity("external1@$externalDomain", "External <User>"),
                        ),
                    cc =
                        listOf(
                            EmailMessageAddressEntity("internal2@$internalDomain", "CC @ Internal"),
                            EmailMessageAddressEntity("external2@$externalDomain", "CC\\External"),
                        ),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val input =
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId = mockEmailAddressId,
                    emailMessageHeader = header,
                    body = "Test body",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.id shouldBe sendResult.id

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockConfigurationDataService).getConfiguredEmailDomains()
            verify(mockEmailMessageDataProcessor).processMessageData(
                any(),
                any(),
                any(),
            )
            verify(mockS3TransientClient).upload(any(), any(), anyOrNull())
            verify(mockEmailMessageService).send(
                check { serviceInput ->
                    serviceInput.transientBucket shouldBe transientBucket
                    serviceInput.region shouldBe region
                    serviceInput.emailAddressId shouldBe mockEmailAddressId
                    serviceInput.s3ObjectKey shouldBe mockS3Key
                },
            )
        }
}
