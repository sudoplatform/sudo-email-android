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
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.inputs.CreateCustomEmailFolderInput
import com.sudoplatform.sudoemail.types.inputs.DeleteMessagesForFolderIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailMessagesInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.time.Duration

/**
 * Test the operation of [SudoEmailClient.deleteMessagesForFolderId].
 */
@RunWith(AndroidJUnit4::class)
class DeleteMessageForFolderIdIntegrationTest : BaseIntegrationTest() {
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
    fun deleteMessagesForFolderIdShouldFailIfGivenInvalidEmailAddressId() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)
            val input =
                DeleteMessagesForFolderIdInput(
                    emailAddressId = UUID.randomUUID().toString(),
                    emailFolderId = emailAddress.folders.first().id,
                )

            shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
                emailClient.deleteMessagesForFolderId(input)
            }
        }

    @Test
    fun deleteMessagesForFolderIdShouldFailIfGivenInvalidEmailFolderId() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)
            val input =
                DeleteMessagesForFolderIdInput(
                    emailAddressId = emailAddress.id,
                    emailFolderId = UUID.randomUUID().toString(),
                )

            shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
                emailClient.deleteMessagesForFolderId(input)
            }
        }

    @Test
    fun deleteMessagesForFolderIdShouldDeleteASingleMessage() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)
            val sendResult =
                sendEmailMessage(
                    emailClient,
                    fromAddress = emailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = emailAddress.emailAddress)),
                )

            sendResult shouldNotBe null

            val inboxFolder =
                getFolderByName(
                    client = emailClient,
                    emailAddressId = emailAddress.id,
                    folderName = "INBOX",
                ) ?: fail("EmailFolder could not be found")

            waitForMessagesByFolder(
                1,
                ListEmailMessagesForEmailFolderIdInput(
                    folderId = inboxFolder.id,
                ),
            )

            val result =
                emailClient.deleteMessagesForFolderId(
                    DeleteMessagesForFolderIdInput(
                        emailFolderId = inboxFolder.id,
                        emailAddressId = emailAddress.id,
                    ),
                )

            result shouldBe inboxFolder.id

            waitForMessagesByFolder(
                0,
                ListEmailMessagesForEmailFolderIdInput(
                    folderId = inboxFolder.id,
                ),
            )
        }

    @Test
    fun deleteMessagesForFolderIdShouldDeleteMultipleMessages() =
        runTest(timeout = Duration.parse("2m")) {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val messageCount = 10
            val sentEmailIds = mutableSetOf<String>()
            for (i in 0 until messageCount) {
                val result = sendEmailMessage(emailClient, emailAddress)
                result.id.isBlank() shouldBe false
                sentEmailIds.add(result.id)
            }
            sentEmailIds.size shouldBeGreaterThan 0

            val sentFolder =
                getFolderByName(
                    client = emailClient,
                    emailAddressId = emailAddress.id,
                    folderName = "SENT",
                ) ?: fail("EmailFolder could not be found")

            waitForMessagesByFolder(
                messageCount,
                ListEmailMessagesForEmailFolderIdInput(
                    folderId = sentFolder.id,
                ),
            )

            val result =
                emailClient.deleteMessagesForFolderId(
                    DeleteMessagesForFolderIdInput(
                        emailFolderId = sentFolder.id,
                        emailAddressId = emailAddress.id,
                    ),
                )

            result shouldBe sentFolder.id

            waitForMessagesByFolder(
                0,
                ListEmailMessagesForEmailFolderIdInput(
                    folderId = sentFolder.id,
                ),
            )
        }

    @Test
    fun deleteMessagesForFolderIdMovesMessagesToTrashWithHardDeleteFalse() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)
            val sendResult =
                sendEmailMessage(
                    emailClient,
                    fromAddress = emailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = emailAddress.emailAddress)),
                )

            sendResult shouldNotBe null

            val inboxFolder =
                getFolderByName(
                    client = emailClient,
                    emailAddressId = emailAddress.id,
                    folderName = "INBOX",
                ) ?: fail("EmailFolder could not be found")

            waitForMessagesByFolder(
                1,
                ListEmailMessagesForEmailFolderIdInput(
                    folderId = inboxFolder.id,
                ),
            )

            val trashFolder =
                getFolderByName(
                    client = emailClient,
                    emailAddressId = emailAddress.id,
                    folderName = "TRASH",
                ) ?: fail("EmailFolder could not be found")

            val result =
                emailClient.deleteMessagesForFolderId(
                    DeleteMessagesForFolderIdInput(
                        emailFolderId = inboxFolder.id,
                        emailAddressId = emailAddress.id,
                        hardDelete = false,
                    ),
                )

            result shouldBe inboxFolder.id

            waitForMessagesByFolder(
                0,
                ListEmailMessagesForEmailFolderIdInput(
                    folderId = inboxFolder.id,
                ),
            )

            waitForMessagesByFolder(
                1,
                ListEmailMessagesForEmailFolderIdInput(
                    folderId = trashFolder.id,
                ),
            )
        }

    @Test
    fun deleteMessagesForFolderIdAlwaysHardDeletesFromTrash() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)
            val sendResult =
                sendEmailMessage(
                    emailClient,
                    fromAddress = emailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = emailAddress.emailAddress)),
                )

            sendResult shouldNotBe null

            val inboxFolder =
                getFolderByName(
                    client = emailClient,
                    emailAddressId = emailAddress.id,
                    folderName = "INBOX",
                ) ?: fail("EmailFolder could not be found")

            waitForMessagesByFolder(
                1,
                ListEmailMessagesForEmailFolderIdInput(
                    folderId = inboxFolder.id,
                ),
            )

            val trashFolder =
                getFolderByName(
                    client = emailClient,
                    emailAddressId = emailAddress.id,
                    folderName = "TRASH",
                ) ?: fail("EmailFolder could not be found")

            emailClient.updateEmailMessages(
                UpdateEmailMessagesInput(
                    ids = listOf(sendResult.id),
                    values = UpdateEmailMessagesInput.UpdatableValues(folderId = trashFolder.id),
                ),
            )

            waitForMessagesByFolder(
                1,
                ListEmailMessagesForEmailFolderIdInput(
                    folderId = trashFolder.id,
                ),
            )

            val result =
                emailClient.deleteMessagesForFolderId(
                    DeleteMessagesForFolderIdInput(
                        emailFolderId = trashFolder.id,
                        emailAddressId = emailAddress.id,
                        hardDelete = false,
                    ),
                )
            result shouldBe trashFolder.id

            waitForMessagesByFolder(
                0,
                ListEmailMessagesForEmailFolderIdInput(
                    folderId = trashFolder.id,
                ),
            )
        }

    @Test
    fun deleteMessagesForFolderIdWorksWithCustomFolders() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)
            val sendResult =
                sendEmailMessage(
                    emailClient,
                    fromAddress = emailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = emailAddress.emailAddress)),
                )

            sendResult shouldNotBe null

            val inboxFolder =
                getFolderByName(
                    client = emailClient,
                    emailAddressId = emailAddress.id,
                    folderName = "INBOX",
                ) ?: fail("EmailFolder could not be found")

            waitForMessagesByFolder(
                1,
                ListEmailMessagesForEmailFolderIdInput(
                    folderId = inboxFolder.id,
                ),
            )

            val customFolder =
                emailClient.createCustomEmailFolder(
                    CreateCustomEmailFolderInput(emailAddress.id, "TEST"),
                )

            emailClient.updateEmailMessages(
                UpdateEmailMessagesInput(
                    ids = listOf(sendResult.id),
                    values = UpdateEmailMessagesInput.UpdatableValues(folderId = customFolder.id),
                ),
            )

            waitForMessagesByFolder(
                1,
                ListEmailMessagesForEmailFolderIdInput(
                    folderId = customFolder.id,
                ),
            )

            val result =
                emailClient.deleteMessagesForFolderId(
                    DeleteMessagesForFolderIdInput(
                        emailFolderId = customFolder.id,
                        emailAddressId = emailAddress.id,
                        hardDelete = false,
                    ),
                )
            result shouldBe customFolder.id

            waitForMessagesByFolder(
                0,
                ListEmailMessagesForEmailFolderIdInput(
                    folderId = customFolder.id,
                ),
            )
        }
}
