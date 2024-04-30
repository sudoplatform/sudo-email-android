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
import com.sudoplatform.sudoemail.types.Direction
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailMessagesInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.awaitility.kotlin.has
import org.awaitility.kotlin.untilCallTo
import org.awaitility.kotlin.withPollInterval
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.updateEmailMessages].
 */
@RunWith(AndroidJUnit4::class)
class UpdateEmailMessagesIntegrationTest : BaseIntegrationTest() {
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
    fun updateEmailMessagesShouldReturnSuccessForUpdatingSingleMessage() = runBlocking {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val sendResult = sendEmailMessage(emailClient, emailAddress)
        sendResult.id.isBlank() shouldBe false

        // Wait for all the messages to arrive
        await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
            runBlocking {
                val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                )
                emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
            }
        } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == 2 }

        val sentFolder = getFolderByName(emailClient, emailAddress.id, "SENT")
            ?: fail("EmailFolder could not be found")

        val retrievedEmailMessage = emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
            ?: fail("Email message could not be found")
        with(retrievedEmailMessage) {
            folderId shouldBe sentFolder.id
            seen shouldBe true
        }

        val trashFolder = getFolderByName(emailClient, emailAddress.id, "TRASH")
            ?: fail("EmailFolder could not be found")

        val input = UpdateEmailMessagesInput(
            listOf(sendResult.id),
            UpdateEmailMessagesInput.UpdatableValues(trashFolder.id, false),
        )
        when (val result = emailClient.updateEmailMessages(input)) {
            is BatchOperationResult.SuccessOrFailureResult -> {
                result.status shouldBe BatchOperationStatus.SUCCESS
            }

            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }

        val updatedEmailMessage = emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
            ?: fail("Email message could not be found")
        with(updatedEmailMessage) {
            folderId shouldBe trashFolder.id
            seen shouldBe false
        }
    }

    @Test
    fun updateEmailMessagesShouldReturnSuccessForUpdatingMultipleMessages() = runBlocking {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val messageCount = 2
        val sentEmailIds = mutableListOf<String>()
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
            sentEmailIds.add(result.id)
        }
        sentEmailIds.size shouldBeGreaterThan 0

        // Wait for all the messages to arrive
        await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
            runBlocking {
                val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                )
                emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
            }
        } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == messageCount * 2 }

        val trashFolder = getFolderByName(emailClient, emailAddress.id, "TRASH")
            ?: fail("EmailFolder could not be found")

        val input = UpdateEmailMessagesInput(
            sentEmailIds,
            UpdateEmailMessagesInput.UpdatableValues(trashFolder.id, false),
        )
        when (val result = emailClient.updateEmailMessages(input)) {
            is BatchOperationResult.SuccessOrFailureResult -> {
                result.status shouldBe BatchOperationStatus.SUCCESS
            }

            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }

        val sentFolder = getFolderByName(emailClient, emailAddress.id, "SENT")
            ?: fail("EmailFolder could not be found")

        val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
            emailAddressId = emailAddress.id,
        )
        val listEmailMessages =
            emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
        val outbound =
            (listEmailMessages as ListAPIResult.Success<EmailMessage>).result.items.filter { it.direction == Direction.OUTBOUND }
        with(outbound[0]) {
            folderId shouldBe trashFolder.id
            seen shouldBe false
            previousFolderId shouldBe sentFolder.id
        }
        with(outbound[1]) {
            folderId shouldBe trashFolder.id
            seen shouldBe false
            previousFolderId shouldBe sentFolder.id
        }
    }

    @Test
    fun updateEmailMessagesShouldKeepPreviousFolderIdUnchangedWhenMovingToSameFolder() =
        runBlocking {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false

            // Wait for all the messages to arrive
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                    )
                    emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == 2 }

            val sentFolder = getFolderByName(emailClient, emailAddress.id, "SENT")
                ?: fail("EmailFolder could not be found")

            val retrievedEmailMessage = emailClient.getEmailMessage(GetEmailMessageInput(result.id))
                ?: fail("Email message could not be found")
            with(retrievedEmailMessage) {
                folderId shouldBe sentFolder.id
                seen shouldBe true
                previousFolderId shouldBe null
            }

            val input = UpdateEmailMessagesInput(
                listOf(result.id),
                UpdateEmailMessagesInput.UpdatableValues(sentFolder.id, false),
            )
            when (val result = emailClient.updateEmailMessages(input)) {
                is BatchOperationResult.SuccessOrFailureResult -> {
                    result.status shouldBe BatchOperationStatus.SUCCESS
                }

                else -> {
                    fail("Unexpected BatchOperationResult")
                }
            }

            val updatedEmailMessage = emailClient.getEmailMessage(GetEmailMessageInput(result.id))
                ?: fail("Email message could not be found")
            with(updatedEmailMessage) {
                folderId shouldBe sentFolder.id
                seen shouldBe false
                previousFolderId shouldBe null
            }
        }

    @Test
    fun updateEmailMessagesShouldReturnPartialResultWhenUpdatingExistingAndNonExistingMessages() =
        runBlocking {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val messageCount = 2
            val sentEmailIds = mutableSetOf<String>()
            for (i in 0 until messageCount) {
                val result = sendEmailMessage(emailClient, emailAddress)
                result.id.isBlank() shouldBe false
                sentEmailIds.add(result.id)
            }
            sentEmailIds.size shouldBeGreaterThan 0

            // Wait for all the messages to arrive
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                    )
                    emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == messageCount * 2 }

            val nonExistentIds = listOf("nonExistentId")
            val emailIdList = mutableListOf<String>()
            emailIdList.addAll(sentEmailIds)
            emailIdList.addAll(nonExistentIds)

            val trashFolder = getFolderByName(emailClient, emailAddress.id, "TRASH")
                ?: fail("EmailFolder could not be found")

            val input = UpdateEmailMessagesInput(
                emailIdList,
                UpdateEmailMessagesInput.UpdatableValues(trashFolder.id, false),
            )
            when (val result = emailClient.updateEmailMessages(input)) {
                is BatchOperationResult.PartialResult -> {
                    result.status shouldBe BatchOperationStatus.PARTIAL
                    result.successValues shouldContainExactlyInAnyOrder sentEmailIds
                    result.failureValues shouldContainExactlyInAnyOrder nonExistentIds
                }

                else -> {
                    fail("Unexpected BatchOperationResult")
                }
            }
        }

    @Test
    fun updateEmailMessagesShouldReturnFailureWhenUpdatingMultipleNonExistentMessages() =
        runBlocking {
            val input = UpdateEmailMessagesInput(
                listOf("id1", "id2", "id3"),
                UpdateEmailMessagesInput.UpdatableValues("folderId", false),
            )
            when (val result = emailClient.updateEmailMessages(input)) {
                is BatchOperationResult.SuccessOrFailureResult -> {
                    result.status shouldBe BatchOperationStatus.FAILURE
                }

                else -> {
                    fail("Unexpected BatchOperationResult")
                }
            }
        }

    @Test
    fun updateEmailMessagesShouldThrowWithInvalidArgumentWithEmptyInput() = runBlocking<Unit> {
        val input = UpdateEmailMessagesInput(
            emptyList(),
            UpdateEmailMessagesInput.UpdatableValues("TRASH", true),
        )
        shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
            emailClient.updateEmailMessages(input)
        }
    }

    @Test
    fun updateEmailMessagesShouldAllowInputLimitEdgeCase() = runBlocking {
        val inputIds = Array(100) { it.toString() }.toList()
        val input = UpdateEmailMessagesInput(
            inputIds,
            UpdateEmailMessagesInput.UpdatableValues("TRASH", true),
        )
        when (val result = emailClient.updateEmailMessages(input)) {
            is BatchOperationResult.SuccessOrFailureResult -> {
                result.status shouldBe BatchOperationStatus.FAILURE
            }

            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }
    }

    @Test
    fun updateEmailMessagesShouldThrowWhenInputLimitExceeded() = runBlocking<Unit> {
        val inputIds = Array(101) { it.toString() }.toList()
        val input = UpdateEmailMessagesInput(
            inputIds,
            UpdateEmailMessagesInput.UpdatableValues("TRASH", true),
        )
        shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
            emailClient.updateEmailMessages(input)
        }
    }
}
