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
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.UpdateDraftEmailMessageInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateDraftEmailMessageIntegrationTest : BaseIntegrationTest() {
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
    fun updateDraftEmailMessageShouldProperlyUpdateOutNetworkMessage() =
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
            updatedDraftMessage.updatedAt.time shouldBeGreaterThan draftEmailMessage.updatedAt.time

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
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

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
            updatedDraftMessage.updatedAt.time shouldBeGreaterThan draftEmailMessage.updatedAt.time

            val parsedUpdatedDraftEmailMessage =
                DefaultEmailMessageDataProcessor(
                    context,
                ).parseInternetMessageData(updatedDraftMessage.rfc822Data)

            parsedUpdatedDraftEmailMessage.to shouldContain recipientAddress.emailAddress
            parsedUpdatedDraftEmailMessage.from shouldContain emailAddress.emailAddress
            parsedUpdatedDraftEmailMessage.subject shouldBe "Test Draft updated"
        }

    @Test
    fun updateDraftEmailMessageShouldSuccessfullyUpdateDraftWithEmailMaskId() =
        runTest {
            val config = emailClient.getConfigurationData()
            Assume.assumeTrue("Test skipped due to masks not being enabled", config.emailMasksEnabled)

            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null
            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddressList.add(emailAddress)
            val maskDomains = getMaskDomains(emailClient)
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"
            val emailMask = provisionEmailMask(maskAddress, emailAddress.emailAddress, ownershipProof)

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailMask.maskAddress,
                    to = listOf(emailMask.maskAddress),
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id, emailMask.id)
            val draftId = emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

            val input = GetDraftEmailMessageInput(draftId, emailAddress.id, emailMask.id)
            val draftEmailMessage = emailClient.getDraftEmailMessage(input)

            draftEmailMessage.id shouldBe draftId
            draftEmailMessage.emailMaskId shouldBe emailMask.id
            val parsedMessage = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(draftEmailMessage.rfc822Data)
            parsedMessage.to shouldContain emailMask.maskAddress
            parsedMessage.from shouldContain emailMask.maskAddress

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
                    emailMaskId = emailMask.id,
                )

            val updateRes = emailClient.updateDraftEmailMessage(updateDraftEmailMessageInput)

            updateRes shouldBe draftId

            val updatedDraftMessage = emailClient.getDraftEmailMessage(GetDraftEmailMessageInput(updateRes, emailAddress.id, emailMask.id))
            updatedDraftMessage.id shouldBe draftId
            updatedDraftMessage.emailMaskId shouldBe emailMask.id
            updatedDraftMessage.updatedAt.time shouldBeGreaterThan draftEmailMessage.updatedAt.time

            val parsedUpdatedDraftEmailMessage =
                DefaultEmailMessageDataProcessor(
                    context,
                ).parseInternetMessageData(updatedDraftMessage.rfc822Data)

            parsedUpdatedDraftEmailMessage.to shouldContain emailMask.maskAddress
            parsedUpdatedDraftEmailMessage.from shouldContain emailMask.maskAddress
            parsedUpdatedDraftEmailMessage.subject shouldBe "Test Draft updated"
        }

    @Test
    fun updateDraftEmailMessageShouldThrowNotFoundErrorWhenEmailMaskNotPassedForDraftCreatedWithOne() =
        runTest {
            val config = emailClient.getConfigurationData()
            Assume.assumeTrue("Test skipped due to masks not being enabled", config.emailMasksEnabled)

            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null
            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddressList.add(emailAddress)
            val maskDomains = getMaskDomains(emailClient)
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"
            val emailMask = provisionEmailMask(maskAddress, emailAddress.emailAddress, ownershipProof)

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailMask.maskAddress,
                    to = listOf(emailMask.maskAddress),
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id, emailMask.id)
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
