/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailAlias
import com.sudoplatform.sudoemail.types.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.types.inputs.CancelScheduledDraftMessageInput
import com.sudoplatform.sudoemail.types.inputs.DeprovisionEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.ListScheduledDraftMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.NotEqualStateFilter
import com.sudoplatform.sudoemail.types.inputs.ScheduledDraftMessageFilterInput
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Test the operation of [SudoEmailClient.listScheduledDraftMessagesForEmailAddressId].
 */
@RunWith(AndroidJUnit4::class)
class ListScheduledDraftMessagesForEmailAddressIdIntegrationTest : BaseIntegrationTest() {
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
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        }

    @Test
    fun listScheduledDraftMessagesForEmailAddressIdShouldFailWithInvalidEmailAddressId() =
        runTest {
            val input =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = "dummyEmailAddressId",
                )

            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                emailClient.listScheduledDraftMessagesForEmailAddressId(input)
            }
        }

    @Test
    fun listScheduledDraftMessagesForEmailAddressIdReturnsEmptyListWhenNoDraftsScheduled() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                )

            val response = emailClient.listScheduledDraftMessagesForEmailAddressId(input)

            response shouldNotBe null
            response.nextToken shouldBe null
            response.items.size shouldBe 0
        }

    @Test
    fun listScheduledDraftMessagesForEmailAddressIdShouldReturnSingleItemProperly() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val sendAt =
                Date(
                    Date().time +
                        java.time.Duration
                            .ofDays(1)
                            .toMillis(),
                )
            val draftId = scheduleSendDraftMessage(emailAddress, sendAt = sendAt)

            val input =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                )

            val response = emailClient.listScheduledDraftMessagesForEmailAddressId(input)

            response shouldNotBe null
            response.nextToken shouldBe null
            response.items.size shouldBe 1
            val scheduledDraftMessage = response.items[0]
            scheduledDraftMessage.id shouldBe draftId
            scheduledDraftMessage.emailAddressId shouldBe emailAddress.id
            scheduledDraftMessage.state shouldBe ScheduledDraftMessageState.SCHEDULED
            scheduledDraftMessage.sendAt shouldBe sendAt
        }

    @Test
    fun listScheduledDraftMessagesForEmailAddressIdReturnsMultipleItemsProperly() =
        runTest {
            val numMessages = 3
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val sendAt =
                Date(
                    Date().time +
                        java.time.Duration
                            .ofDays(1)
                            .toMillis(),
                )
            val draftIds = mutableListOf<String>()
            for (i in 0 until numMessages) {
                val scheduleResult = scheduleSendDraftMessage(emailAddress, sendAt = sendAt)
                scheduleResult shouldNotBe null
                draftIds.add(scheduleResult)
            }

            val input =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                )

            val response = emailClient.listScheduledDraftMessagesForEmailAddressId(input)

            response shouldNotBe null
            response.nextToken shouldBe null
            response.items.size shouldBe numMessages
            for (scheduledDraftMessage in response.items) {
                draftIds shouldContain scheduledDraftMessage.id
                scheduledDraftMessage.emailAddressId shouldBe emailAddress.id
                scheduledDraftMessage.state shouldBe ScheduledDraftMessageState.SCHEDULED
                scheduledDraftMessage.sendAt shouldBe sendAt
            }
        }

    @Test
    fun listScheduledDraftMessagesForEmailAddressIdShouldRespectLimitAndPaginateProperly() =
        runTest {
            val numMessages = 5
            val limit = 3
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val sendAt =
                Date(
                    Date().time +
                        java.time.Duration
                            .ofDays(1)
                            .toMillis(),
                )
            val draftIds = mutableListOf<String>()
            for (i in 0 until numMessages) {
                val scheduleResult = scheduleSendDraftMessage(emailAddress, sendAt = sendAt)
                scheduleResult shouldNotBe null
                draftIds.add(scheduleResult)
            }

            val input =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                    limit = limit,
                )

            val responsePage1 = emailClient.listScheduledDraftMessagesForEmailAddressId(input)

            responsePage1 shouldNotBe null
            responsePage1.nextToken shouldNotBe null
            responsePage1.items.size shouldBe limit
            for (scheduledDraftMessage in responsePage1.items) {
                draftIds shouldContain scheduledDraftMessage.id
                scheduledDraftMessage.emailAddressId shouldBe emailAddress.id
                scheduledDraftMessage.state shouldBe ScheduledDraftMessageState.SCHEDULED
                scheduledDraftMessage.sendAt shouldBe sendAt
            }

            val responsePage2 =
                emailClient.listScheduledDraftMessagesForEmailAddressId(
                    ListScheduledDraftMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                        limit = limit,
                        nextToken = responsePage1.nextToken,
                    ),
                )

            responsePage2 shouldNotBe null
            responsePage2.nextToken shouldBe null
            responsePage2.items.size shouldBe numMessages - limit
            for (scheduledDraftMessage in responsePage2.items) {
                draftIds shouldContain scheduledDraftMessage.id
                scheduledDraftMessage.emailAddressId shouldBe emailAddress.id
                scheduledDraftMessage.state shouldBe ScheduledDraftMessageState.SCHEDULED
                scheduledDraftMessage.sendAt shouldBe sendAt
            }
        }

    @Test
    fun listScheduledDraftMessagesForEmailAddressIdShouldFilterByStateProperly() =
        runTest {
            val numMessages = 3
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val sendAt =
                Date(
                    Date().time +
                        java.time.Duration
                            .ofDays(1)
                            .toMillis(),
                )
            val draftIds = mutableListOf<String>()
            for (i in 0 until numMessages) {
                val scheduleResult = scheduleSendDraftMessage(emailAddress, sendAt = sendAt)
                scheduleResult shouldNotBe null
                draftIds.add(scheduleResult)
            }

            val cancelled =
                emailClient.cancelScheduledDraftMessage(
                    CancelScheduledDraftMessageInput(
                        id = draftIds[0],
                        emailAddressId = emailAddress.id,
                    ),
                )
            cancelled shouldBe draftIds[0]

            val input =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                    filter =
                        ScheduledDraftMessageFilterInput(
                            state =
                                NotEqualStateFilter(
                                    notEqual = ScheduledDraftMessageState.CANCELLED,
                                ),
                        ),
                )

            val response = emailClient.listScheduledDraftMessagesForEmailAddressId(input)

            response shouldNotBe null
            response.nextToken shouldBe null
            response.items.size shouldBe numMessages - 1
            for (scheduledDraftMessage in response.items) {
                draftIds shouldContain scheduledDraftMessage.id
                scheduledDraftMessage.emailAddressId shouldBe emailAddress.id
                scheduledDraftMessage.state shouldBe ScheduledDraftMessageState.SCHEDULED
                scheduledDraftMessage.sendAt shouldBe sendAt
            }
            response.items.find { it.id == cancelled } shouldBe null
        }

    // -------------------------------------------------------------------------
    // Alias-related tests – skipped when aliases are not enabled
    // -------------------------------------------------------------------------

    @Test
    fun listScheduledDraftMessagesForEmailAddressIdShouldReturnEmailAliasIdWhenPresent() =
        runTest {
            Assume.assumeTrue("Test skipped because email masks are not enabled.", aliasesEnabled)

            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"
            val alias = provisionEmailAlias(aliasAddress, emailAddress.emailAddress, ownershipProof)

            val sendAt =
                Date(
                    Date().time +
                        java.time.Duration
                            .ofDays(1)
                            .toMillis(),
                )

            val draftId = scheduleSendDraftMessage(emailAddress, emailAliasId = alias.id, sendAt = sendAt)

            val input =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                )

            val response = emailClient.listScheduledDraftMessagesForEmailAddressId(input)

            response shouldNotBe null
            response.nextToken shouldBe null
            response.items.size shouldBe 1
            val scheduledDraftMessage = response.items[0]
            scheduledDraftMessage.id shouldBe draftId
            scheduledDraftMessage.emailAddressId shouldBe emailAddress.id
            scheduledDraftMessage.state shouldBe ScheduledDraftMessageState.SCHEDULED
            scheduledDraftMessage.sendAt shouldBe sendAt
            scheduledDraftMessage.emailAliasId shouldBe alias.id
        }

    @Test
    fun listScheduledDraftMessagesForEmailAddressIdShouldReturnNullEmailAliasIdWhenNotPresent() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val sendAt =
                Date(
                    Date().time +
                        java.time.Duration
                            .ofDays(1)
                            .toMillis(),
                )

            val draftId = scheduleSendDraftMessage(emailAddress, sendAt = sendAt)

            val input =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                )

            val response = emailClient.listScheduledDraftMessagesForEmailAddressId(input)

            response shouldNotBe null
            response.items.size shouldBe 1
            val scheduledDraftMessage = response.items[0]
            scheduledDraftMessage.id shouldBe draftId
            scheduledDraftMessage.emailAliasId shouldBe null
        }

    @Test
    fun listScheduledDraftMessagesForEmailAddressIdShouldReturnMixedAliasAndNonAliasDrafts() =
        runTest {
            Assume.assumeTrue("Test skipped because email masks are not enabled.", aliasesEnabled)

            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"
            val alias = provisionEmailAlias(aliasAddress, emailAddress.emailAddress, ownershipProof)

            val sendAt =
                Date(
                    Date().time +
                        java.time.Duration
                            .ofDays(1)
                            .toMillis(),
                )

            // Schedule one draft with an alias and one without
            val aliasedDraftId = scheduleSendDraftMessage(emailAddress, emailAliasId = alias.id, sendAt = sendAt)
            val unaliasedDraftId = scheduleSendDraftMessage(emailAddress, sendAt = sendAt)

            val input =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                )

            val response = emailClient.listScheduledDraftMessagesForEmailAddressId(input)

            response shouldNotBe null
            response.items.size shouldBe 2

            val aliasedDraft = response.items.find { it.id == aliasedDraftId }
            aliasedDraft shouldNotBe null
            aliasedDraft!!.emailAliasId shouldBe alias.id

            val unaliasedDraft = response.items.find { it.id == unaliasedDraftId }
            unaliasedDraft shouldNotBe null
            unaliasedDraft!!.emailAliasId shouldBe null
        }
}
