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
import com.sudoplatform.sudoemail.types.EmailMask
import com.sudoplatform.sudoemail.types.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.types.inputs.CancelScheduledDraftMessageInput
import com.sudoplatform.sudoemail.types.inputs.DeprovisionEmailMaskInput
import com.sudoplatform.sudoemail.types.inputs.ListScheduledDraftMessagesForEmailAddressIdInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.runner.RunWith
import kotlin.test.Test

/**
 * Test the operation of [SudoEmailClient.cancelScheduledDraftMessage].
 */
@RunWith(AndroidJUnit4::class)
class CancelScheduledDraftMessageIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

    // Shared mask test resources (only populated when masks are enabled)
    private var maskDomains: List<String> = emptyList()
    private var masksEnabled: Boolean = false

    @Before
    fun setup() =
        runTest {
            sudoClient.reset()
            sudoClient.generateEncryptionKey()

            val config = emailClient.getConfigurationData()
            masksEnabled = config.emailMasksEnabled
            if (masksEnabled) {
                maskDomains = getMaskDomains(emailClient)
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
    fun cancelScheduledDraftMessageShouldFailWithInvalidEmailAddressId() =
        runTest {
            val input =
                CancelScheduledDraftMessageInput(
                    id = "dummyId",
                    emailAddressId = "dummyEmailAddressId",
                )

            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                emailClient.cancelScheduledDraftMessage(input)
            }
        }

    @Test
    fun cancelScheduledDraftMessageShouldSucceedWithInvalidDraftId() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)
            val draftId = "dummyDraftId"

            val input =
                CancelScheduledDraftMessageInput(
                    id = draftId,
                    emailAddressId = emailAddress.id,
                )

            val response = emailClient.cancelScheduledDraftMessage(input)

            response shouldNotBe null
            response shouldBe draftId
        }

    @Test
    fun cancelScheduledDraftMessageShouldReturnDraftIdOnSuccess() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val draftId = scheduleSendDraftMessage(emailAddress)

            val input =
                CancelScheduledDraftMessageInput(
                    id = draftId,
                    emailAddressId = emailAddress.id,
                )

            val response = emailClient.cancelScheduledDraftMessage(input)

            response shouldNotBe null
            response shouldBe draftId
        }

    // -------------------------------------------------------------------------
    // Mask-related tests – skipped when masks are not enabled
    // -------------------------------------------------------------------------

    @Test
    fun cancelScheduledDraftMessageWithMaskShouldReturnDraftIdOnSuccess() =
        runTest {
            Assume.assumeTrue("Test skipped because email masks are not enabled.", masksEnabled)

            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"
            val mask = provisionEmailMask(maskAddress, emailAddress.emailAddress, ownershipProof)

            // Schedule with the mask associated
            val draftId = scheduleSendDraftMessage(emailAddress, emailMaskId = mask.id)

            val input =
                CancelScheduledDraftMessageInput(
                    id = draftId,
                    emailAddressId = emailAddress.id,
                    emailMaskId = mask.id,
                )

            val response = emailClient.cancelScheduledDraftMessage(input)

            response shouldNotBe null
            response shouldBe draftId

            // Verify the scheduled draft is now cancelled
            val listResult =
                emailClient.listScheduledDraftMessagesForEmailAddressId(
                    ListScheduledDraftMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                    ),
                )
            val cancelledDraft = listResult.items.find { it.id == draftId }
            cancelledDraft shouldNotBe null
            cancelledDraft!!.state shouldBe ScheduledDraftMessageState.CANCELLED
            cancelledDraft.emailMaskId shouldBe mask.id
        }

    @Test
    fun cancelScheduledDraftMessageWithMaskShouldSucceedWithoutMaskIdInCancelInput() =
        runTest {
            Assume.assumeTrue("Test skipped because email masks are not enabled.", masksEnabled)

            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"
            val mask = provisionEmailMask(maskAddress, emailAddress.emailAddress, ownershipProof)

            // Schedule with the mask associated
            val draftId = scheduleSendDraftMessage(emailAddress, emailMaskId = mask.id)

            // Cancel without specifying the emailMaskId – should still succeed
            val input =
                CancelScheduledDraftMessageInput(
                    id = draftId,
                    emailAddressId = emailAddress.id,
                )

            val response = emailClient.cancelScheduledDraftMessage(input)

            response shouldNotBe null
            response shouldBe draftId
        }
}
