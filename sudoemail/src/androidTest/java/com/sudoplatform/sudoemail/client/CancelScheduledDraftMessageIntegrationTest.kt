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
import com.sudoplatform.sudoemail.types.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.types.inputs.CancelScheduledDraftMessageInput
import com.sudoplatform.sudoemail.types.inputs.ListScheduledDraftMessagesForEmailAddressIdInput
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
            val ownershipProof = sharedOwnershipProof()

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
            val ownershipProof = sharedOwnershipProof()

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
    // Alias-related tests – skipped when aliases are not enabled
    // -------------------------------------------------------------------------

    @Test
    fun cancelScheduledDraftMessageWithAliasShouldReturnDraftIdOnSuccess() =
        runTest {
            Assume.assumeTrue("Test skipped because email masks are not enabled.", aliasesEnabled)

            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"
            val alias = provisionEmailAlias(aliasAddress, emailAddress.emailAddress, ownershipProof)

            // Schedule with the alias associated
            val draftId = scheduleSendDraftMessage(emailAddress, emailAliasId = alias.id)

            val input =
                CancelScheduledDraftMessageInput(
                    id = draftId,
                    emailAddressId = emailAddress.id,
                    emailAliasId = alias.id,
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
            cancelledDraft.emailAliasId shouldBe alias.id
        }

    @Test
    fun cancelScheduledDraftMessageWithAliasShouldSucceedWithoutAliasIdInCancelInput() =
        runTest {
            Assume.assumeTrue("Test skipped because email masks are not enabled.", aliasesEnabled)

            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"
            val alias = provisionEmailAlias(aliasAddress, emailAddress.emailAddress, ownershipProof)

            // Schedule with the alias associated
            val draftId = scheduleSendDraftMessage(emailAddress, emailAliasId = alias.id)

            // Cancel without specifying the emailAliasId – should still succeed
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
