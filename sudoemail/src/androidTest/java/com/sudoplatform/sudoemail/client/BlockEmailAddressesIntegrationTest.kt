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
import com.sudoplatform.sudoemail.types.inputs.BlockEmailAddressesInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailAddressIdInput
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

            val input = BlockEmailAddressesInput(
                receiverEmailAddress.owner,
                emptyList(),
            )
            shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                emailClient.blockEmailAddresses(input)
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

        val input = BlockEmailAddressesInput(
            receiverEmailAddress.owner,
            listOf(
                emailAddressToBlock.emailAddress.lowercase(),
                emailAddressToBlock.emailAddress.uppercase(),
            ),
        )
        shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
            emailClient.blockEmailAddresses(input)
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

        val input = BlockEmailAddressesInput(
            receiverEmailAddress.owner,
            listOf(
                emailAddressToBlock.emailAddress,
            ),
        )

        when (val result = emailClient.blockEmailAddresses(input)) {
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

        val input = BlockEmailAddressesInput(
            receiverEmailAddress.owner,
            listOf(
                emailAddressToBlock.emailAddress,
                "spammyMcSpamface${UUID.randomUUID()}@spambot.com",
            ),
        )

        when (val result = emailClient.blockEmailAddresses(input)) {
            is BatchOperationResult.SuccessOrFailureResult -> {
                result.status shouldBe BatchOperationStatus.SUCCESS
            }

            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }
    }

    @Test
    fun messagesFromBlockedAddressesShouldNotBeReceived() = runBlocking<Unit> {
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

        val input = BlockEmailAddressesInput(
            receiverEmailAddress.owner,
            listOf(
                emailAddressToBlock.emailAddress,
            ),
        )

        when (val result = emailClient.blockEmailAddresses(input)) {
            is BatchOperationResult.SuccessOrFailureResult -> {
                result.status shouldBe BatchOperationStatus.SUCCESS
            }

            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }

        sendEmailMessage(
            emailClient,
            emailAddressToBlock,
            toAddress = receiverEmailAddress.emailAddress,
            body = "This message should get blocked",
        )

        // Wait for messages to potentially arrive even though they shouldn't
        val listEmailMessages =
            await.timeout(
                Duration.TEN_SECONDS.multiply(12), // Increase timeout from default 10 seconds
            ).withPollDelay(
                Duration.TEN_SECONDS.multiply(6), // Wait 60 seconds for first poll
            ).untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                        emailAddressId = receiverEmailAddress.id,
                        CachePolicy.REMOTE_ONLY,
                    )
                    emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.isEmpty() }

        when (listEmailMessages) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.size shouldBe 0
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }
}
