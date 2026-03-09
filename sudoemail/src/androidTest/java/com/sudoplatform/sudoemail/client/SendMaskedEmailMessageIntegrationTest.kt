/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.ExternalTestAccount
import com.sudoplatform.sudoemail.ExternalTestAccountType
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.TestData
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EmailMask
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EncryptionStatus
import com.sudoplatform.sudoemail.types.InternetMessageFormatHeader
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.DeprovisionEmailMaskInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageWithBodyInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.SendMaskedEmailMessageInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.UUID
import kotlin.time.Duration

/**
 * Test the operation of [SudoEmailClient.sendMaskedEmailMessage].
 */
@RunWith(AndroidJUnit4::class)
class SendMaskedEmailMessageIntegrationTest : BaseIntegrationTest() {
    private val emailMaskList = mutableListOf<EmailMask>()
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()
    private var runTests = true

    // Shared test resources
    private lateinit var testSudo: Sudo
    private lateinit var ownershipProof: String
    private lateinit var testEmailAddress: EmailAddress
    private lateinit var maskDomains: List<String>
    private lateinit var externalTestAccount: ExternalTestAccount

    @Before
    fun setup() =
        runTest {
            sudoClient.reset()
            sudoClient.generateEncryptionKey()

            // Check if email masks are enabled, skip all tests if not
            runTests = emailClient.getConfigurationData().emailMasksEnabled
            Assume.assumeTrue("Test suite skipped due to masks not being enabled.", runTests)

            // Create shared test resources
            testSudo = createSudo(TestData.sudo)
            testSudo.id shouldNotBe null
            sudoList.add(testSudo)

            ownershipProof = getOwnershipProof(testSudo)
            ownershipProof shouldNotBe null

            maskDomains = getMaskDomains(emailClient)
            maskDomains.size shouldBeGreaterThanOrEqual 1

            // Create email address for testing
            testEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddressList.add(testEmailAddress)

            externalTestAccount = ExternalTestAccount(context, logger, ExternalTestAccountType.GMAIL)
        }

    @After
    fun teardown() =
        runTest {
            if (emailMaskList.isNotEmpty()) {
                emailMaskList.map {
                    emailClient.deprovisionEmailMask(
                        DeprovisionEmailMaskInput(it.id),
                    )
                }
            }
            if (emailAddressList.isNotEmpty()) {
                emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
            }
            if (sudoList.isNotEmpty()) {
                sudoList.map { sudoClient.deleteSudo(it) }
            }
            sudoClient.reset()
            if (runTests) {
                externalTestAccount.closeConnection()
            }
        }

