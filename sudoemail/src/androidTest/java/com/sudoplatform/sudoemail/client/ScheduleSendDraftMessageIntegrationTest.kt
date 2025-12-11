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
import com.sudoplatform.sudoemail.types.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.DeleteDraftEmailMessagesInput
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.ScheduleSendDraftMessageInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
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
    fun scheduleSendDraftMessageShouldFailWithInvalidEmailAddressId() =
        runTest {
            val input =
                ScheduleSendDraftMessageInput(
                    "dummyId",
                    "dummyEmailAddressId",
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
                    sendAt,
                )

            val response = emailClient.scheduleSendDraftMessage(input)

            response shouldNotBe null
            response.id shouldBe draftId
            response.emailAddressId shouldBe emailAddress.id
            response.sendAt shouldBe sendAt
            response.state shouldBe ScheduledDraftMessageState.SCHEDULED
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
}
