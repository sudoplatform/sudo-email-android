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
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailFolder
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.CreateCustomEmailFolderInput
import com.sudoplatform.sudoemail.types.inputs.DeleteCustomEmailFolderInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailMessagesInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.fail

/**
 * Test the operation of [SudoEmailClient.deleteCustomEmailFolder].
 */
@RunWith(AndroidJUnit4::class)
class DeleteCustomEmailFolderIntegrationTest : BaseIntegrationTest() {
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

    private suspend fun createCustomEmailFolder(emailAddressId: String, emailFolderName: String): EmailFolder {
        val input = CreateCustomEmailFolderInput(emailAddressId, emailFolderName)

        val result = emailClient.createCustomEmailFolder(input)
        return result
    }

    @Test
    fun deleteCustomEmailFolderShouldReturnNullForUnknownFolder() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = DeleteCustomEmailFolderInput("bad-id", emailAddress.id)

        val result = emailClient.deleteCustomEmailFolder(input)
        result shouldBe null
    }

    @Test
    fun deleteCustomEmailFolderShouldReturnDeletedFolderOnSuccess() = runTest {
        val customFolderName = "TEST"
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

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
    fun deleteCustomEmailFolderShouldMoveMessagesToTrashOnFolderDeletion() = runTest {
        val customFolderName = "TEST"
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val customFolder = createCustomEmailFolder(emailAddress.id, customFolderName)

        val sendResult = sendEmailMessage(
            emailClient,
            emailAddress,

        )

        sendResult shouldNotBe null

        val updateResult = emailClient.updateEmailMessages(
            UpdateEmailMessagesInput(
                listOf(sendResult.id),
                UpdateEmailMessagesInput.UpdatableValues(customFolder.id),
            ),
        )

        updateResult shouldNotBe null

        when (
            val listEmailMessages = emailClient.listEmailMessagesForEmailFolderId(
                ListEmailMessagesForEmailFolderIdInput(customFolder.id),
            )
        ) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items shouldHaveSize 1
                listEmailMessages.result.items[0].id shouldBe sendResult.id
                listEmailMessages.result.items[0].folderId shouldBe customFolder.id
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

        when (
            val listEmailMessages = emailClient.listEmailMessagesForEmailFolderId(
                ListEmailMessagesForEmailFolderIdInput(trashFolder!!.id),
            )
        ) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items shouldHaveSize 1
                listEmailMessages.result.items[0].id shouldBe sendResult.id
                listEmailMessages.result.items[0].folderId shouldBe trashFolder.id
            }

            is ListAPIResult.Partial -> {
                fail("Unexpected failure to list messages")
            }
        }
    }
}
