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
import com.sudoplatform.sudoemail.S3TestAccount
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.TestAccount
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailAlias
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EncryptionStatus
import com.sudoplatform.sudoemail.types.InternetMessageFormatHeader
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.DeprovisionEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageWithBodyInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoemail.types.inputs.SendEmailMessageViaAliasInput
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldHaveSize
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
 * Test the operation of [SudoEmailClient.sendEmailMessageViaAlias].
 */
@RunWith(AndroidJUnit4::class)
class SendEmailMessageViaAliasIntegrationTest : BaseIntegrationTest() {
    private val emailAliasList = mutableListOf<EmailAlias>()
    private val emailAddressList = mutableListOf<EmailAddress>()
    private var runTests = true

    // Shared test resources
    private lateinit var ownershipProof: String
    private lateinit var testEmailAddress: EmailAddress
    private lateinit var aliasDomains: List<String>
    private lateinit var externalTestAccount: ExternalTestAccount
    private lateinit var s3TestAccount: S3TestAccount
    private lateinit var externalAddress: String

    @Before
    fun setup() =
        runTest {
            val config = emailClient.getConfigurationData()

            // Check if email masks are enabled, skip all tests if not
            runTests = config.emailAliasesEnabled
            Assume.assumeTrue("Test suite skipped due to masks not being enabled.", runTests)

            // Create shared test resources
            ownershipProof = sharedOwnershipProof()

            aliasDomains = getAliasDomains(emailClient)
            aliasDomains.size shouldBeGreaterThanOrEqual 1

            // Create email address for testing
            testEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender-pers")
            emailAddressList.add(testEmailAddress)

            if (testSentEmailBucket == null) {
                externalTestAccount = ExternalTestAccount(context, logger, ExternalTestAccountType.GMAIL)
                externalAddress = externalTestAccount.getEmailAddress()
            } else {
                s3TestAccount =
                    S3TestAccount(
                        DefaultS3Client(
                            context,
                            userClient,
                            region!!,
                            testSentEmailBucket!!,
                            logger,
                        ),
                        userClient.getCredentialsProvider().identityId,
                        logger,
                    )
                externalAddress = "${UUID.randomUUID()}@sudoplatform.com"
            }
        }

    @After
    fun teardown() =
        runTest {
            try {
                if (emailAliasList.isNotEmpty()) {
                    emailAliasList.map {
                        emailClient.deprovisionEmailAlias(
                            DeprovisionEmailAliasInput(it.id),
                        )
                    }
                }
                if (emailAddressList.isNotEmpty()) {
                    emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
                }
            } catch (e: Exception) {
                // No-op
            }
            if (runTests && testSentEmailBucket == null) {
                externalTestAccount.closeConnection()
            }
        }

    // Error cases
    @Test
    fun sendEmailMessageViaAliasThrowsUnauthorizedAddressErrorIfUnknownAliasIdUsed() =
        runTest {
            val unknownAliasId = "00000000-0000-0000-0000-000000000000"
            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress("unknown@${aliasDomains.first()}"),
                    to = listOf(EmailMessage.EmailAddress(externalAddress)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject for unknown alias id",
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = unknownAliasId,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body for unknown alias id",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.UnauthorizedAddressException> {
                emailClient.sendEmailMessageViaAlias(sendViaAliasInput)
            }
        }

