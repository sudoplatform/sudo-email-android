/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import aws.smithy.kotlin.runtime.content.Document
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.TestData
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.BlockedEmailAddressAction
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.inputs.ListEmailFoldersForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.awaitility.Duration
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of [SudoEmailClient.blockEmailAddresses]
 */
@RunWith(AndroidJUnit4::class)
class BlockEmailAddressesIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

    @Before
    fun setup() {
        sudoClient.reset()
        sudoClient.generateEncryptionKey()
    }

    @After
    fun teardown() =
        runTest {
            val blocklist = emailClient.getEmailAddressBlocklist()
            if (blocklist.isNotEmpty()) {
                emailClient.unblockEmailAddressesByHashedValue(blocklist.map { it.hashedBlockedValue })
            }
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
            sudoList.map { sudoClient.deleteSudo(it) }
            sudoClient.reset()
        }

    @Test
    fun blockEmailAddressesThrowsAnErrorIfPassedAnEmptyAddressesArray() =
        runTest {
            shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                emailClient.blockEmailAddresses(emptyList())
            }
        }

    @Test
    fun blockEmailAddressesThrowsAnErrorIfPassedAnInvalidEmailAddress() =
        runTest {
            shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                emailClient.blockEmailAddresses(listOf("not-an-email-address"))
            }
        }

    @Test
    fun blockEmailAddressesShouldAllowDuplicateAddresses() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
            receiverEmailAddress shouldNotBe null
            emailAddressList.add(receiverEmailAddress)

            val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender-${UUID.randomUUID()}")
            emailAddressToBlock shouldNotBe null
            emailAddressList.add(emailAddressToBlock)

            val result =
                emailClient.blockEmailAddresses(
                    listOf(
                        emailAddressToBlock.emailAddress.lowercase(),
                        emailAddressToBlock.emailAddress.uppercase(),
                    ),
                )

            result.status shouldBe BatchOperationStatus.SUCCESS
        }

    @Test
    fun blockEmailAddressesShouldSuccessfullyBlockASingleAddress() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
            receiverEmailAddress shouldNotBe null
            emailAddressList.add(receiverEmailAddress)

            val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender-${UUID.randomUUID()}")
            emailAddressToBlock shouldNotBe null
            emailAddressList.add(emailAddressToBlock)

            val result =
                emailClient.blockEmailAddresses(
                    listOf(
                        emailAddressToBlock.emailAddress,
                    ),
                )

            result.status shouldBe BatchOperationStatus.SUCCESS
        }

    @Test
    fun blockEmailAddressesShouldSuccessfullyBlockASingleAddressWithActionParameter() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
            receiverEmailAddress shouldNotBe null
            emailAddressList.add(receiverEmailAddress)

            val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender-${UUID.randomUUID()}")
            emailAddressToBlock shouldNotBe null
            emailAddressList.add(emailAddressToBlock)

            val result =
                emailClient.blockEmailAddresses(
                    listOf(
                        emailAddressToBlock.emailAddress,
                    ),
                    action = BlockedEmailAddressAction.SPAM,
                )

            result.status shouldBe BatchOperationStatus.SUCCESS
        }

    @Test
    fun blockEmailAddressesShouldSuccessfullyBlockASingleAddressWithEmailAddressIdParameter() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
            receiverEmailAddress shouldNotBe null
            emailAddressList.add(receiverEmailAddress)

            val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender-${UUID.randomUUID()}")
            emailAddressToBlock shouldNotBe null
            emailAddressList.add(emailAddressToBlock)

            val result =
                emailClient.blockEmailAddresses(
                    listOf(
                        emailAddressToBlock.emailAddress,
                    ),
                    emailAddressId = receiverEmailAddress.id,
                )

            result.status shouldBe BatchOperationStatus.SUCCESS
        }

    @Test
    fun blockEmailAddressesShouldSuccessfullyBlockASingleAddressWithLevelParameter() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
            receiverEmailAddress shouldNotBe null
            emailAddressList.add(receiverEmailAddress)

            val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender-${UUID.randomUUID()}")
            emailAddressToBlock shouldNotBe null
            emailAddressList.add(emailAddressToBlock)

            val result =
                emailClient.blockEmailAddresses(
                    listOf(
                        emailAddressToBlock.emailAddress,
                    ),
                    level = com.sudoplatform.sudoemail.types.BlockedEmailAddressLevel.DOMAIN,
                )

            result.status shouldBe BatchOperationStatus.SUCCESS
        }

    @Test
    fun blockEmailAddressesShouldSuccessfullyBlockMultipleAddress() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
            receiverEmailAddress shouldNotBe null
            emailAddressList.add(receiverEmailAddress)

            val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender-${UUID.randomUUID()}")
            emailAddressToBlock shouldNotBe null
            emailAddressList.add(emailAddressToBlock)

            val result =
                emailClient.blockEmailAddresses(
                    listOf(
                        emailAddressToBlock.emailAddress,
                        "spammyMcSpamface${UUID.randomUUID()}@spambot.com",
                    ),
                )
            result.status shouldBe BatchOperationStatus.SUCCESS
        }

    // This test can take a while...
    @Test
    fun messagesFromBlockedAddressesShouldNotBeReceived() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val receiver = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
            receiver shouldNotBe null
            emailAddressList.add(receiver)

            val sender = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender-${UUID.randomUUID()}")
            sender shouldNotBe null
            emailAddressList.add(sender)

            val listFoldersInput = ListEmailFoldersForEmailAddressIdInput(receiver.id)
            val inboxFolder =
                emailClient.listEmailFoldersForEmailAddressId(listFoldersInput).items.find { it.folderName == "INBOX" }
            inboxFolder shouldNotBe null
            val inboxFolderId = inboxFolder?.id ?: fail("inbox folder unexpectedly null")
            // Send message while unblocked
            sendEmailMessage(
                client = emailClient,
                fromAddress = sender,
                toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = receiver.emailAddress)),
                subject = "messagesFromBlockedAddressesShouldNotBeReceived ${UUID.randomUUID()}",
                body = "This message should go through",
            )

            // Make sure message was received
            waitForMessagesByFolder(
                count = 1,
                listInput =
                    ListEmailMessagesForEmailFolderIdInput(
                        folderId = inboxFolderId,
                    ),
            )

            // Block the sender
            val result =
                emailClient.blockEmailAddresses(
                    listOf(
                        sender.emailAddress,
                    ),
                )
            result.status shouldBe BatchOperationStatus.SUCCESS

            // Send another message
            sendEmailMessage(
                emailClient,
                sender,
                toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = receiver.emailAddress)),
                body = "This message should get blocked ${UUID.randomUUID()}",
            )

            // Wait for messages to potentially arrive even though they shouldn't
            // Increase timeout from default 10 seconds and wait 60 seconds for first poll
            // Should still have the original message, but not latest one
            waitForMessagesByFolder(
                atMost = Duration.TWO_MINUTES,
                pollInterval = Duration.ONE_MINUTE,
                count = 1,
                listInput =
                    ListEmailMessagesForEmailFolderIdInput(
                        folderId = inboxFolderId,
                    ),
            )

            // Unblock the sender
            val unblockResult =
                emailClient.unblockEmailAddresses(
                    listOf(sender.emailAddress),
                )
            unblockResult.status shouldBe BatchOperationStatus.SUCCESS

            // Send one more message
            sendEmailMessage(
                client = emailClient,
                fromAddress = sender,
                toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = receiver.emailAddress)),
                subject = "messagesFromBlockedAddressesShouldNotBeReceived ${UUID.randomUUID()}",
                body = "This message should go through",
            )

            // Check that it was received, should have original message plus last one
            waitForMessagesByFolder(
                count = 2,
                listInput =
                    ListEmailMessagesForEmailFolderIdInput(
                        folderId = inboxFolderId,
                    ),
            )
        }

    @Test
    fun messagesFromBlockedAddressWithSpamActionShouldBeHandledAppropriately() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val receiver = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
            receiver shouldNotBe null
            emailAddressList.add(receiver)

            val sender = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender-${UUID.randomUUID()}")
            sender shouldNotBe null
            emailAddressList.add(sender)

            val listFoldersInput = ListEmailFoldersForEmailAddressIdInput(receiver.id)
            val foldersList = emailClient.listEmailFoldersForEmailAddressId(listFoldersInput).items
            val spamFolder = foldersList.find { it.folderName == "SPAM" }
            val inboxFolder = foldersList.find { it.folderName == "INBOX" }
            inboxFolder shouldNotBe null
            // Send message while unblocked
            val sendRes =
                sendEmailMessage(
                    client = emailClient,
                    fromAddress = sender,
                    toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = receiver.emailAddress)),
                    subject = "messagesFromBlockedAddressWithSpamActionShouldBeHandledAppropriately ${UUID.randomUUID()}",
                    body = "This message should go through",
                )
            sendRes shouldNotBe null
            sendRes.id shouldNotBe null

            // Make sure message was received
            waitForMessagesByFolder(
                count = 1,
                listInput =
                    ListEmailMessagesForEmailFolderIdInput(
                        folderId = inboxFolder!!.id,
                    ),
            )

            // Block the sender
            val result =
                emailClient.blockEmailAddresses(
                    listOf(
                        sender.emailAddress,
                    ),
                    action = BlockedEmailAddressAction.SPAM,
                )
            result.status shouldBe BatchOperationStatus.SUCCESS

            // Send another message
            sendEmailMessage(
                emailClient,
                fromAddress = sender,
                toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = receiver.emailAddress)),
                body = "This message should get blocked or go to spam",
            )

            // Increase timeout from default 10 seconds and wait 60 seconds for first poll
            // Should still have the original message, but not latest one
            waitForMessagesByFolder(
                atMost = Duration.TWO_MINUTES,
                pollInterval = Duration.ONE_MINUTE,
                // If we are checking the inbox, the original message will be there, if spam then this one will be there
                count = 1,
                listInput =
                    ListEmailMessagesForEmailFolderIdInput(
                        folderId = spamFolder?.id ?: inboxFolder.id,
                    ),
            )

            // Unblock the sender
            val unblockResult =
                emailClient.unblockEmailAddresses(
                    listOf(sender.emailAddress),
                )
            unblockResult.status shouldBe BatchOperationStatus.SUCCESS

            // Send one more message
            sendEmailMessage(
                client = emailClient,
                fromAddress = sender,
                toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = receiver.emailAddress)),
                subject = "messagesFromBlockedAddressesShouldNotBeReceived ${UUID.randomUUID()}",
                body = "This message should go through",
            )

            // Check that it was received, should have original message plus last one
            waitForMessagesByFolder(
                count = 2,
                listInput =
                    ListEmailMessagesForEmailFolderIdInput(
                        folderId = inboxFolder.id,
                    ),
            )
        }

    @Test
    fun blockingByEmailAddressIdDoesNotBlockForOtherEmailAddresses() =
        runTest(timeout = kotlin.time.Duration.parse("3m")) {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val receiver1 = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver1-${UUID.randomUUID()}")
            receiver1 shouldNotBe null
            emailAddressList.add(receiver1)

            val receiver2 = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver2-${UUID.randomUUID()}")
            receiver2 shouldNotBe null
            emailAddressList.add(receiver2)

            val sender = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender-${UUID.randomUUID()}")
            sender shouldNotBe null
            emailAddressList.add(sender)

            val listFoldersInput1 = ListEmailFoldersForEmailAddressIdInput(receiver1.id)
            val receiver1Inbox =
                emailClient.listEmailFoldersForEmailAddressId(listFoldersInput1).items.find { it.folderName == "INBOX" }
            receiver1Inbox shouldNotBe null
            val receiver1InboxFolderId = receiver1Inbox?.id ?: fail("inbox folder unexpectedly null")

            val listFoldersInput2 = ListEmailFoldersForEmailAddressIdInput(receiver2.id)
            val receiver2Inbox =
                emailClient.listEmailFoldersForEmailAddressId(listFoldersInput2).items.find { it.folderName == "INBOX" }
            receiver2Inbox shouldNotBe null
            val receiver2InboxFolderId = receiver2Inbox?.id ?: fail("inbox folder unexpectedly null")

            // Block the sender for receiver1
            val result =
                emailClient.blockEmailAddresses(
                    listOf(
                        sender.emailAddress,
                    ),
                    emailAddressId = receiver1.id,
                )
            result.status shouldBe BatchOperationStatus.SUCCESS

            // Send a message
            sendEmailMessage(
                emailClient,
                fromAddress = sender,
                toAddresses =
                    listOf(
                        EmailMessage.EmailAddress(emailAddress = receiver1.emailAddress),
                        EmailMessage.EmailAddress(emailAddress = receiver2.emailAddress),
                    ),
            )

            waitForMessagesByFolder(
                count = 1,
                listInput =
                    ListEmailMessagesForEmailFolderIdInput(
                        folderId = receiver2InboxFolderId,
                    ),
                atMost = Duration.TWO_MINUTES,
            )

            waitForMessagesByFolder(
                count = 0,
                listInput =
                    ListEmailMessagesForEmailFolderIdInput(
                        folderId = receiver1InboxFolderId,
                    ),
                atMost = Duration.TWO_MINUTES,
            )
        }

    @Test
    fun blockingByDomainShouldBlockAllAddressesWithThatDomain() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val receiver = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
            receiver shouldNotBe null
            emailAddressList.add(receiver)

            val sender1 = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender1-${UUID.randomUUID()}")
            sender1 shouldNotBe null
            emailAddressList.add(sender1)

            val sender2 = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender2-${UUID.randomUUID()}")
            sender2 shouldNotBe null
            emailAddressList.add(sender2)

            val listFoldersInput = ListEmailFoldersForEmailAddressIdInput(receiver.id)
            val inboxFolder =
                emailClient.listEmailFoldersForEmailAddressId(listFoldersInput).items.find { it.folderName == "INBOX" }
            inboxFolder shouldNotBe null
            val inboxFolderId = inboxFolder?.id ?: fail("inbox folder unexpectedly null")

            val result =
                emailClient.blockEmailAddresses(
                    listOf(
                        sender1.emailAddress,
                    ),
                    level = com.sudoplatform.sudoemail.types.BlockedEmailAddressLevel.DOMAIN,
                )
            result.status shouldBe BatchOperationStatus.SUCCESS

            // Send a message from sender2
            sendEmailMessage(
                emailClient,
                fromAddress = sender2,
                toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = receiver.emailAddress)),
                body = "This message should not get through ${UUID.randomUUID()}",
            )

            waitForMessagesByFolder(
                atMost = Duration.TWO_MINUTES,
                pollInterval = Duration.ONE_MINUTE,
                count = 0,
                listInput =
                    ListEmailMessagesForEmailFolderIdInput(
                        folderId = inboxFolderId,
                    ),
            )
        }
}
