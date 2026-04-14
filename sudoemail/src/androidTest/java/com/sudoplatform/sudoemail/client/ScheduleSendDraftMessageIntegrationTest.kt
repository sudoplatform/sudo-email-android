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
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMask
import com.sudoplatform.sudoemail.types.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.DeleteDraftEmailMessagesInput
import com.sudoplatform.sudoemail.types.inputs.DeprovisionEmailMaskInput
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.ScheduleSendDraftMessageInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.util.Date

/**
 * Test the operation of [SudoEmailClient.scheduleSendDraftMessage].
 */
@RunWith(AndroidJUnit4::class)
class ScheduleSendDraftMessageIntegrationTest : BaseIntegrationTest() {
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
            emailAddressList.forEach { emailClient.deprovisionEmailAddress(it.id) }
            sudoList.forEach { sudoClient.deleteSudo(it) }
            sudoClient.reset()
        }

    @Test
    fun scheduleSendDraftMessageShouldFailWithInvalidEmailAddressId() =
        runTest {
            val input =
                ScheduleSendDraftMessageInput(
                    "dummyId",
                    "dummyEmailAddressId",
                    emailMaskId = null,
                    Date(),
                )

            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                emailClient.scheduleSendDraftMessage(input)
            }
        }

    @Test
    fun scheduleSendDraftMessageShouldFailWithSendAtNotInFuture() =
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
                ScheduleSendDraftMessageInput(
                    "dummyId",
                    emailAddress.id,
                    null,
                    Date(),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                emailClient.scheduleSendDraftMessage(input)
            }
        }

    @Test
    fun scheduleSendDraftMessageShouldFailIfDraftMessageNotFound() =
        runTest {
            val sendAt = Date(Date().time + Duration.ofDays(1).toMillis())
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input =
                ScheduleSendDraftMessageInput(
                    "dummyId",
                    emailAddress.id,
                    null,
                    sendAt,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                emailClient.scheduleSendDraftMessage(input)
            }
        }

    @Test
    fun scheduleSendDraftMessageShouldReturnScheduledDraftMessageEntityOnSuccess() =
        runTest {
            val sendAt = Date(Date().time + Duration.ofDays(1).toMillis())
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
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
            val draftId = emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

            draftId shouldNotBe null

            val input =
                ScheduleSendDraftMessageInput(
                    draftId,
                    emailAddress.id,
                    null,
                    sendAt,
                )

            val response = emailClient.scheduleSendDraftMessage(input)

            response shouldNotBe null
            response.id shouldBe draftId
            response.emailAddressId shouldBe emailAddress.id
            response.sendAt shouldBe sendAt
            response.state shouldBe ScheduledDraftMessageState.SCHEDULED
            response.emailMaskId shouldBe null
        }

    @Test
    fun scheduleAndDeleteDraftMessageShouldSucceed() =
        runTest {
            val sendAt = Date(Date().time + Duration.ofDays(1).toMillis())
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
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
            val draftId = emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            draftId shouldNotBe null

            val input =
                ScheduleSendDraftMessageInput(
                    draftId,
                    emailAddress.id,
                    null,
                    sendAt,
                )
            val scheduledDraft = emailClient.scheduleSendDraftMessage(input)
            scheduledDraft shouldNotBe null
            scheduledDraft.id shouldBe draftId

            // Ensure that the draft message has been created/scheduled
            delay(3000)
            emailClient.deleteDraftEmailMessages(DeleteDraftEmailMessagesInput(listOf(draftId), emailAddress.id))
            // give some time for the deletion to complete
            delay(3000)
            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                emailClient.getDraftEmailMessage(GetDraftEmailMessageInput(scheduledDraft.id, emailAddress.id))
            }
        }

    // -------------------------------------------------------------------------
    // Mask-related tests – skipped when masks are not enabled
    // -------------------------------------------------------------------------

    @Test
    fun scheduleSendDraftMessageWithMaskShouldReturnEmailMaskIdInResponse() =
        runTest {
            Assume.assumeTrue("Test skipped because email masks are not enabled.", masksEnabled)

            val sendAt = Date(Date().time + Duration.ofDays(1).toMillis())
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

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAddress.emailAddress,
                    to = listOf(emailAddress.emailAddress),
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
            val draftId = emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            draftId shouldNotBe null

            val input =
                ScheduleSendDraftMessageInput(
                    id = draftId,
                    emailAddressId = emailAddress.id,
                    emailMaskId = mask.id,
                    sendAt = sendAt,
                )

            val response = emailClient.scheduleSendDraftMessage(input)

            response shouldNotBe null
            response.id shouldBe draftId
            response.emailAddressId shouldBe emailAddress.id
            response.sendAt shouldBe sendAt
            response.state shouldBe ScheduledDraftMessageState.SCHEDULED
            response.emailMaskId shouldBe mask.id
        }

    @Test
    fun scheduleSendDraftMessageWithInvalidMaskIdShouldFail() =
        runTest {
            Assume.assumeTrue("Test skipped because email masks are not enabled.", masksEnabled)

            val sendAt = Date(Date().time + Duration.ofDays(1).toMillis())
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
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
            val draftId = emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            draftId shouldNotBe null

            val input =
                ScheduleSendDraftMessageInput(
                    id = draftId,
                    emailAddressId = emailAddress.id,
                    emailMaskId = "non-existent-mask-id",
                    sendAt = sendAt,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                emailClient.scheduleSendDraftMessage(input)
            }
        }
}