    @Test
    fun sendEmailMessageViaAliasThrowsUnauthorizedAddressErrorIfAliasAddressDoesNotMatchId() =
        runTest {
            val aliasLocalPart1 = generateSafeLocalPart("android-alias1")
            val aliasAddress1 = "$aliasLocalPart1@${aliasDomains.first()}"

            val provisionedAlias1 = provisionEmailAlias(aliasAddress1, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias1)

            val aliasLocalPart2 = generateSafeLocalPart("android-alias2")
            val aliasAddress2 = "$aliasLocalPart2@${aliasDomains.first()}"

            val provisionedAlias2 = provisionEmailAlias(aliasAddress2, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias2)

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedAlias1.aliasAddress),
                    to = listOf(EmailMessage.EmailAddress(externalAddress)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject for mismatched alias address",
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias2.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body for mismatched alias address",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.UnauthorizedAddressException> {
                emailClient.sendEmailMessageViaAlias(sendViaAliasInput)
            }
        }

    @Test
    fun sendEmailMessageViaAliasThrowsInvalidMessageContentErrorIfGivenNoFromAddress() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("android-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(""),
                    to = listOf(EmailMessage.EmailAddress(externalAddress)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject for no from address",
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body for no from address",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
                emailClient.sendEmailMessageViaAlias(sendViaAliasInput)
            }
        }

    @Test
    fun sendEmailMessageViaAliasThrowsEmailMessageSizeLimitExceededErrorIfMessageExceedsSizeLimit() =
        runTest {
            val isEmulator =
                android.os.Build.HARDWARE
                    .contains("ranchu") ||
                    android.os.Build.PRODUCT
                        .contains("sdk")
            Assume.assumeFalse(
                "Skipping on emulator due to memory constraints",
                isEmulator,
            )

            val aliasLocalPart = generateSafeLocalPart("android-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            val configurationData = emailClient.getConfigurationData()
            val maxOutboundSize = configurationData.emailMessageMaxOutboundMessageSize

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedAlias.aliasAddress),
                    to = listOf(EmailMessage.EmailAddress(externalAddress)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject for message size limit",
                )

            // Create a body that exceeds the maximum outbound size
            val largeBody = "A".repeat((maxOutboundSize + 1))

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias.id,
                    emailMessageHeader = emailMessageHeader,
                    body = largeBody,
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageSizeLimitExceededException> {
                emailClient.sendEmailMessageViaAlias(sendViaAliasInput)
            }
        }

    @Test
    fun sendEmailMessageViaAliasThrowsInvalidMessageContentErrorIfMessageHasNoRecipients() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("android-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedAlias.aliasAddress),
                    to = emptyList(),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject for no recipients",
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body for no recipients",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
                emailClient.sendEmailMessageViaAlias(sendViaAliasInput)
            }
        }

    @Test
    fun sendEmailMessageViaAliasThrowsInvalidMessageContentErrorIfMessageContainsVariousProhibitedAttachmentTypes() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("android-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

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
                        from = EmailMessage.EmailAddress(provisionedAlias.aliasAddress),
                        to = listOf(EmailMessage.EmailAddress(externalAddress)),
                        cc = emptyList(),
                        bcc = emptyList(),
                        replyTo = emptyList(),
                        subject = "Test Subject for prohibited attachment type $randomExtension",
                    )

                val sendViaAliasInput =
                    SendEmailMessageViaAliasInput(
                        senderEmailAliasId = provisionedAlias.id,
                        emailMessageHeader = emailMessageHeader,
                        body = "Test Body for prohibited attachment type $randomExtension",
                        attachments = listOf(prohibitedAttachment),
                        inlineAttachment = emptyList(),
                    )

                shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
                    emailClient.sendEmailMessageViaAlias(sendViaAliasInput)
                }
            }
        }

    // Out-network cases
    @Test
    fun sendEmailMessageViaAliasSuccessfullySendsToSingleExternalEmailAddress() =
        runTest(timeout = Duration.parse("2m")) {
            val aliasLocalPart = generateSafeLocalPart("android-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)
            val timestamp = Date()
            val senderDisplayName = "Sender Display Name"
            val receiverDisplayName = "Receiver Display Name"

            val specialCharactersSubject = "Test Subject with special characters 😎 ¡ ™ £ ¢ ∞ § ¶ • ª. at $timestamp"
            val specialCharactersBody = "Test Body with special characters 💐, 😱 ¡ ™ £ ¢ ∞ § ¶ • ª. at $timestamp"

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedAlias.aliasAddress, senderDisplayName),
                    to = listOf(EmailMessage.EmailAddress(externalAddress, receiverDisplayName)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = specialCharactersSubject,
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias.id,
                    emailMessageHeader = emailMessageHeader,
                    body = specialCharactersBody,
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendEmailMessageViaAlias(sendViaAliasInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            val sentMessage =
                emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
                    ?: throw AssertionError("should not be null")
            with(sentMessage) {
                from.firstOrNull()?.emailAddress shouldBe provisionedAlias.aliasAddress
                to.firstOrNull()?.emailAddress shouldBe externalAddress
                encryptionStatus shouldBe EncryptionStatus.UNENCRYPTED
                emailAliasId shouldBe provisionedAlias.id
            }

            var receivedEmail: TestAccount.ReceivedEmail?
            if (testSentEmailBucket == null) {
                receivedEmail =
                    externalTestAccount.waitForEmailFromSender(
                        sender = aliasAddress,
                        subject = specialCharactersSubject,
                        ExternalTestAccount.WaitOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                    )
            } else {
                receivedEmail =
                    s3TestAccount.waitForEmail(
                        sender = aliasAddress,
                        subject = specialCharactersSubject,
                        S3TestAccount.WaitForEmailOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                    )
            }

            receivedEmail shouldNotBe null
            receivedEmail.subject shouldContain specialCharactersSubject
            receivedEmail.from[0].emailAddress shouldBe aliasAddress
            receivedEmail.from[0].displayName shouldBe senderDisplayName
            receivedEmail.to[0].emailAddress shouldBe externalAddress
            receivedEmail.to[0].displayName shouldBe receiverDisplayName
        }

    @Test
    fun sendEmailMessageViaAliasSuccessfullySendsToCcRecipient() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("android-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)
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
                    from = EmailMessage.EmailAddress(provisionedAlias.aliasAddress),
                    to = emptyList(),
                    cc = listOf(EmailMessage.EmailAddress(externalAddress)),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent with cc recipient and attachment via an alias at $timestamp",
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent with cc recipient and attachment via an alias at $timestamp",
                    attachments = listOf(attachment),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendEmailMessageViaAlias(sendViaAliasInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            var receivedEmail: TestAccount.ReceivedEmail?
            if (testSentEmailBucket == null) {
                receivedEmail =
                    externalTestAccount.waitForEmailFromSender(
                        sender = aliasAddress,
                        subject = emailMessageHeader.subject,
                        ExternalTestAccount.WaitOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                    )
            } else {
                receivedEmail =
                    s3TestAccount.waitForEmail(
                        sender = aliasAddress,
                        subject = emailMessageHeader.subject,
                        S3TestAccount.WaitForEmailOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                    )
            }

            receivedEmail shouldNotBe null
            receivedEmail.subject shouldContain emailMessageHeader.subject
            receivedEmail.from[0].emailAddress shouldBe aliasAddress
            receivedEmail.cc[0].emailAddress shouldBe externalAddress
            receivedEmail.attachments.size shouldBe 1
            receivedEmail.attachments[0].fileName shouldBe attachment.fileName
            receivedEmail.attachments[0].mimeType?.lowercase() shouldBe attachment.mimeType
        }

    @Test
    fun sendEmailMessageViaAliasSuccessfullySendsToBothToAndCcRecipients() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("android-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)
            val timestamp = Date()
            val senderDisplayName = "Sender Display Name"
            val toReceiverDisplayName = "To Receiver Display Name"
            val ccReceiverDisplayName = "CC Receiver Display Name"

            val inboxFolder =
                getFolderByName(
                    client = emailClient,
                    emailAddressId = testEmailAddress.id,
                    folderName = "INBOX",
                ) ?: fail("EmailFolder could not be found")

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedAlias.aliasAddress, senderDisplayName),
                    to = listOf(EmailMessage.EmailAddress(testEmailAddress.emailAddress, toReceiverDisplayName)),
                    cc = listOf(EmailMessage.EmailAddress(externalAddress, ccReceiverDisplayName)),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent with both to and cc recipient via an alias at $timestamp",
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent with both to and cc recipient via an alias at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendEmailMessageViaAlias(sendViaAliasInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            val inboundMessages =
                waitForMessagesByFolder(
                    1,
                    ListEmailMessagesForEmailFolderIdInput(
                        folderId = inboxFolder.id,
                    ),
                )

            when (
                inboundMessages
            ) {
                is ListAPIResult.Success -> {
                    inboundMessages.result.items shouldHaveSize 1
                    inboundMessages.result.items[0].emailAddressId shouldBe testEmailAddress.id
                    inboundMessages.result.items[0].subject shouldContain emailMessageHeader.subject
                    inboundMessages.result.items[0]
                        .from
                        .firstOrNull()
                        ?.emailAddress shouldBe aliasAddress
                    inboundMessages.result.items[0]
                        .to
                        .firstOrNull()
                        ?.emailAddress shouldBe testEmailAddress.emailAddress
                    inboundMessages.result.items[0].encryptionStatus shouldBe EncryptionStatus.UNENCRYPTED
                }

                is ListAPIResult.Partial -> {
                    kotlin.test.fail("Unexpected failure to list messages")
                }
            }

            var externalReceivedMessage: TestAccount.ReceivedEmail?
            if (testSentEmailBucket == null) {
                externalReceivedMessage =
                    externalTestAccount.waitForEmailFromSender(
                        sender = aliasAddress,
                        subject = emailMessageHeader.subject,
                        ExternalTestAccount.WaitOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                    )
            } else {
                externalReceivedMessage =
                    s3TestAccount.waitForEmail(
                        sender = aliasAddress,
                        subject = emailMessageHeader.subject,
                        S3TestAccount.WaitForEmailOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                    )
            }

            externalReceivedMessage shouldNotBe null
            externalReceivedMessage.subject shouldContain emailMessageHeader.subject
            externalReceivedMessage.from[0].emailAddress shouldBe aliasAddress
            externalReceivedMessage.from[0].displayName shouldBe senderDisplayName
        }

    @Test
    fun sendEmailMessageViaAliasSuccessfullySendsToInternalAndExternalRecipients() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("android-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val internalRecipient =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internal-recipient",
                )
            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)
            val timestamp = Date()

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedAlias.aliasAddress),
                    to =
                        listOf(
                            EmailMessage.EmailAddress(internalRecipient.emailAddress),
                            EmailMessage.EmailAddress(externalAddress),
                        ),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent to both internal and external recipients via an alias at $timestamp",
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent to both internal and external recipients via an alias at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendEmailMessageViaAlias(sendViaAliasInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

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
                    message.subject shouldContain emailMessageHeader.subject
                    message.from.firstOrNull()?.emailAddress shouldBe provisionedAlias.aliasAddress
                    message.encryptionStatus shouldBe EncryptionStatus.UNENCRYPTED
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            var externalReceivedEmail: TestAccount.ReceivedEmail?
            if (testSentEmailBucket == null) {
                externalReceivedEmail =
                    externalTestAccount.waitForEmailFromSender(
                        sender = aliasAddress,
                        subject = emailMessageHeader.subject,
                        ExternalTestAccount.WaitOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                    )
            } else {
                externalReceivedEmail =
                    s3TestAccount.waitForEmail(
                        sender = aliasAddress,
                        subject = emailMessageHeader.subject,
                        S3TestAccount.WaitForEmailOptions(timeoutMs = 120_000, searchFromDate = Date(timestamp.time - 60_000)),
                    )
            }

            externalReceivedEmail shouldNotBe null
            externalReceivedEmail.subject shouldContain emailMessageHeader.subject
            externalReceivedEmail.from[0].emailAddress shouldBe aliasAddress
            externalReceivedEmail.to.find { it.emailAddress == externalAddress } shouldNotBe null
        }

    // In-network cases
    @Test
    fun sendEmailMessageViaAliasSuccessfullySendsToSingleInternalEmailAddress() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("android-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)
            val internalRecipient =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internalRecipient",
                )
            emailAddressList.add(internalRecipient)
            val timestamp = Date()

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedAlias.aliasAddress),
                    to = listOf(EmailMessage.EmailAddress(internalRecipient.emailAddress)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent to internal recipient via an alias at $timestamp",
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent to internal recipient via an alias at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendEmailMessageViaAlias(sendViaAliasInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            val sentMessage =
                emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
                    ?: throw AssertionError("should not be null")

            with(sentMessage) {
                from.firstOrNull()?.emailAddress shouldBe provisionedAlias.aliasAddress
                to.firstOrNull()?.emailAddress shouldBe internalRecipient.emailAddress
                encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
            }

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
                    message.subject shouldContain emailMessageHeader.subject
                    message.from.firstOrNull()?.emailAddress shouldBe provisionedAlias.aliasAddress
                    message.encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
                    message.emailAliasId shouldBe null
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun sendEmailMessageViaAliasSuccessfullySendsToMultipleInternalEmailAddresses() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("android-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            val internalRecipient1 =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internalRecipient1",
                )
            emailAddressList.add(internalRecipient1)

            val internalRecipient2 =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internalRecipient2",
                )
            emailAddressList.add(internalRecipient2)
            val timestamp = Date()

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedAlias.aliasAddress),
                    to =
                        listOf(
                            EmailMessage.EmailAddress(internalRecipient1.emailAddress),
                            EmailMessage.EmailAddress(internalRecipient2.emailAddress),
                        ),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent to multiple internal recipients via an alias at $timestamp",
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent to multiple internal recipients via an alias at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendEmailMessageViaAlias(sendViaAliasInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

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
                        message.subject shouldContain emailMessageHeader.subject
                        message.from.firstOrNull()?.emailAddress shouldBe provisionedAlias.aliasAddress
                        message.encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
                        message.emailAliasId shouldBe null
                    }

                    else -> {
                        fail("Unexpected ListAPIResult")
                    }
                }
            }
        }

    @Test
    fun sendEmailMessageViaAliasSuccessfullySendsToInternalCcRecipient() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("android-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            val internalRecipient =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internalRecipient",
                )
            emailAddressList.add(internalRecipient)
            val timestamp = Date()

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedAlias.aliasAddress),
                    to = emptyList(),
                    cc = listOf(EmailMessage.EmailAddress(internalRecipient.emailAddress)),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent with internal cc recipient via an alias at $timestamp",
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent with internal cc recipient via an alias at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendEmailMessageViaAlias(sendViaAliasInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

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
                    message.subject shouldContain emailMessageHeader.subject
                    message.from.firstOrNull()?.emailAddress shouldBe provisionedAlias.aliasAddress
                    message.cc.firstOrNull()?.emailAddress shouldBe internalRecipient.emailAddress
                    message.encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
                    message.emailAliasId shouldBe null
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun sendEmailMessageViaAliasSuccessfullySendsToAnotherAlias() =
        runTest {
            val aliasLocalPart1 = generateSafeLocalPart("android-alias1")
            val aliasAddress1 = "$aliasLocalPart1@${aliasDomains.first()}"

            val provisionedAlias1 = provisionEmailAlias(aliasAddress1, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias1)

            val otherProvisionedAddress =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                )
            emailAddressList.add(otherProvisionedAddress)
            val aliasLocalPart2 = generateSafeLocalPart("android-alias2")
            val aliasAddress2 = "$aliasLocalPart2@${aliasDomains.first()}"

            val provisionedAlias2 = provisionEmailAlias(aliasAddress2, otherProvisionedAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias2)
            val timestamp = Date()

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedAlias1.aliasAddress),
                    to = listOf(EmailMessage.EmailAddress(provisionedAlias2.aliasAddress)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject sent from one alias to another alias at $timestamp",
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias1.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body sent from one alias to another alias at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendEmailMessageViaAlias(sendViaAliasInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

            // Verify the second alias received the message
            when (
                val messagesForAlias2 =
                    waitForMessagesByAddress(
                        1,
                        ListEmailMessagesForEmailAddressIdInput(
                            emailAddressId = otherProvisionedAddress.id,
                        ),
                    )
            ) {
                is ListAPIResult.Success -> {
                    val message = messagesForAlias2.result.items.first()
                    message.subject shouldContain emailMessageHeader.subject
                    message.from.firstOrNull()?.emailAddress shouldBe provisionedAlias1.aliasAddress
                    message.encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
                    message.emailAliasId shouldBe provisionedAlias2.id
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun sendEmailMessageViaAliasSuccessfullySendToInternalRecipientWithSpecialCharactersInSubjectAndBody() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("android-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            val internalRecipient =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internalRecipient",
                )
            emailAddressList.add(internalRecipient)
            val timestamp = Date()

            val specialCharactersSubject = "Test Subject with special characters 😎 ¡ ™ £ ¢ ∞ § ¶ • ª. at $timestamp"
            val specialCharactersBody = "Test Body with special characters 💐, 😱 ¡ ™ £ ¢ ∞ § ¶ • ª. at $timestamp"

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedAlias.aliasAddress),
                    to = listOf(EmailMessage.EmailAddress(internalRecipient.emailAddress)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = specialCharactersSubject,
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias.id,
                    emailMessageHeader = emailMessageHeader,
                    body = specialCharactersBody,
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendEmailMessageViaAlias(sendViaAliasInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

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
                    message.subject shouldContain emailMessageHeader.subject
                    message.from.firstOrNull()?.emailAddress shouldBe provisionedAlias.aliasAddress
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
    fun sendEmailMessageViaAliasShouldSuccessfullySendMessageWithAttachmentsToInternalRecipient() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("android-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            val internalRecipient =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internal-recipient",
                )
            emailAddressList.add(internalRecipient)
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
                    from = EmailMessage.EmailAddress(provisionedAlias.aliasAddress),
                    to = listOf(EmailMessage.EmailAddress(internalRecipient.emailAddress)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject with attachment at $timestamp",
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body with attachment at $timestamp",
                    attachments = listOf(attachment),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendEmailMessageViaAlias(sendViaAliasInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

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
                    message.subject shouldContain emailMessageHeader.subject
                    message.from.firstOrNull()?.emailAddress shouldBe provisionedAlias.aliasAddress
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
                body shouldContain sendViaAliasInput.body
                attachments.size shouldBe 1
                attachments[0].fileName shouldBe attachment.fileName
                attachments[0].mimeType shouldBe attachment.mimeType
                attachments[0].data shouldBe attachment.data
            }
        }

    @Test
    fun sendEmailMessageViaAliasSuccessfullySendsWithSenderAndInternalReceiverDisplayNames() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("android-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            val internalRecipient =
                provisionEmailAddress(
                    emailClient,
                    ownershipProof,
                    prefix = "internalRecipient",
                )
            emailAddressList.add(internalRecipient)
            val timestamp = Date()
            val senderDisplayName = "Sender Display Name"
            val receiverDisplayName = "Receiver Display Name"

            val emailMessageHeader =
                InternetMessageFormatHeader(
                    from = EmailMessage.EmailAddress(provisionedAlias.aliasAddress, senderDisplayName),
                    to = listOf(EmailMessage.EmailAddress(internalRecipient.emailAddress, receiverDisplayName)),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject with display names at $timestamp",
                )

            val sendViaAliasInput =
                SendEmailMessageViaAliasInput(
                    senderEmailAliasId = provisionedAlias.id,
                    emailMessageHeader = emailMessageHeader,
                    body = "Test Body with display names at $timestamp",
                    attachments = emptyList(),
                    inlineAttachment = emptyList(),
                )

            val sendResult = emailClient.sendEmailMessageViaAlias(sendViaAliasInput)

            sendResult.id shouldNotBe null

            waitForMessage(sendResult.id)

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
                    message.subject shouldContain emailMessageHeader.subject
                    message.from.firstOrNull()?.emailAddress shouldBe provisionedAlias.aliasAddress
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
