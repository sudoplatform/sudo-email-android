/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.util.DefaultEmailMessageDataProcessor
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.DeleteDraftEmailMessagesInput
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.ListScheduledDraftMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ScheduleSendDraftMessageInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.awaitility.Awaitility
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

    // Shared alias test resources (only populated when aliases are enabled)
    private var aliasDomains: List<String> = emptyList()
    private var aliasesEnabled: Boolean = false

    @Before
    fun setup() =
        runTest {
            val config = emailClient.getConfigurationData()
            aliasesEnabled = config.emailAliasesEnabled
            if (aliasesEnabled) {
                aliasDomains = getAliasDomains(emailClient)
            }
        }

    @After
    fun teardown() =
        runTest {
            emailAddressList.forEach { emailClient.deprovisionEmailAddress(it.id) }
        }

    @Test
    fun scheduleSendDraftMessageShouldFailWithInvalidEmailAddressId() =
        runTest {
            val input =
                ScheduleSendDraftMessageInput(
                    "dummyId",
                    "dummyEmailAddressId",
                    emailAliasId = null,
                    Date(),
                )

            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                emailClient.scheduleSendDraftMessage(input)
            }
        }

    @Test
    fun scheduleSendDraftMessageShouldFailWithSendAtNotInFuture() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

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
            val ownershipProof = sharedOwnershipProof()

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
            val ownershipProof = sharedOwnershipProof()

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
            response.emailAliasId shouldBe null
        }

    @Test
    fun scheduleAndDeleteDraftMessageShouldSucceed() =
        runTest {
            val sendAt = Date(Date().time + Duration.ofDays(1).toMillis())
            val ownershipProof = sharedOwnershipProof()

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

            val listInput = ListScheduledDraftMessagesForEmailAddressIdInput(emailAddressId = emailAddress.id)

            // Poll until the draft appears as SCHEDULED (scheduling is eventually
            // consistent).
            Awaitility
                .await("draft $draftId to be scheduled")
                .atMost(org.awaitility.Duration.TEN_SECONDS)
                .pollInterval(org.awaitility.Duration.TWO_HUNDRED_MILLISECONDS)
                .until {
                    runBlocking {
                        emailClient.listScheduledDraftMessagesForEmailAddressId(listInput).items.any {
                            it.id == draftId && it.state == ScheduledDraftMessageState.SCHEDULED
                        }
                    }
                }

            emailClient.deleteDraftEmailMessages(DeleteDraftEmailMessagesInput(listOf(draftId), emailAddress.id))

            // Poll until getting the underlying draft throws not-found (deletion is
            // eventually consistent).
            Awaitility
                .await("draft $draftId to be deleted")
                .atMost(org.awaitility.Duration.TEN_SECONDS)
                .pollInterval(org.awaitility.Duration.TWO_HUNDRED_MILLISECONDS)
                .until {
                    runBlocking {
                        shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                            emailClient.getDraftEmailMessage(GetDraftEmailMessageInput(scheduledDraft.id, emailAddress.id))
                        }
                        true
                    }
                }
        }

    // -------------------------------------------------------------------------
    // Alias-related tests – skipped when aliases are not enabled
    // -------------------------------------------------------------------------

    @Test
    fun scheduleSendDraftMessageWithAliasShouldReturnEmailAliasIdInResponse() =
        runTest {
            Assume.assumeTrue("Test skipped because email masks are not enabled.", aliasesEnabled)

            val sendAt = Date(Date().time + Duration.ofDays(1).toMillis())
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"
            val alias = provisionEmailAlias(aliasAddress, emailAddress.emailAddress, ownershipProof)

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAddress.emailAddress,
                    to = listOf(emailAddress.emailAddress),
                )
            val createDraftEmailMessageInput =
                CreateDraftEmailMessageInput(
                    rfc822Data,
                    emailAddress.id,
                    alias.id,
                )
            val draftId = emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            draftId shouldNotBe null

            val input =
                ScheduleSendDraftMessageInput(
                    id = draftId,
                    emailAddressId = emailAddress.id,
                    emailAliasId = alias.id,
                    sendAt = sendAt,
                )

            val response = emailClient.scheduleSendDraftMessage(input)

            response shouldNotBe null
            response.id shouldBe draftId
            response.emailAddressId shouldBe emailAddress.id
            response.sendAt shouldBe sendAt
            response.state shouldBe ScheduledDraftMessageState.SCHEDULED
            response.emailAliasId shouldBe alias.id
        }

    @Test
    fun scheduleSendDraftMessageWithInvalidAliasIdShouldFail() =
        runTest {
            Assume.assumeTrue("Test skipped because email masks are not enabled.", aliasesEnabled)

            val sendAt = Date(Date().time + Duration.ofDays(1).toMillis())
            val ownershipProof = sharedOwnershipProof()

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
                    emailAliasId = "non-existent-alias-id",
                    sendAt = sendAt,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                emailClient.scheduleSendDraftMessage(input)
            }
        }
}
