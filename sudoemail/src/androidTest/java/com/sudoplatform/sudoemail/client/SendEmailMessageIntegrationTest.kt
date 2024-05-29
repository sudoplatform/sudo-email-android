/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.TestData
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.Direction
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EncryptionStatus
import com.sudoplatform.sudoemail.types.InternetMessageFormatHeader
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesInput
import com.sudoplatform.sudoemail.types.inputs.SendEmailMessageInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of [SudoEmailClient.sendEmailMessage].
 */
@RunWith(AndroidJUnit4::class)
class SendEmailMessageIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

    @Before
    fun setup() {
        sudoClient.reset()
        sudoClient.generateEncryptionKey()
    }

    @After
    fun teardown() = runTest {
        emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        sudoList.map { sudoClient.deleteSudo(it) }
        sudoClient.reset()
    }

    /* Send Non-E2E email tests */

    @Test
    fun sendEmailShouldReturnEmailMessageId() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        var emailId = ""
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            emailId = result.id
            emailId.isBlank() shouldBe false
            result.createdAt shouldNotBe null
        }

        when (val listEmailMessages = waitForMessages(messageCount * 2)) {
            is ListAPIResult.Success -> {
                val outbound = listEmailMessages.result.items.filter {
                    it.direction == Direction.OUTBOUND
                }
                val inbound = listEmailMessages.result.items.filter {
                    it.direction == Direction.INBOUND
                }
                outbound.size shouldBe messageCount
                inbound.size shouldBe messageCount
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val retrievedEmailMessage = emailClient.getEmailMessage(GetEmailMessageInput(emailId))
            ?: fail("Email message could not be found")
        with(retrievedEmailMessage) {
            from.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
            to.firstOrNull()?.emailAddress shouldBe toSimulatorAddress
            hasAttachments shouldBe false
            size shouldBeGreaterThan 0.0
            encryptionStatus shouldBe EncryptionStatus.UNENCRYPTED
        }
    }

    @Test
    fun sendEmailWithValidAttachmentShouldReturnEmailMessageIdAndCreatedAt() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val attachment = EmailAttachment(
            fileName = "goodExtension.pdf",
            contentId = UUID.randomUUID().toString(),
            mimeType = "application/pdf",
            inlineAttachment = false,
            data = "This file has a valid file extension".toByteArray(),
        )
        val inlineAttachment = EmailAttachment(
            fileName = "goodImage.png",
            contentId = UUID.randomUUID().toString(),
            mimeType = "image/png",
            inlineAttachment = true,
            data = ByteArray(42),
        )

        val messageCount = 2
        var emailId = ""
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(
                emailClient,
                emailAddress,
                attachments = listOf(attachment),
                inlineAttachments = listOf(inlineAttachment),
            )
            emailId = result.id
            emailId.isBlank() shouldBe false
            result.createdAt shouldNotBe null
        }

        when (val listEmailMessages = waitForMessages(messageCount * 2)) {
            is ListAPIResult.Success -> {
                val outbound = listEmailMessages.result.items.filter {
                    it.direction == Direction.OUTBOUND
                }
                val inbound = listEmailMessages.result.items.filter {
                    it.direction == Direction.INBOUND
                }
                outbound.size shouldBe messageCount
                inbound.size shouldBe messageCount
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val retrievedEmailMessage = emailClient.getEmailMessage(GetEmailMessageInput(emailId))
            ?: fail("Email message could not be found")
        with(retrievedEmailMessage) {
            from.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
            to.firstOrNull()?.emailAddress shouldBe toSimulatorAddress
            hasAttachments shouldBe true
            size shouldBeGreaterThan 0.0
            encryptionStatus shouldBe EncryptionStatus.UNENCRYPTED
        }
    }

    @Test
    fun sendEmailShouldThrowWithEmptyRecipients() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
            sendEmailMessage(
                emailClient,
                emailAddress,
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        }
    }

    @Test
    fun sendEmailWithMixtureOfRecipientsShouldReturnUnencryptedStatus() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val receiverEmailAddressTwo = provisionEmailAddress(emailClient, ownershipProof)
        receiverEmailAddressTwo shouldNotBe null
        emailAddressList.add(receiverEmailAddressTwo)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.isEmpty() shouldBe false
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        var emailId = ""
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(
                emailClient,
                emailAddress,
                listOf(
                    EmailMessage.EmailAddress(receiverEmailAddress.emailAddress),
                    EmailMessage.EmailAddress(toSimulatorAddress),
                ),
                listOf(EmailMessage.EmailAddress(receiverEmailAddressTwo.emailAddress)),
            )
            emailId = result.id
            emailId.isBlank() shouldBe false
            result.createdAt shouldNotBe null
        }

        when (val listEmailMessages = waitForMessages(messageCount * 4)) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.isEmpty() shouldBe false
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val retrievedEmailMessage = emailClient.getEmailMessage(GetEmailMessageInput(emailId))
            ?: fail("Email message could not be found")
        with(retrievedEmailMessage) {
            from.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
            to shouldBe listOf(
                EmailMessage.EmailAddress(receiverEmailAddress.emailAddress),
                EmailMessage.EmailAddress(toSimulatorAddress),
            )
            cc shouldBe listOf(EmailMessage.EmailAddress(receiverEmailAddressTwo.emailAddress))
            hasAttachments shouldBe false
            size shouldBeGreaterThan 0.0
            encryptionStatus shouldBe EncryptionStatus.UNENCRYPTED
        }
    }

    @Test
    fun sendEmailShouldThrowWhenInvalidRecipientAddressUsed() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
            sendEmailMessage(emailClient, emailAddress, listOf(EmailMessage.EmailAddress("invalidEmailAddress")))
        }
    }

    @Test
    fun sendEmailShouldThrowWhenInvalidSenderAddressUsed() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val emailMessageHeader = InternetMessageFormatHeader(
            EmailMessage.EmailAddress(emailAddress.emailAddress),
            listOf(EmailMessage.EmailAddress(emailAddress.emailAddress)),
            emptyList(),
            emptyList(),
            emptyList(),
            "message subject",
        )
        val sendEmailMessageInput = SendEmailMessageInput(
            "invalidSenderAddressId",
            emailMessageHeader,
            "email body",
        )
        shouldThrow<SudoEmailClient.EmailMessageException.UnauthorizedAddressException> {
            emailClient.sendEmailMessage(sendEmailMessageInput)
        }
    }

    @Test
    fun sendEmailShouldThrowWhenInvalidAttachmentUsed() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val attachment = EmailAttachment(
            fileName = "badExtension.js",
            contentId = UUID.randomUUID().toString(),
            mimeType = "text/javascript",
            inlineAttachment = false,
            data = "This file has an invalid file extension".toByteArray(),
        )
        shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
            sendEmailMessage(emailClient, emailAddress, attachments = listOf(attachment))
        }
    }

    @Test
    fun sendEmailShouldThrowWhenInNetworkRecipientAddressNotFound() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val emailDomains = emailClient.getSupportedEmailDomains(CachePolicy.REMOTE_ONLY)
        val inNetworkNotFoundAddress = "notfoundaddress@${emailDomains.first()}"

        shouldThrow<SudoEmailClient.EmailMessageException.InNetworkAddressNotFoundException> {
            sendEmailMessage(
                emailClient,
                emailAddress,
                listOf(
                    EmailMessage.EmailAddress(toSimulatorAddress),
                    EmailMessage.EmailAddress(inNetworkNotFoundAddress),
                ),
            )
        }
    }

    /* Send E2E email tests */

    @Test
    fun sendEncryptedEmailShouldReturnEmailMessageId() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.isEmpty() shouldBe false
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        var emailId = ""
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(
                emailClient,
                emailAddress,
                listOf(EmailMessage.EmailAddress(receiverEmailAddress.emailAddress)),
            )
            emailId = result.id
            emailId.isBlank() shouldBe false
            result.createdAt shouldNotBe null
        }

        when (val listEmailMessages = waitForMessages(messageCount * 2)) {
            is ListAPIResult.Success -> {
                val outbound = listEmailMessages.result.items.filter {
                    it.direction == Direction.OUTBOUND
                }
                val inbound = listEmailMessages.result.items.filter {
                    it.direction == Direction.INBOUND
                }
                outbound.size shouldBe messageCount
                inbound.size shouldBe messageCount
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val retrievedEmailMessage = emailClient.getEmailMessage(GetEmailMessageInput(emailId))
            ?: fail("Email message could not be found")
        with(retrievedEmailMessage) {
            from.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
            to.firstOrNull()?.emailAddress shouldBe receiverEmailAddress.emailAddress
            hasAttachments shouldBe false
            size shouldBeGreaterThan 0.0
            encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
        }
    }

    @Test
    fun sendEncryptedEmailWithValidAttachmentShouldReturnEmailMessageId() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.isEmpty() shouldBe false
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val attachment = EmailAttachment(
            fileName = "goodExtension.pdf",
            contentId = UUID.randomUUID().toString(),
            mimeType = "application/pdf",
            inlineAttachment = false,
            data = "This file has a valid file extension".toByteArray(),
        )
        val inlineAttachment = EmailAttachment(
            fileName = "goodImage.png",
            contentId = UUID.randomUUID().toString(),
            mimeType = "image/png",
            inlineAttachment = true,
            data = ByteArray(42),
        )

        val messageCount = 2
        var emailId = ""
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(
                emailClient,
                emailAddress,
                listOf(EmailMessage.EmailAddress(receiverEmailAddress.emailAddress)),
                attachments = listOf(attachment),
                inlineAttachments = listOf(inlineAttachment),
            )
            emailId = result.id
            emailId.isBlank() shouldBe false
            result.createdAt shouldNotBe null
        }

        when (val listEmailMessages = waitForMessages(messageCount * 2)) {
            is ListAPIResult.Success -> {
                val outbound = listEmailMessages.result.items.filter {
                    it.direction == Direction.OUTBOUND
                }
                val inbound = listEmailMessages.result.items.filter {
                    it.direction == Direction.INBOUND
                }
                outbound.size shouldBe messageCount
                inbound.size shouldBe messageCount
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val retrievedEmailMessage = emailClient.getEmailMessage(GetEmailMessageInput(emailId))
            ?: fail("Email message could not be found")
        with(retrievedEmailMessage) {
            from.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
            to.firstOrNull()?.emailAddress shouldBe receiverEmailAddress.emailAddress
            hasAttachments shouldBe true
            size shouldBeGreaterThan 0.0
            encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
        }
    }

    @Test
    fun sendEncryptedEmailWithMultipleRecipientsShouldReturnEmailMessageId() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val receiverEmailAddressTwo = provisionEmailAddress(emailClient, ownershipProof)
        receiverEmailAddressTwo shouldNotBe null
        emailAddressList.add(receiverEmailAddressTwo)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.isEmpty() shouldBe false
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        var emailId = ""
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(
                emailClient,
                emailAddress,
                listOf(
                    EmailMessage.EmailAddress(receiverEmailAddress.emailAddress),
                    EmailMessage.EmailAddress(receiverEmailAddressTwo.emailAddress),
                ),
            )
            emailId = result.id
            emailId.isBlank() shouldBe false
            result.createdAt shouldNotBe null
        }

        when (val listEmailMessages = waitForMessages(messageCount * 3)) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.isEmpty() shouldBe false
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val retrievedEmailMessage = emailClient.getEmailMessage(GetEmailMessageInput(emailId))
            ?: fail("Email message could not be found")
        with(retrievedEmailMessage) {
            from.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
            to.map { it.emailAddress } shouldContain (receiverEmailAddress.emailAddress)
            hasAttachments shouldBe false
            size shouldBeGreaterThan 0.0
            encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
        }
    }

    @Test
    fun sendEncryptedEmailWithIdenticalFromRecipientShouldReturnEmailAddressId() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.isEmpty() shouldBe false
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        var emailId = ""
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(
                emailClient,
                emailAddress,
                listOf(EmailMessage.EmailAddress(emailAddress.emailAddress)),
            )
            emailId = result.id
            emailId.isBlank() shouldBe false
            result.createdAt shouldNotBe null
        }

        when (val listEmailMessages = waitForMessages(messageCount * 2)) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.isEmpty() shouldBe false
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val retrievedEmailMessage = emailClient.getEmailMessage(GetEmailMessageInput(emailId))
            ?: fail("Email message could not be found")
        with(retrievedEmailMessage) {
            from.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
            to.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
            hasAttachments shouldBe false
            size shouldBeGreaterThan 0.0
            encryptionStatus shouldBe EncryptionStatus.ENCRYPTED
        }
    }

    @Test
    fun sendEncryptedEmailShouldThrowWhenInNetworkRecipientAddressNotFound() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val emailDomains = emailClient.getSupportedEmailDomains(CachePolicy.REMOTE_ONLY)
        val inNetworkNotFoundAddress = "notfoundaddress@${emailDomains.first()}"

        shouldThrow<SudoEmailClient.EmailMessageException.InNetworkAddressNotFoundException> {
            sendEmailMessage(
                emailClient,
                emailAddress,
                listOf(
                    EmailMessage.EmailAddress(receiverEmailAddress.emailAddress),
                    EmailMessage.EmailAddress(inNetworkNotFoundAddress),
                ),
            )
        }
    }

    @Test
    @Ignore("Ignore as this depends on the amount of memory on the simulator")
    fun sendEncryptedEmailShouldThrowIfMessageIsTooLarge() = runTest {
        val configurationData = emailClient.getConfigurationData()
        val emailMessageMaxOutboundMessageSize =
            configurationData.emailMessageMaxOutboundMessageSize
        val attachment = EmailAttachment(
            fileName = "large-attachment.txt",
            contentId = "",
            mimeType = "text/plain",
            inlineAttachment = false,
            data = ByteArray(emailMessageMaxOutboundMessageSize),
        )

        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageSizeLimitExceededException> {
            sendEmailMessage(
                emailClient,
                emailAddress,
                listOf(EmailMessage.EmailAddress(emailAddress.emailAddress)),
                attachments = listOf(attachment),
            )
        }
    }
}
