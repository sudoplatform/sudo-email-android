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
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of [SudoEmailClient.getDraftEmailMessage].
 */
@RunWith(AndroidJUnit4::class)
class GetDraftEmailMessageIntegrationTest : BaseIntegrationTest() {
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
    fun getDraftEmailMessageShouldThrowErrorIfSenderEmailAddressNotFound() = runTest {
        val mockDraftId = UUID.randomUUID()
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)
        val input = GetDraftEmailMessageInput(mockDraftId.toString(), "bogusEmailId")
        shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
            emailClient.getDraftEmailMessage(input)
        }
    }

    @Test
    fun getDraftEmailMessageShouldThrowErrorIfDraftMessageNotFound() = runTest {
        val mockDraftId = UUID.randomUUID()
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)
        val input = GetDraftEmailMessageInput(mockDraftId.toString(), emailAddress.id)
        shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
            emailClient.getDraftEmailMessage(input)
        }
    }

    @Test
    fun getDraftEmailMessageShouldReturnProperMessage() = runTest {
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
        draftEmailMessage.emailAddressId shouldBe emailAddress.id
        val parsedMessage = Rfc822MessageDataProcessor(context).parseInternetMessageData(draftEmailMessage.rfc822Data)

        parsedMessage.to shouldContain emailAddress.emailAddress
        parsedMessage.from shouldContain emailAddress.emailAddress
        parsedMessage.subject shouldBe "Test Draft"
    }
}
