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
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.PartialEmailMessage
import com.sudoplatform.sudoemail.types.inputs.ListEmailFoldersForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.awaitility.kotlin.has
import org.awaitility.kotlin.untilCallTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

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
    fun teardown() = runTest {
        emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        sudoList.map { sudoClient.deleteSudo(it) }
        sudoClient.reset()
    }

    @Test
    fun blockEmailAddressesThrowsAnErrorIfPassedAnEmptyAddressesArray() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            receiverEmailAddress shouldNotBe null
            emailAddressList.add(receiverEmailAddress)

            val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof)
            emailAddressToBlock shouldNotBe null
            emailAddressList.add(emailAddressToBlock)

            shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                emailClient.blockEmailAddresses(emptyList())
            }
        }

    @Test
    fun blockEmailAddressesShouldThrowAnErrorIfPassedDuplicateAddresses() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof)
        emailAddressToBlock shouldNotBe null
        emailAddressList.add(emailAddressToBlock)

        shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
            emailClient.blockEmailAddresses(
                listOf(
                    emailAddressToBlock.emailAddress.lowercase(),
                    emailAddressToBlock.emailAddress.uppercase(),
                ),
            )
        }
    }

    @Test
    fun blockEmailAddressesShouldSuccessfullyBlockASingleAddress() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof)
        emailAddressToBlock shouldNotBe null
        emailAddressList.add(emailAddressToBlock)

        val result = emailClient.blockEmailAddresses(
            listOf(
                emailAddressToBlock.emailAddress,
            ),
        )

        result.status shouldBe BatchOperationStatus.SUCCESS
    }

    @Test
    fun blockEmailAddressesShouldSuccessfullyBlockAMultipleAddress() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof)
        emailAddressToBlock shouldNotBe null
        emailAddressList.add(emailAddressToBlock)

        val result = emailClient.blockEmailAddresses(
            listOf(
                emailAddressToBlock.emailAddress,
                "spammyMcSpamface${UUID.randomUUID()}@spambot.com",
            ),
        )
        result.status shouldBe BatchOperationStatus.SUCCESS
    }

    // This test can take a while...
    @Test
    fun messagesFromBlockedAddressesShouldNotBeReceived() = runTest(timeout = 120.seconds) {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val senderReceiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
        senderReceiverEmailAddress shouldNotBe null
        emailAddressList.add(senderReceiverEmailAddress)

        val listFoldersInput = ListEmailFoldersForEmailAddressIdInput(senderReceiverEmailAddress.id)
        val inboxFolder =
            emailClient.listEmailFoldersForEmailAddressId(listFoldersInput).items.find { it.folderName == "INBOX" }

        // Send message while unblocked
        sendEmailMessage(
            emailClient,
            senderReceiverEmailAddress,
            body = "This message should go through & be returned as OOTO",
        )

        // Make sure message was received
        waitForMessages(
            count = 1,
            listInput = inboxFolder?.id?.let {
                ListEmailMessagesForEmailFolderIdInput(
                    it,
                )
            },
        )

        // Block the sender
        val result = emailClient.blockEmailAddresses(
            listOf(
                fromSimulatorAddress,
            ),
        )
        result.status shouldBe BatchOperationStatus.SUCCESS

        // Send another message
        sendEmailMessage(
            emailClient,
            senderReceiverEmailAddress,
            body = "The OOTO response for this message should get blocked",
        )

        // Wait for messages to potentially arrive even though they shouldn't
        // Increase timeout from default 10 seconds and wait 60 seconds for first poll
        // Should still have the original message, but not latest one
        waitForMessages(
            Duration.TWO_MINUTES,
            Duration.ONE_MINUTE,
            1,
            inboxFolder?.id?.let {
                ListEmailMessagesForEmailFolderIdInput(
                    it,
                    cachePolicy = CachePolicy.REMOTE_ONLY,
                )
            },
        )

        // Unblock the sender
        val unblockResult = emailClient.unblockEmailAddresses(
            listOf(fromSimulatorAddress),
        )
        unblockResult.status shouldBe BatchOperationStatus.SUCCESS

        // Send one more message
        sendEmailMessage(
            emailClient,
            senderReceiverEmailAddress,
            body = "This message should also go through & be returned as OOTO",
        )

        // Check that it was received, should have original message plus last one
        waitForMessages(
            count = 2,
            listInput = inboxFolder?.id?.let {
                ListEmailMessagesForEmailFolderIdInput(
                    it,
                    cachePolicy = CachePolicy.REMOTE_ONLY,
                )
            },
        )
    }

    /** Wait for multiple messages to arrive. */
    private fun waitForMessages(
        timeout: Duration = Duration.ONE_MINUTE,
        poll: Duration = Duration.TWO_HUNDRED_MILLISECONDS,
        count: Int,
        listInput: ListEmailMessagesForEmailFolderIdInput?,
    ): ListAPIResult<EmailMessage, PartialEmailMessage> {
        return await
            .atMost(timeout)
            .pollInterval(poll)
            .untilCallTo {
                runBlocking {
                    with(emailClient) {
                        if (listInput != null) {
                            listEmailMessagesForEmailFolderId(listInput)
                        } else {
                            fail("Unexpected null for listEmailMessagesInput")
                        }
                    }
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == count }
    }
}
