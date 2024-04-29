/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
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
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.listDraftEmailMessageMetadataForEmailAddressId].
 */
@RunWith(AndroidJUnit4::class)
class ListDraftEmailMessagesForEmailAddressIdIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

    @Before
    fun setup() {
        sudoClient.reset()
        sudoClient.generateEncryptionKey()
    }

    @After
    fun teardown() = runBlocking {
        emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        sudoList.map { sudoClient.deleteSudo(it) }
        sudoClient.reset()
    }

    @Test
    fun listDraftEmailMessagesForEmailAddressIdShouldThrowErrorIfSenderEmailAddressNotFound() = runBlocking<Unit> {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
            emailClient.listDraftEmailMessagesForEmailAddressId("bogusEmailId")
        }
    }

    @Test
    fun listDraftEmailMessagesForEmailAddressIdShouldReturnEmptyListWhenDraftMessagesNotFound() = runBlocking {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val result = emailClient.listDraftEmailMessagesForEmailAddressId(emailAddress.id)

        result.size shouldBe 0
    }

    @Test
    fun listDraftEmailMessagesForEmailAddressIdShouldReturnListOfDraftMessages() = runBlocking {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        for (i in 0 until 2) {
            val rfc822Data = Rfc822MessageDataProcessor().encodeToInternetMessageData(
                from = emailAddress.emailAddress,
                to = listOf(emailAddress.emailAddress),
                subject = "Draft $i",
            )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
            emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
        }

        val result = emailClient.listDraftEmailMessagesForEmailAddressId(emailAddress.id)

        result.size shouldBe 2

        result.forEach { item ->
            item.emailAddressId shouldBe emailAddress.id

            val parsedMessage = Rfc822MessageDataProcessor().parseInternetMessageData(item.rfc822Data)
            parsedMessage.to shouldContain emailAddress.emailAddress
            parsedMessage.from shouldContain emailAddress.emailAddress
            parsedMessage.subject shouldContain "Draft"
        }
    }
}
