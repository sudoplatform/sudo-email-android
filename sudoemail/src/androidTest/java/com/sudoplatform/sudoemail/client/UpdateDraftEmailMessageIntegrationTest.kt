/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.TestData
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.UpdateDraftEmailMessageInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateDraftEmailMessageIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

    @Before
    fun setup() {
        sudoClient.reset()
        sudoClient.generateEncryptionKey()
    }

    @After
    fun teardown() = runTest {
        emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        sudoList.map { sudoClient.deleteSudo(it) }
        sudoClient.reset()
    }

    @Test
    fun updateDraftEmailMessageShouldProperlyUpdateMessage() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val rfc822Data = Rfc822MessageDataProcessor(context).encodeToInternetMessageData(
            from = emailAddress.emailAddress,
            to = listOf(emailAddress.emailAddress),
            subject = "Test Draft",
        )

        val createDraftInput = CreateDraftEmailMessageInput(
            rfc822Data = rfc822Data,
            senderEmailAddressId = emailAddress.id,
        )

        val draftId = emailClient.createDraftEmailMessage(createDraftInput)

        val input = GetDraftEmailMessageInput(draftId, emailAddress.id)
        val draftEmailMessage = emailClient.getDraftEmailMessage(input)

        draftEmailMessage.id shouldBe draftId
        val parsedMessage = Rfc822MessageDataProcessor(context).parseInternetMessageData(draftEmailMessage.rfc822Data)

        parsedMessage.to shouldContain emailAddress.emailAddress
        parsedMessage.from shouldContain emailAddress.emailAddress
        parsedMessage.subject shouldBe "Test Draft"

        val updatedRfc822Data = Rfc822MessageDataProcessor(context).encodeToInternetMessageData(
            from = parsedMessage.from[0],
            to = listOf(parsedMessage.to[0]),
            subject = "Test Draft updated",
        )

        val updateDraftEmailMessageInput = UpdateDraftEmailMessageInput(
            id = draftId,
            rfc822Data = updatedRfc822Data,
            senderEmailAddressId = emailAddress.id,
        )

        val updateRes = emailClient.updateDraftEmailMessage(updateDraftEmailMessageInput)

        updateRes shouldBe draftId

        val updatedDraftMessage = emailClient.getDraftEmailMessage(GetDraftEmailMessageInput(updateRes, emailAddress.id))
        updatedDraftMessage.id shouldBe draftId
        updatedDraftMessage.updatedAt.time shouldBeGreaterThan draftEmailMessage.updatedAt.time

        val parsedUpdatedDraftEmailMessage = Rfc822MessageDataProcessor(context).parseInternetMessageData(updatedDraftMessage.rfc822Data)

        parsedUpdatedDraftEmailMessage.to shouldContain emailAddress.emailAddress
        parsedUpdatedDraftEmailMessage.from shouldContain emailAddress.emailAddress
        parsedUpdatedDraftEmailMessage.subject shouldBe "Test Draft updated"
    }
}