    // Error cases
    @Test
    fun sendMaskedEmailMessageThrowsUnauthorizedAddressErrorIfUnknownMaskIdUsed() =
        runTest {
            val unknownMaskId = "00000000-0000-0000-0000-000000000000"
            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress("unknown@${maskDomains.first()}"),
                    to = listOf(EmailMessage.EmailAddress(externalTestAccount.getEmailAddress())),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject for unknown mask id",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = unknownMaskId,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body for unknown mask id",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.UnauthorizedAddressException> {
                emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)
            }
        }

    @Test
    fun sendMaskedEmailMessageThrowsUnauthorizedAddressErrorIfMaskAddressDoesNotMatchId() =
        runTest {
            val maskLocalPart1 = generateSafeLocalPart("android-mask1")
            val maskAddress1 = "$maskLocalPart1@${maskDomains.first()}"

            val provisionedMask1 = provisionEmailMask(maskAddress1, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask1)

            val maskLocalPart2 = generateSafeLocalPart("android-mask2")
            val maskAddress2 = "$maskLocalPart2@${maskDomains.first()}"

            val provisionedMask2 = provisionEmailMask(maskAddress2, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask2)

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask1.maskAddress),
                    to = listOf(EmailMessage.EmailAddress(externalTestAccount.getEmailAddress())),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject for mismatched mask address",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask2.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body for mismatched mask address",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.UnauthorizedAddressException> {
                emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)
            }
        }

    @Test
    fun sendMaskedEmailMessageThrowsInvalidMessageContentErrorIfGivenNoFromAddress() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(""),
                    to = listOf(EmailMessage.EmailAddress(externalTestAccount.getEmailAddress())),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject for no from address",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body for no from address",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
                emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)
            }
        }

    @Test
    fun sendMaskedEmailMessageThrowsEmailMessageSizeLimitExceededErrorIfMessageExceedsSizeLimit() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            val configurationData = emailClient.getConfigurationData()
            val maxOutboundSize = configurationData.emailMessageMaxOutboundMessageSize

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                    to = listOf(EmailMessage.EmailAddress(externalTestAccount.getEmailAddress())),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject for message size limit",
                )

            // Create a body that exceeds the maximum outbound size
            val largeBody = "A".repeat((maxOutboundSize + 1))

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = largeBody,
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageSizeLimitExceededException> {
                emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)
            }
        }

    @Test
    fun sendMaskedEmailMessageThrowsInvalidMessageContentErrorIfMessageHasNoRecipients() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                    to = emptyList(),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject for no recipients",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body for no recipients",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
                emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)
            }
        }

    @Test
    fun sendMaskedEmailMessageThrowsInvalidMessageContentErrorIfMessageContainsVariousProhibitedAttachmentTypes() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            val configurationData = emailClient.getConfigurationData()
            val prohibitedExtensionTypes = configurationData.prohibitedFileExtensions

            // Loop through three random extension types to make sure they all throw
            for (i in 1..3) {
                val randomExtension = prohibitedExtensionTypes.randomOrNull() ?: continue

                val prohibitedAttachment =
                    EmailAttachment(
                        fileName = "malicious.$randomExtension",
                        contentId = UUID.randomUUID().toString(),
                        mimeType = "application/octet-stream",
                        inlineAttachment = false,
                        data = "This is a malicious file with a prohibited extension".toByteArray(),
                    )

                val emailMessageHeader =
                    InternetMessageFormatHeader(
                        from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                        to = listOf(EmailMessage.EmailAddress(externalTestAccount.getEmailAddress())),
                        cc = emptyList(),
                        bcc = emptyList(),
                        replyTo = emptyList(),
                        subject = "Test Subject for prohibited attachment type $randomExtension",
                    )

                val sendMaskedEmailMessageInput =
                    SendMaskedEmailMessageInput(
                        senderEmailMaskId = provisionedMask.id,
                        emailMessageHeader = emailMessageHeader,
                        body = "Test Body for prohibited attachment type $randomExtension",
                        attachments = listOf(prohibitedAttachment),
                        inlineAttachment = emptyList(),
                    )

                shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
                    emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)
                }
            }
        }

    // Out-network cases
    @Test
    fun sendMaskedEmailMessageSuccessfullySendsToSingleExternalEmailAddress() =
        runTest(timeout = Duration.parse("2m")) {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)
            val timestamp = Date()

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                    to = listOf(EmailMessage.EmailAddress(externalTestAccount.getEmailAddress())),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent via a mask at $timestamp",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent via a mask at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            val sentMessage =
                emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
                    ?: throw AssertionError("should not be null")

            with(sentMessage) {
                from.firstOrNull()?.emailAddress shouldBe provisionedMask.maskAddress
                to.firstOrNull()?.emailAddress shouldBe externalTestAccount.getEmailAddress()
                encryptionStatus shouldBe EncryptionStatus.UNENCRYPTED
                emailMaskId shouldBe provisionedMask.id
            }

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            val receivedEmail =
                externalTestAccount.waitForEmailFromSender(
                    sender = maskAddress,
                    subject = emailMessageHeader.subject,
                    ExternalTestAccount.WaitOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                )

            receivedEmail shouldNotBe null
            receivedEmail.subject shouldBe emailMessageHeader.subject
            receivedEmail.from[0].emailAddress shouldBe maskAddress
        }

    @Test
    fun sendMaskedEmailMessageSuccessfullySendsToMultipleExternalEmailAddresses() =
        runTest(timeout = Duration.parse("2m")) {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)
            val timestamp = Date()

            val otherExternalTestAccount =
                if (externalTestAccount.getAccountType() == ExternalTestAccountType.GMAIL) {
                    ExternalTestAccount(context, logger, ExternalTestAccountType.YAHOO)
                } else {
                    ExternalTestAccount(context, logger, ExternalTestAccountType.GMAIL)
                }

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                    to =
                        listOf(
                            EmailMessage.EmailAddress(externalTestAccount.getEmailAddress()),
                            EmailMessage.EmailAddress(otherExternalTestAccount.getEmailAddress()),
                        ),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent to multiple recipients via a mask at $timestamp",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent to multiple recipients via a mask at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            listOf(externalTestAccount, otherExternalTestAccount).forEach { recipient ->
                val receivedEmail =
                    recipient.waitForEmailFromSender(
                        sender = maskAddress,
                        subject = emailMessageHeader.subject,
                        ExternalTestAccount.WaitOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                    )

                receivedEmail shouldNotBe null
                receivedEmail.subject shouldBe emailMessageHeader.subject
                receivedEmail.from[0].emailAddress shouldBe maskAddress
            }
            otherExternalTestAccount.closeConnection()
        }

    @Test
    fun sendMaskedEmailMessageSuccessfullySendsToCcRecipient() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)
            val timestamp = Date()

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                    to = emptyList(),
                    cc = listOf(EmailMessage.EmailAddress(externalTestAccount.getEmailAddress())),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent with cc recipient via a mask at $timestamp",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent with cc recipient via a mask at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            val receivedEmail =
                externalTestAccount.waitForEmailFromSender(
                    sender = maskAddress,
                    subject = emailMessageHeader.subject,
                    ExternalTestAccount.WaitOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                )

            receivedEmail shouldNotBe null
            receivedEmail.subject shouldBe emailMessageHeader.subject
            receivedEmail.from[0].emailAddress shouldBe maskAddress
            receivedEmail.cc[0].emailAddress shouldBe externalTestAccount.getEmailAddress()
        }

    @Test
    fun sendMaskedEmailMessageSuccessfullySendsToBothToAndCcRecipients() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val otherExternalTestAccount =
                if (externalTestAccount.getAccountType() == ExternalTestAccountType.GMAIL) {
                    ExternalTestAccount(context, logger, ExternalTestAccountType.YAHOO)
                } else {
                    ExternalTestAccount(context, logger, ExternalTestAccountType.GMAIL)
                }

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)
            val timestamp = Date()

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                    to = listOf(EmailMessage.EmailAddress(externalTestAccount.getEmailAddress())),
                    cc = listOf(EmailMessage.EmailAddress(otherExternalTestAccount.getEmailAddress())),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent with both to and cc recipient via a mask at $timestamp",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent with both to and cc recipient via a mask at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            listOf(externalTestAccount, otherExternalTestAccount).forEach { recipient ->
                val receivedEmail =
                    recipient.waitForEmailFromSender(
                        sender = maskAddress,
                        subject = emailMessageHeader.subject,
                        ExternalTestAccount.WaitOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                    )

                receivedEmail shouldNotBe null
                receivedEmail.subject shouldBe emailMessageHeader.subject
                receivedEmail.from[0].emailAddress shouldBe maskAddress
            }
            otherExternalTestAccount.closeConnection()
        }

    @Test
    fun sendMaskedEmailMessageSuccessfullySendsToInternalAndExternalRecipients() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val internalRecipient =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internalRecipient",
                )
            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)
            val timestamp = Date()

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                    to =
                        listOf(
                            EmailMessage.EmailAddress(internalRecipient.emailAddress),
                            EmailMessage.EmailAddress(externalTestAccount.getEmailAddress()),
                        ),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent to both internal and external recipients via a mask at $timestamp",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent to both internal and external recipients via a mask at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            // Verify internal recipient received the message
            when (
                val internalRecipientMessages =
                    waitForMessagesByAddress(
                        1,
                        ListEmailMessagesForEmailAddressIdInput(
                            emailAddressId = internalRecipient.id,
                        ),
                    )
            ) {
                is ListAPIResult.Success -> {
                    val message = internalRecipientMessages.result.items.first()
                    message.subject shouldBe emailMessageHeader.subject
                    message.from.firstOrNull()?.emailAddress shouldBe provisionedMask.maskAddress
                    message.encryptionStatus shouldBe EncryptionStatus.UNENCRYPTED
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            // Verify external recipient received the message
            val receivedEmail =
                externalTestAccount.waitForEmailFromSender(
                    sender = maskAddress,
                    subject = emailMessageHeader.subject,
                    ExternalTestAccount.WaitOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                )

            receivedEmail shouldNotBe null
            receivedEmail.subject shouldBe emailMessageHeader.subject
            receivedEmail.from[0].emailAddress shouldBe maskAddress
        }

    @Test
    fun sendMaskedEmailMessageSuccessfullySendsMessageWithSpecialCharactersInSubjectAndBody() =
        runTest(timeout = Duration.parse("2m")) {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)
            val timestamp = Date()

            val specialCharactersSubject = "Test Subject with special characters 😎 ¡ ™ £ ¢ ∞ § ¶ • ª. at $timestamp"
            val specialCharactersBody = "Test Body with special characters 💐, 😱 ¡ ™ £ ¢ ∞ § ¶ • ª. at $timestamp"

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                    to = listOf(EmailMessage.EmailAddress(externalTestAccount.getEmailAddress())),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = specialCharactersSubject,
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = specialCharactersBody,
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            val receivedEmail =
                externalTestAccount.waitForEmailFromSender(
                    sender = maskAddress,
                    subject = specialCharactersSubject,
                    ExternalTestAccount.WaitOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                )

            receivedEmail shouldNotBe null
            receivedEmail.subject shouldBe specialCharactersSubject
            receivedEmail.from[0].emailAddress shouldBe maskAddress
        }

    @Test fun sendMaskedEmailMessageSuccessfullySendsMessageWithAttachments() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)
            val timestamp = Date()

            val attachment =
                EmailAttachment(
                    fileName = "testAttachment.txt",
                    contentId = UUID.randomUUID().toString(),
                    mimeType = "text/plain",
                    inlineAttachment = false,
                    data = "This is the content of the attachment".toByteArray(),
                )

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                    to = listOf(EmailMessage.EmailAddress(externalTestAccount.getEmailAddress())),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject with attachment at $timestamp",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body with attachment at $timestamp",
                    attachments = listOf(attachment),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            val receivedEmail =
                externalTestAccount.waitForEmailFromSender(
                    sender = maskAddress,
                    subject = emailMessageHeader.subject,
                    ExternalTestAccount.WaitOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                )

            receivedEmail shouldNotBe null
            receivedEmail.subject shouldBe emailMessageHeader.subject
            receivedEmail.from[0].emailAddress shouldBe maskAddress
            receivedEmail.attachments.size shouldBe 1
            receivedEmail.attachments[0].fileName shouldBe attachment.fileName
            receivedEmail.attachments[0].mimeType?.lowercase() shouldBe attachment.mimeType
        }

    @Test
    fun sendMaskedEmailMessageSuccessfullySendsMessageWithSenderAndReceiverDisplayNames() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)
            val timestamp = Date()
            val senderDisplayName = "Sender Display Name"
            val receiverDisplayName = "Receiver Display Name"

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress, senderDisplayName),
                    to = listOf(EmailMessage.EmailAddress(externalTestAccount.getEmailAddress(), receiverDisplayName)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject with display names at $timestamp",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body with display names at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            val receivedEmail =
                externalTestAccount.waitForEmailFromSender(
                    sender = maskAddress,
                    subject = emailMessageHeader.subject,
                    ExternalTestAccount.WaitOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                )

            receivedEmail shouldNotBe null
            receivedEmail.subject shouldBe emailMessageHeader.subject
            receivedEmail.from[0].emailAddress shouldBe maskAddress
            receivedEmail.from[0].displayName shouldBe senderDisplayName
            receivedEmail.to[0].emailAddress shouldBe externalTestAccount.getEmailAddress()
            receivedEmail.to[0].displayName shouldBe receiverDisplayName
        }

    // In-network cases
    @Test
    fun sendMaskedEmailMessageSuccessfullySendsToSingleInternalEmailAddress() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)
            val internalRecipient =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internalRecipient",
                )
            val timestamp = Date()

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                    to = listOf(EmailMessage.EmailAddress(internalRecipient.emailAddress)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent to internal recipient via a mask at $timestamp",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent to internal recipient via a mask at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            val sentMessage =
                emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
                    ?: throw AssertionError("should not be null")

            with(sentMessage) {
                from.firstOrNull()?.emailAddress shouldBe provisionedMask.maskAddress
                to.firstOrNull()?.emailAddress shouldBe internalRecipient.emailAddress
                encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
            }

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            // Verify message was received
            when (
                val messagesForRecipient =
                    waitForMessagesByAddress(
                        1,
                        ListEmailMessagesForEmailAddressIdInput(
                            emailAddressId = internalRecipient.id,
                        ),
                    )
            ) {
                is ListAPIResult.Success -> {
                    val message = messagesForRecipient.result.items.first()
                    message.subject shouldBe emailMessageHeader.subject
                    message.from.firstOrNull()?.emailAddress shouldBe provisionedMask.maskAddress
                    message.encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
                    message.emailMaskId shouldBe null
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun sendMaskedEmailMessageSuccessfullySendsToMultipleInternalEmailAddresses() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            val internalRecipient1 =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internalRecipient1",
                )

            val internalRecipient2 =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internalRecipient2",
                )
            val timestamp = Date()

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                    to =
                        listOf(
                            EmailMessage.EmailAddress(internalRecipient1.emailAddress),
                            EmailMessage.EmailAddress(internalRecipient2.emailAddress),
                        ),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent to multiple internal recipients via a mask at $timestamp",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent to multiple internal recipients via a mask at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            listOf(internalRecipient1, internalRecipient2).forEach { recipient ->
                // Verify message was received
                when (
                    val messagesForRecipient =
                        waitForMessagesByAddress(
                            1,
                            ListEmailMessagesForEmailAddressIdInput(
                                emailAddressId = recipient.id,
                            ),
                        )
                ) {
                    is ListAPIResult.Success -> {
                        val message = messagesForRecipient.result.items.first()
                        message.subject shouldBe emailMessageHeader.subject
                        message.from.firstOrNull()?.emailAddress shouldBe provisionedMask.maskAddress
                        message.encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
                        message.emailMaskId shouldBe null
                    }

                    else -> {
                        fail("Unexpected ListAPIResult")
                    }
                }
            }
        }

    @Test
    fun sendMaskedEmailMessageSuccessfullySendsToInternalCcRecipient() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            val internalRecipient =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internalRecipient",
                )
            val timestamp = Date()

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                    to = emptyList(),
                    cc = listOf(EmailMessage.EmailAddress(internalRecipient.emailAddress)),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent with internal cc recipient via a mask at $timestamp",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent with internal cc recipient via a mask at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            // Verify message was received
            when (
                val messagesForRecipient =
                    waitForMessagesByAddress(
                        1,
                        ListEmailMessagesForEmailAddressIdInput(
                            emailAddressId = internalRecipient.id,
                        ),
                    )
            ) {
                is ListAPIResult.Success -> {
                    val message = messagesForRecipient.result.items.first()
                    message.subject shouldBe emailMessageHeader.subject
                    message.from.firstOrNull()?.emailAddress shouldBe provisionedMask.maskAddress
                    message.cc.firstOrNull()?.emailAddress shouldBe internalRecipient.emailAddress
                    message.encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
                    message.emailMaskId shouldBe null
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun sendMaskedEmailMessageSuccessfullySendsToAnotherMask() =
        runTest {
            val maskLocalPart1 = generateSafeLocalPart("android-mask1")
            val maskAddress1 = "$maskLocalPart1@${maskDomains.first()}"

            val provisionedMask1 = provisionEmailMask(maskAddress1, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask1)

            val otherProvisionedAddress =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                )
            val maskLocalPart2 = generateSafeLocalPart("android-mask2")
            val maskAddress2 = "$maskLocalPart2@${maskDomains.first()}"

            val provisionedMask2 = provisionEmailMask(maskAddress2, otherProvisionedAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask2)
            val timestamp = Date()

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask1.maskAddress),
                    to = listOf(EmailMessage.EmailAddress(provisionedMask2.maskAddress)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent from one mask to another mask at $timestamp",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask1.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent from one mask to another mask at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            // Verify the second mask received the message
            when (
                val messagesForMask2 =
                    waitForMessagesByAddress(
                        1,
                        ListEmailMessagesForEmailAddressIdInput(
                            emailAddressId = otherProvisionedAddress.id,
                        ),
                    )
            ) {
                is ListAPIResult.Success -> {
                    val message = messagesForMask2.result.items.first()
                    message.subject shouldBe emailMessageHeader.subject
                    message.from.firstOrNull()?.emailAddress shouldBe provisionedMask1.maskAddress
                    message.encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
                    message.emailMaskId shouldBe provisionedMask2.id
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun sendMaskedEmailMessageSuccessfullySendToInternalRecipientWithSpecialCharactersInSubjectAndBody() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            val internalRecipient =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internalRecipient",
                )
            val timestamp = Date()

            val specialCharactersSubject = "Test Subject with special characters 😎 ¡ ™ £ ¢ ∞ § ¶ • ª. at $timestamp"
            val specialCharactersBody = "Test Body with special characters 💐, 😱 ¡ ™ £ ¢ ∞ § ¶ • ª. at $timestamp"

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                    to = listOf(EmailMessage.EmailAddress(internalRecipient.emailAddress)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = specialCharactersSubject,
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = specialCharactersBody,
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            // Verify message was received
            var receivedMessageId = ""
            when (
                val messagesForRecipient =
                    waitForMessagesByAddress(
                        1,
                        ListEmailMessagesForEmailAddressIdInput(
                            emailAddressId = internalRecipient.id,
                        ),
                    )
            ) {
                is ListAPIResult.Success -> {
                    val message = messagesForRecipient.result.items.first()
                    message.subject shouldBe emailMessageHeader.subject
                    message.from.firstOrNull()?.emailAddress shouldBe provisionedMask.maskAddress
                    message.encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
                    receivedMessageId = message.id
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val receivedMessage =
                emailClient.getEmailMessageWithBody(GetEmailMessageWithBodyInput(receivedMessageId, internalRecipient.id))
                    ?: throw AssertionError("should not be null")

            with(receivedMessage) {
                body shouldContain specialCharactersBody
            }
        }

    @Test
    fun sendMaskedEmailMessageShouldSuccessfullySendMessageWithAttachmentsToInternalRecipient() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            val internalRecipient =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internalRecipient",
                )
            val timestamp = Date()

            val attachment =
                EmailAttachment(
                    fileName = "testAttachment.txt",
                    contentId = UUID.randomUUID().toString(),
                    mimeType = "text/plain",
                    inlineAttachment = false,
                    data = "This is the content of the attachment".toByteArray(),
                )

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress),
                    to = listOf(EmailMessage.EmailAddress(internalRecipient.emailAddress)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject with attachment at $timestamp",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body with attachment at $timestamp",
                    attachments = listOf(attachment),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            // Verify message was received
            var receivedMessageId = ""
            when (
                val messagesForRecipient =
                    waitForMessagesByAddress(
                        1,
                        ListEmailMessagesForEmailAddressIdInput(
                            emailAddressId = internalRecipient.id,
                        ),
                    )
            ) {
                is ListAPIResult.Success -> {
                    val message = messagesForRecipient.result.items.first()
                    message.subject shouldBe emailMessageHeader.subject
                    message.from.firstOrNull()?.emailAddress shouldBe provisionedMask.maskAddress
                    message.hasAttachments shouldBe true
                    message.encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
                    receivedMessageId = message.id
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val receivedMessageWithBodyAndAttachments =
                emailClient.getEmailMessageWithBody(GetEmailMessageWithBodyInput(receivedMessageId, internalRecipient.id))
                    ?: throw AssertionError("should not be null")

            with(receivedMessageWithBodyAndAttachments) {
                body shouldContain sendMaskedEmailMessageInput.body
                attachments.size shouldBe 1
                attachments[0].fileName shouldBe attachment.fileName
                attachments[0].mimeType shouldBe attachment.mimeType
                attachments[0].data shouldBe attachment.data
            }
        }

    @Test
    fun sendMaskedEmailMessageSuccessfullySendsWithSenderAndInternalReceiverDisplayNames() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("android-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            val internalRecipient =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internalRecipient",
                )
            val timestamp = Date()
            val senderDisplayName = "Sender Display Name"
            val receiverDisplayName = "Receiver Display Name"

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedMask.maskAddress, senderDisplayName),
                    to = listOf(EmailMessage.EmailAddress(internalRecipient.emailAddress, receiverDisplayName)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject with display names at $timestamp",
                )

            val sendMaskedEmailMessageInput =
                SendMaskedEmailMessageInput(
                    senderEmailMaskId = provisionedMask.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body with display names at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendMaskedEmailMessage(sendMaskedEmailMessageInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            // Wait a bit for message to be delivered
            Thread.sleep(5000)

            // Verify message was received
            when (
                val messagesForRecipient =
                    waitForMessagesByAddress(
                        1,
                        ListEmailMessagesForEmailAddressIdInput(
                            emailAddressId = internalRecipient.id,
                        ),
                    )
            ) {
                is ListAPIResult.Success -> {
                    val message = messagesForRecipient.result.items.first()
                    message.subject shouldBe emailMessageHeader.subject
                    message.from.firstOrNull()?.emailAddress shouldBe provisionedMask.maskAddress
                    message.from.firstOrNull()?.displayName shouldBe senderDisplayName
                    message.to.firstOrNull()?.emailAddress shouldBe internalRecipient.emailAddress
                    message.to.firstOrNull()?.displayName shouldBe receiverDisplayName
                    message.encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }
}
