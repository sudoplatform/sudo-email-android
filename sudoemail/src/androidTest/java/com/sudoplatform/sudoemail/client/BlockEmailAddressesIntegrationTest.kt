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
import com.sudoplatform.sudoemail.types.BatchOperationResult
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.ListEmailFoldersForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.awaitility.kotlin.has
import org.awaitility.kotlin.untilCallTo
import org.awaitility.kotlin.withPollDelay
import org.awaitility.kotlin.withPollInterval
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
    fun teardown() = runBlocking {
        emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        sudoList.map { sudoClient.deleteSudo(it) }
        sudoClient.reset()
    }

    @Test
    fun blockEmailAddressesThrowsAnErrorIfPassedAnEmptyAddressesArray() =
        runBlocking<Unit> {
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
    fun blockEmailAddressesShouldThrowAnErrorIfPassedDuplicateAddresses() = runBlocking<Unit> {
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
    fun blockEmailAddressesShouldSuccessfullyBlockASingleAddress() = runBlocking<Unit> {
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

        when (
            val result = emailClient.blockEmailAddresses(
                listOf(
                    emailAddressToBlock.emailAddress,
                ),
            )
        ) {
            is BatchOperationResult.SuccessOrFailureResult -> {
                result.status shouldBe BatchOperationStatus.SUCCESS
            }

            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }
    }

    @Test
    fun blockEmailAddressesShouldSuccessfullyBlockAMultipleAddress() = runBlocking<Unit> {
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

        when (
            val result = emailClient.blockEmailAddresses(
                listOf(
                    emailAddressToBlock.emailAddress,
                    "spammyMcSpamface${UUID.randomUUID()}@spambot.com",
                ),
            )
        ) {
            is BatchOperationResult.SuccessOrFailureResult -> {
                result.status shouldBe BatchOperationStatus.SUCCESS
            }

            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }
    }

    // This test can take a while...
    @Test
    fun messagesFromBlockedAddressesShouldNotBeReceived() = runBlocking<Unit> {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val senderReceiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
        senderReceiverEmailAddress shouldNotBe null
        emailAddressList.add(senderReceiverEmailAddress)

        val listFoldersInput = ListEmailFoldersForEmailAddressIdInput(senderReceiverEmailAddress.id)
        val inboxFolder = emailClient.listEmailFoldersForEmailAddressId(listFoldersInput).items.find { it.folderName == "INBOX" }

        // Send message while unblocked
        sendEmailMessage(
            emailClient,
            senderReceiverEmailAddress,
            body = "This message should go through & be returned as OOTO",
        )

        // Make sure message was received
        await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
            runBlocking {
                val listEmailMessagesInput = inboxFolder?.id?.let {
                    ListEmailMessagesForEmailFolderIdInput(
                        it,
                    )
                }
                if (listEmailMessagesInput != null) {
                    emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
                } else {
                    fail("Unexpected null for listEmailMessagesInput")
                }
            }
        } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == 1 }

        // Block the sender
        when (
            val result = emailClient.blockEmailAddresses(
                listOf(
                    fromSimulatorAddress,
                ),
            )
        ) {
            is BatchOperationResult.SuccessOrFailureResult -> {
                result.status shouldBe BatchOperationStatus.SUCCESS
            }

            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }

        // Send another message
        sendEmailMessage(
            emailClient,
            senderReceiverEmailAddress,
            body = "The OOTO response for this message should get blocked",
        )

        // Wait for messages to potentially arrive even though they shouldn't
        await.timeout(
            Duration.TEN_SECONDS.multiply(12), // Increase timeout from default 10 seconds
        ).withPollDelay(
            Duration.TEN_SECONDS.multiply(6), // Wait 60 seconds for first poll
        ).untilCallTo {
            runBlocking {
                val listEmailMessagesInput = inboxFolder?.id?.let {
                    ListEmailMessagesForEmailFolderIdInput(
                        it,
                        cachePolicy = CachePolicy.REMOTE_ONLY,
                    )
                }
                if (listEmailMessagesInput != null) {
                    emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
                } else {
                    fail("Unexpected null for listEmailMessagesInput")
                }
            }
            // Should still have the original message, but not latest one
        } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == 1 }

        // Unblock the sender
        when (
            val unblockResult = emailClient.unblockEmailAddresses(
                listOf(fromSimulatorAddress),
            )
        ) {
            is BatchOperationResult.SuccessOrFailureResult -> {
                unblockResult.status shouldBe BatchOperationStatus.SUCCESS
            }

            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }

        // Send one more message
        sendEmailMessage(
            emailClient,
            senderReceiverEmailAddress,
            body = "This message should also go through & be returned as OOTO",
        )

        // Check that it was received
        await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
            runBlocking {
                val listEmailMessagesInput = inboxFolder?.id?.let {
                    ListEmailMessagesForEmailFolderIdInput(
                        it,
                        cachePolicy = CachePolicy.REMOTE_ONLY,
                    )
                }
                if (listEmailMessagesInput != null) {
                    emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
                } else {
                    fail("Unexpected null for listEmailMessagesInput")
                }
            }
            // Should have original message plus last one
        } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == 2 }
    }
}
