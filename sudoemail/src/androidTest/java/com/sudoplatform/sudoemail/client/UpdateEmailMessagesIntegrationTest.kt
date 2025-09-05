/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.TestData
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
import io.kotlintest.inspectors.forAll
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
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
    fun teardown() =
        runTest {
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
            sudoList.map { sudoClient.deleteSudo(it) }
            sudoClient.reset()
        }

    @Test
    fun updateEmailMessagesShouldReturnSuccessForUpdatingSingleMessage() =
        runTest {
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

            waitForMessages(1)

            val sentFolder =
                getFolderByName(emailClient, emailAddress.id, "SENT")
                    ?: fail("EmailFolder could not be found")

            val retrievedEmailMessage =
                emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
                    ?: fail("Email message could not be found")
            with(retrievedEmailMessage) {
                folderId shouldBe sentFolder.id
                seen shouldBe true
            }

            val trashFolder =
                getFolderByName(emailClient, emailAddress.id, "TRASH")
                    ?: fail("EmailFolder could not be found")

            val input =
                UpdateEmailMessagesInput(
                    listOf(sendResult.id),
                    UpdateEmailMessagesInput.UpdatableValues(trashFolder.id, false),
                )
            val result = emailClient.updateEmailMessages(input)
            result.status shouldBe BatchOperationStatus.SUCCESS
            result.successValues?.first()?.id shouldBe sendResult.id
            result.failureValues?.isEmpty() shouldBe true

            val updatedEmailMessage =
                emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
                    ?: fail("Email message could not be found")
            with(updatedEmailMessage) {
                folderId shouldBe trashFolder.id
                seen shouldBe false
            }
        }

    @Test
    fun updateEmailMessagesShouldReturnSuccessForUpdatingMultipleMessages() =
        runTest {
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

            waitForMessages(messageCount)

            val trashFolder =
                getFolderByName(emailClient, emailAddress.id, "TRASH")
                    ?: fail("EmailFolder could not be found")

            val input =
                UpdateEmailMessagesInput(
                    sentEmailIds,
                    UpdateEmailMessagesInput.UpdatableValues(trashFolder.id, false),
                )
            val result = emailClient.updateEmailMessages(input)
            result.status shouldBe BatchOperationStatus.SUCCESS
            result.successValues?.size shouldBe 2
            result.failureValues?.isEmpty() shouldBe true

            val sentFolder =
                getFolderByName(emailClient, emailAddress.id, "SENT")
                    ?: fail("EmailFolder could not be found")

            val listEmailMessagesInput =
                ListEmailMessagesForEmailAddressIdInput(
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
        runTest {
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

            waitForMessages(1)

            val sentFolder =
                getFolderByName(emailClient, emailAddress.id, "SENT")
                    ?: fail("EmailFolder could not be found")

            val retrievedEmailMessage =
                emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
                    ?: fail("Email message could not be found")
            with(retrievedEmailMessage) {
                folderId shouldBe sentFolder.id
                seen shouldBe true
                previousFolderId shouldBe null
            }

            val input =
                UpdateEmailMessagesInput(
                    listOf(sendResult.id),
                    UpdateEmailMessagesInput.UpdatableValues(sentFolder.id, false),
                )
            val updateResult = emailClient.updateEmailMessages(input)
            updateResult.status shouldBe BatchOperationStatus.SUCCESS

            val updatedEmailMessage =
                emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
                    ?: fail("Email message could not be found")
            with(updatedEmailMessage) {
                folderId shouldBe sentFolder.id
                seen shouldBe false
                previousFolderId shouldBe null
            }
        }

    @Test
    fun updateEmailMessagesShouldReturnPartialResultWhenUpdatingExistingAndNonExistingMessages() =
        runTest {
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

            waitForMessages(messageCount)

            val nonExistentIds = listOf("nonExistentId")
            val emailIdList = mutableListOf<String>()
            emailIdList.addAll(sentEmailIds)
            emailIdList.addAll(nonExistentIds)

            val trashFolder =
                getFolderByName(emailClient, emailAddress.id, "TRASH")
                    ?: fail("EmailFolder could not be found")

            val input =
                UpdateEmailMessagesInput(
                    emailIdList,
                    UpdateEmailMessagesInput.UpdatableValues(trashFolder.id, false),
                )
            val result = emailClient.updateEmailMessages(input)
            result.status shouldBe BatchOperationStatus.PARTIAL
            result.successValues?.size shouldBe sentEmailIds.size
            result.successValues?.forAll {
                sentEmailIds.shouldContain(it.id)
            }
            result.failureValues?.size shouldBe nonExistentIds.size
            result.failureValues?.forAll {
                nonExistentIds.shouldContain(it.id)
            }
        }

    @Test
    fun updateEmailMessagesShouldReturnFailureWhenUpdatingMultipleNonExistentMessages() =
        runTest {
            val input =
                UpdateEmailMessagesInput(
                    listOf("id1", "id2", "id3"),
                    UpdateEmailMessagesInput.UpdatableValues("folderId", false),
                )
            val result = emailClient.updateEmailMessages(input)
            result.status shouldBe BatchOperationStatus.FAILURE
        }

    @Test
    fun updateEmailMessagesShouldThrowWithInvalidArgumentWithEmptyInput() =
        runTest {
            val input =
                UpdateEmailMessagesInput(
                    emptyList(),
                    UpdateEmailMessagesInput.UpdatableValues("TRASH", true),
                )
            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                emailClient.updateEmailMessages(input)
            }
        }

    @Test
    fun updateEmailMessagesShouldAllowInputLimitEdgeCase() =
        runTest {
            val (updateEmailMessagesLimit) = emailClient.getConfigurationData()
            val inputIds = Array(updateEmailMessagesLimit) { it.toString() }.toList()
            val input =
                UpdateEmailMessagesInput(
                    inputIds,
                    UpdateEmailMessagesInput.UpdatableValues("TRASH", true),
                )
            val result = emailClient.updateEmailMessages(input)
            result.status shouldBe BatchOperationStatus.FAILURE
        }

    @Test
    fun updateEmailMessagesShouldThrowWhenInputLimitExceeded() =
        runTest {
            val (updateEmailMessagesLimit) = emailClient.getConfigurationData()
            val inputIds = Array(updateEmailMessagesLimit + 1) { it.toString() }.toList()
            val input =
                UpdateEmailMessagesInput(
                    inputIds,
                    UpdateEmailMessagesInput.UpdatableValues("TRASH", true),
                )
            shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
                emailClient.updateEmailMessages(input)
            }
        }
}
