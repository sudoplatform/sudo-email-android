/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailFolder
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.CreateCustomEmailFolderInput
import com.sudoplatform.sudoemail.types.inputs.DeleteCustomEmailFolderInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailMessagesInput
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.fail
import kotlin.time.Duration

/**
 * Test the operation of [SudoEmailClient.deleteCustomEmailFolder].
 */
@RunWith(AndroidJUnit4::class)
class DeleteCustomEmailFolderIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()

    @After
    fun teardown() =
        runTest {
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        }

    private suspend fun createCustomEmailFolder(
        emailAddressId: String,
        emailFolderName: String,
    ): EmailFolder {
        val input = CreateCustomEmailFolderInput(emailAddressId, emailFolderName)

        val result = emailClient.createCustomEmailFolder(input)
        return result
    }

    @Test
    fun deleteCustomEmailFolderShouldReturnNullForUnknownFolder() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = DeleteCustomEmailFolderInput("bad-id", emailAddress.id)

            val result = emailClient.deleteCustomEmailFolder(input)
            result shouldBe null
        }

    @Test
    fun deleteCustomEmailFolderShouldReturnDeletedFolderOnSuccess() =
        runTest {
            val customFolderName = "TEST"
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val customFolder = createCustomEmailFolder(emailAddress.id, customFolderName)

            val input = DeleteCustomEmailFolderInput(customFolder.id, emailAddress.id)
            val result = emailClient.deleteCustomEmailFolder(input)
            result shouldNotBe null
            result!!.id shouldStartWith emailAddress.id
            result.customFolderName shouldBe "TEST"
        }

    @Test
    fun deleteCustomEmailFolderShouldMoveMessagesToTrashOnFolderDeletion() =
        runTest(timeout = Duration.parse("3m")) {
            val customFolderName = "TEST"
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val sendResult =
                sendEmailMessage(
                    emailClient,
                    emailAddress,
                )

            sendResult shouldNotBe null

            val customFolder = createCustomEmailFolder(emailAddress.id, customFolderName)

            // Wait to make sure message has been sent and processed before trying to update
            waitForMessage(sendResult.id)

            val updateResult =
                emailClient.updateEmailMessages(
                    UpdateEmailMessagesInput(
                        listOf(sendResult.id),
                        UpdateEmailMessagesInput.UpdatableValues(customFolder.id),
                    ),
                )

            updateResult shouldNotBe null
            when (updateResult.status) {
                BatchOperationStatus.SUCCESS -> {
                    updateResult.successValues?.size shouldBe 1
                    updateResult.successValues?.get(0)?.id shouldBe sendResult.id
                }

                else -> {
                    fail("Unexpected failure to update message: ${updateResult.failureValues?.get(0)?.errorType}")
                }
            }

            val listCustomFolderMessages =
                waitForMessagesByFolder(
                    1,
                    ListEmailMessagesForEmailFolderIdInput(customFolder.id),
                    org.awaitility.Duration.ONE_MINUTE,
                    traceId = "After moving to custom folder, waiting for messages to appear in custom folder",
                )
            when (
                listCustomFolderMessages
            ) {
                is ListAPIResult.Success -> {
                    listCustomFolderMessages.result.items shouldHaveSize 1
                    listCustomFolderMessages.result.items[0].id shouldBe sendResult.id
                    listCustomFolderMessages.result.items[0].folderId shouldBe customFolder.id
                }

                is ListAPIResult.Partial -> {
                    fail("Unexpected failure to list messages")
                }
            }

            val input = DeleteCustomEmailFolderInput(customFolder.id, emailAddress.id)
            val result = emailClient.deleteCustomEmailFolder(input)
            result shouldNotBe null
            result!!.id shouldStartWith emailAddress.id
            result.customFolderName shouldBe "TEST"

            val trashFolder = getFolderByName(emailClient, emailAddress.id, "TRASH")

            trashFolder shouldNotBe null

            val listTrashFolderMessages =
                waitForMessagesByFolder(
                    1,
                    ListEmailMessagesForEmailFolderIdInput(trashFolder!!.id),
                    org.awaitility.Duration.ONE_MINUTE,
                    traceId = "After deleting waiting for messages to appear in trash folder",
                )
            when (
                listTrashFolderMessages
            ) {
                is ListAPIResult.Success -> {
                    listTrashFolderMessages.result.items shouldHaveSize 1
                    listTrashFolderMessages.result.items[0].id shouldBe sendResult.id
                    listTrashFolderMessages.result.items[0].folderId shouldBe trashFolder.id
                }

                is ListAPIResult.Partial -> {
                    fail("Unexpected failure to list messages")
                }
            }
        }
}
