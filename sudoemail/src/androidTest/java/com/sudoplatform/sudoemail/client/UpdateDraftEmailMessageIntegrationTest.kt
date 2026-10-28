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
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.UpdateDraftEmailMessageInput
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateDraftEmailMessageIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()

    @After
    fun teardown() =
        runTest {
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        }

    @Test
    fun updateDraftEmailMessageShouldProperlyUpdateOutNetworkMessage() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAddress.emailAddress,
                    to = listOf(successSimulatorAddress),
                    subject = "Test Draft",
                )

            val createDraftInput =
                CreateDraftEmailMessageInput(
                    rfc822Data = rfc822Data,
                    senderEmailAddressId = emailAddress.id,
                )

            val draftId = emailClient.createDraftEmailMessage(createDraftInput)

            val input = GetDraftEmailMessageInput(draftId, emailAddress.id)
            val draftEmailMessage = emailClient.getDraftEmailMessage(input)

            draftEmailMessage.id shouldBe draftId
            val parsedMessage = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(draftEmailMessage.rfc822Data)

            parsedMessage.to shouldContain successSimulatorAddress
            parsedMessage.from shouldContain emailAddress.emailAddress
            parsedMessage.subject shouldBe "Test Draft"

            val updatedRfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = parsedMessage.from[0],
                    to = listOf(parsedMessage.to[0]),
                    subject = "Test Draft updated",
                )

            val updateDraftEmailMessageInput =
                UpdateDraftEmailMessageInput(
                    id = draftId,
                    rfc822Data = updatedRfc822Data,
                    senderEmailAddressId = emailAddress.id,
                )

            val updateRes = emailClient.updateDraftEmailMessage(updateDraftEmailMessageInput)

            updateRes shouldBe draftId

            val updatedDraftMessage = emailClient.getDraftEmailMessage(GetDraftEmailMessageInput(updateRes, emailAddress.id))
            updatedDraftMessage.id shouldBe draftId
            // Ideally, it would be greater, not equal, but sometimes it happens to fast and the time is only stored up to the second
            // As long as the subject actually is properly updated below, we should be fine
            updatedDraftMessage.updatedAt.time shouldBeGreaterThanOrEqual draftEmailMessage.updatedAt.time

            val parsedUpdatedDraftEmailMessage =
                DefaultEmailMessageDataProcessor(
                    context,
                ).parseInternetMessageData(updatedDraftMessage.rfc822Data)

            parsedUpdatedDraftEmailMessage.to shouldContain successSimulatorAddress
            parsedUpdatedDraftEmailMessage.from shouldContain emailAddress.emailAddress
            parsedUpdatedDraftEmailMessage.subject shouldBe "Test Draft updated"
        }

    @Test
    fun updateDraftEmailMessageShouldProperlyUpdateInNetworkMessage() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)
            val recipientAddress = provisionEmailAddress(emailClient, ownershipProof, mixedCaseEmail = true)
            recipientAddress shouldNotBe null
            emailAddressList.add(recipientAddress)

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAddress.emailAddress,
                    to = listOf(recipientAddress.emailAddress),
                    subject = "Test Draft",
                )

            val createDraftInput =
                CreateDraftEmailMessageInput(
                    rfc822Data = rfc822Data,
                    senderEmailAddressId = emailAddress.id,
                )

            val draftId = emailClient.createDraftEmailMessage(createDraftInput)

            val input = GetDraftEmailMessageInput(draftId, emailAddress.id)
            val draftEmailMessage = emailClient.getDraftEmailMessage(input)

            draftEmailMessage.id shouldBe draftId
            val parsedMessage = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(draftEmailMessage.rfc822Data)

            parsedMessage.to shouldContain recipientAddress.emailAddress
            parsedMessage.from shouldContain emailAddress.emailAddress
            parsedMessage.subject shouldBe "Test Draft"

            val updatedRfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = parsedMessage.from[0],
                    to = listOf(parsedMessage.to[0]),
                    subject = "Test Draft updated",
                )

            val updateDraftEmailMessageInput =
                UpdateDraftEmailMessageInput(
                    id = draftId,
                    rfc822Data = updatedRfc822Data,
                    senderEmailAddressId = emailAddress.id,
                )

            val updateRes = emailClient.updateDraftEmailMessage(updateDraftEmailMessageInput)

            updateRes shouldBe draftId

            val updatedDraftMessage = emailClient.getDraftEmailMessage(GetDraftEmailMessageInput(updateRes, emailAddress.id))
            updatedDraftMessage.id shouldBe draftId
            // Ideally, it would be greater, not equal, but sometimes it happens to fast and the time is only stored up to the second
            // As long as the subject actually is properly updated below, we should be fine
            updatedDraftMessage.updatedAt.time shouldBeGreaterThanOrEqual draftEmailMessage.updatedAt.time

            val parsedUpdatedDraftEmailMessage =
                DefaultEmailMessageDataProcessor(
                    context,
                ).parseInternetMessageData(updatedDraftMessage.rfc822Data)

            parsedUpdatedDraftEmailMessage.to shouldContain recipientAddress.emailAddress
            parsedUpdatedDraftEmailMessage.from shouldContain emailAddress.emailAddress
            parsedUpdatedDraftEmailMessage.subject shouldBe "Test Draft updated"
        }

    @Test
    fun updateDraftEmailMessageShouldSuccessfullyUpdateDraftWithEmailAliasId() =
        runTest {
            val config = emailClient.getConfigurationData()
            Assume.assumeTrue("Test skipped due to masks not being enabled", config.emailAliasesEnabled)

            val ownershipProof = sharedOwnershipProof()
            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddressList.add(emailAddress)
            val aliasDomains = getAliasDomains(emailClient)
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"
            val emailAlias = provisionEmailAlias(aliasAddress, emailAddress.emailAddress, ownershipProof)

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAlias.aliasAddress,
                    to = listOf(emailAlias.aliasAddress),
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id, emailAlias.id)
            val draftId = emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

            val input = GetDraftEmailMessageInput(draftId, emailAddress.id, emailAlias.id)
            val draftEmailMessage = emailClient.getDraftEmailMessage(input)

            draftEmailMessage.id shouldBe draftId
            draftEmailMessage.emailAliasId shouldBe emailAlias.id
            val parsedMessage = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(draftEmailMessage.rfc822Data)
            parsedMessage.to shouldContain emailAlias.aliasAddress
            parsedMessage.from shouldContain emailAlias.aliasAddress

            val updatedRfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = parsedMessage.from[0],
                    to = listOf(parsedMessage.to[0]),
                    subject = "Test Draft updated",
                )

            val updateDraftEmailMessageInput =
                UpdateDraftEmailMessageInput(
                    id = draftId,
                    rfc822Data = updatedRfc822Data,
                    senderEmailAddressId = emailAddress.id,
                    emailAliasId = emailAlias.id,
                )

            val updateRes = emailClient.updateDraftEmailMessage(updateDraftEmailMessageInput)

            updateRes shouldBe draftId

            val updatedDraftMessage = emailClient.getDraftEmailMessage(GetDraftEmailMessageInput(updateRes, emailAddress.id, emailAlias.id))
            updatedDraftMessage.id shouldBe draftId
            updatedDraftMessage.emailAliasId shouldBe emailAlias.id
            // Ideally, it would be greater, not equal, but sometimes it happens to fast and the time is only stored up to the second
            // As long as the subject actually is properly updated below, we should be fine
            updatedDraftMessage.updatedAt.time shouldBeGreaterThanOrEqual draftEmailMessage.updatedAt.time

            val parsedUpdatedDraftEmailMessage =
                DefaultEmailMessageDataProcessor(
                    context,
                ).parseInternetMessageData(updatedDraftMessage.rfc822Data)

            parsedUpdatedDraftEmailMessage.to shouldContain emailAlias.aliasAddress
            parsedUpdatedDraftEmailMessage.from shouldContain emailAlias.aliasAddress
            parsedUpdatedDraftEmailMessage.subject shouldBe "Test Draft updated"
        }

    @Test
    fun updateDraftEmailMessageShouldThrowNotFoundErrorWhenEmailAliasNotPassedForDraftCreatedWithOne() =
        runTest {
            val config = emailClient.getConfigurationData()
            Assume.assumeTrue("Test skipped due to masks not being enabled", config.emailAliasesEnabled)

            val ownershipProof = sharedOwnershipProof()
            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddressList.add(emailAddress)
            val aliasDomains = getAliasDomains(emailClient)
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"
            val emailAlias = provisionEmailAlias(aliasAddress, emailAddress.emailAddress, ownershipProof)

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAlias.aliasAddress,
                    to = listOf(emailAlias.aliasAddress),
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id, emailAlias.id)
            val draftId = emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

            val updateDraftEmailMessageInput =
                UpdateDraftEmailMessageInput(
                    id = draftId,
                    rfc822Data = rfc822Data,
                    senderEmailAddressId = emailAddress.id,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                emailClient.updateDraftEmailMessage(updateDraftEmailMessageInput)
            }
        }
}
