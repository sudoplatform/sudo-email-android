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
import com.sudoplatform.sudoemail.internal.util.DefaultEmailMessageDataProcessor
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.DeleteDraftEmailMessagesInput
import com.sudoplatform.sudoemail.types.inputs.ListDraftEmailMessageMetadataForEmailAddressIdInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.time.Duration

/**
 * Test the operation of [SudoEmailClient.deleteDraftEmailMessages].
 */
@RunWith(AndroidJUnit4::class)
class DeleteDraftEmailMessagesIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

    @Before
    fun setup() {
        runTest {
            sudoClient.reset()
            sudoClient.generateEncryptionKey()
        }
    }

    @After
    fun teardown() =
        runTest {
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
            sudoList.map { sudoClient.deleteSudo(it) }
            sudoClient.reset()
        }

    @Test
    fun deleteDraftEmailMessagesShouldThrowErrorIfSenderEmailAddressNotFound() =
        runTest {
            val mockDraftId = UUID.randomUUID()
            val mockEmailAddressId = "non-existent-email-address-id"

            val input =
                DeleteDraftEmailMessagesInput(
                    listOf(mockDraftId.toString()),
                    mockEmailAddressId,
                )
            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                emailClient.deleteDraftEmailMessages(input)
            }
        }

    @Test
    fun deleteDraftEmailMessagesShouldReturnSuccessOnNonExistentMessage() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val mockDraftId = UUID.randomUUID().toString()

            val input =
                DeleteDraftEmailMessagesInput(
                    listOf(mockDraftId),
                    emailAddress.id,
                )

            val result = emailClient.deleteDraftEmailMessages(input)
            result.status shouldBe BatchOperationStatus.SUCCESS
        }

    @Test
    fun deleteDraftEmailMessagesShouldReturnSuccessResult() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAddress.emailAddress,
                    to = listOf(emailAddress.emailAddress),
                    subject = "Test Draft",
                )

            val createDraftInput =
                CreateDraftEmailMessageInput(
                    rfc822Data = rfc822Data,
                    senderEmailAddressId = emailAddress.id,
                )

            val draftId = emailClient.createDraftEmailMessage(createDraftInput)

            val deleteDraftEmailMessagesInput =
                DeleteDraftEmailMessagesInput(
                    listOf(draftId),
                    emailAddress.id,
                )
            val result = emailClient.deleteDraftEmailMessages(deleteDraftEmailMessagesInput)
            result.status shouldBe BatchOperationStatus.SUCCESS

            val listDraftEmailMessagesResult =
                emailClient.listDraftEmailMessageMetadataForEmailAddressId(
                    ListDraftEmailMessageMetadataForEmailAddressIdInput(emailAddress.id, 1000),
                )
            listDraftEmailMessagesResult.items.find { it.id == draftId } shouldBe null
        }

    @Test
    fun deleteDraftEmailMessagesShouldSuccessfullyDeleteSingleDraftWithEmailMaskId() =
        runTest {
            val config = emailClient.getConfigurationData()
            Assume.assumeTrue("Test skipped due to masks not being enabled", config.emailMasksEnabled)

            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null
            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val maskDomains = getMaskDomains(emailClient)
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"
            val emailMask = provisionEmailMask(maskAddress, emailAddress.emailAddress, ownershipProof)

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAddress.emailAddress,
                    to = listOf(emailAddress.emailAddress),
                    subject = "Test Draft",
                )

            val createDraftInput =
                CreateDraftEmailMessageInput(
                    rfc822Data = rfc822Data,
                    senderEmailAddressId = emailAddress.id,
                    emailMaskId = emailMask.id,
                )

            val draftId = emailClient.createDraftEmailMessage(createDraftInput)

            val deleteDraftEmailMessagesInput =
                DeleteDraftEmailMessagesInput(
                    listOf(draftId),
                    emailAddress.id,
                    emailMask.id,
                )
            val result = emailClient.deleteDraftEmailMessages(deleteDraftEmailMessagesInput)
            result.status shouldBe BatchOperationStatus.SUCCESS

            val listDraftEmailMessagesResult =
                emailClient.listDraftEmailMessageMetadataForEmailAddressId(
                    ListDraftEmailMessageMetadataForEmailAddressIdInput(emailAddress.id, 1000),
                )
            listDraftEmailMessagesResult.items.find { it.id == draftId } shouldBe null
        }

    @Test
    fun deleteDraftEmailMessagesShouldDeleteMultipleMessagesAtOnce() =
        runTest(timeout = Duration.parse("2m")) {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val draftIds = mutableListOf<String>()

            (0..(3)).forEach { _ ->
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAddress.emailAddress,
                        to = listOf(emailAddress.emailAddress),
                        subject = "Test Draft",
                    )

                val createDraftInput =
                    CreateDraftEmailMessageInput(
                        rfc822Data = rfc822Data,
                        senderEmailAddressId = emailAddress.id,
                    )
                val draftId = emailClient.createDraftEmailMessage(createDraftInput)
                draftIds.add(draftId)
            }

            val input =
                DeleteDraftEmailMessagesInput(
                    draftIds,
                    emailAddress.id,
                )
            val result = emailClient.deleteDraftEmailMessages(input)
            result.status shouldBe BatchOperationStatus.SUCCESS
        }

    @Test
    fun deleteDraftEmailMessagesShouldSucceedWhenDeletingMultipleDraftsWithAMaskId() =
        runTest(timeout = Duration.parse("2m")) {
            val config = emailClient.getConfigurationData()
            Assume.assumeTrue("Test skipped due to masks not being enabled", config.emailMasksEnabled)

            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null
            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val maskDomains = getMaskDomains(emailClient)
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"
            val emailMask = provisionEmailMask(maskAddress, emailAddress.emailAddress, ownershipProof)

            val draftIds = mutableListOf<String>()

            (0..(3)).forEach { _ ->
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAddress.emailAddress,
                        to = listOf(emailAddress.emailAddress),
                        subject = "Test Draft",
                    )

                val createDraftInput =
                    CreateDraftEmailMessageInput(
                        rfc822Data = rfc822Data,
                        senderEmailAddressId = emailAddress.id,
                        emailMaskId = emailMask.id,
                    )
                val draftId = emailClient.createDraftEmailMessage(createDraftInput)
                draftIds.add(draftId)
            }

            val input =
                DeleteDraftEmailMessagesInput(
                    draftIds,
                    emailAddress.id,
                    emailMask.id,
                )
            val result = emailClient.deleteDraftEmailMessages(input)
            result.status shouldBe BatchOperationStatus.SUCCESS
        }
}
