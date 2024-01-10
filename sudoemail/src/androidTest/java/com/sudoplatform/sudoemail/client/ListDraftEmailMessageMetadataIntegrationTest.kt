/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
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
import com.sudoplatform.sudoemail.util.Rfc822MessageParser
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.listDraftEmailMessageMetadata].
 */
@RunWith(AndroidJUnit4::class)
class ListDraftEmailMessageMetadataIntegrationTest : BaseIntegrationTest() {
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
    fun listDraftEmailMessageMetadataShouldFailWithBogusSenderAddress() = runBlocking<Unit> {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)
        shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
            emailClient.listDraftEmailMessageMetadata("bogusEmailId")
        }
    }

    @Test
    fun listDraftEmailMessageMetadataShouldReturnEmptyListIfNoDrafts() = runBlocking {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val result = emailClient.listDraftEmailMessageMetadata(emailAddress.id)

        result.size shouldBe 0
    }

    @Test
    fun listDraftEmailMessageMetadataShouldReturnListOfDraftMessages() = runBlocking {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val createdDraftIds = mutableListOf<String>()

        for (i in 0 until 2) {
            val rfc822Data = Rfc822MessageParser.encodeToRfc822Data(
                from = emailAddress.emailAddress,
                to = listOf(emailAddress.emailAddress),
                subject = "Draft $i",
            )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
            createdDraftIds.add(emailClient.createDraftEmailMessage(createDraftEmailMessageInput))
        }
        createdDraftIds.sort()
        val result = emailClient.listDraftEmailMessageMetadata(emailAddress.id)

        result.size shouldBe 2
        result.sortedBy { it.id }.forEachIndexed { index, draftEmailMessageMetadata ->
            draftEmailMessageMetadata.id shouldBe createdDraftIds[index]
        }
    }

    @Test
    fun listDraftEmailMessageMetadataShouldUpdateWhenNewDraftIsAdded() = runBlocking {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        for (i in 0 until 2) {
            val rfc822Data = Rfc822MessageParser.encodeToRfc822Data(
                from = emailAddress.emailAddress,
                to = listOf(emailAddress.emailAddress),
                subject = "Draft $i",
            )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
            emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
        }

        val result = emailClient.listDraftEmailMessageMetadata(emailAddress.id)

        result.size shouldBe 2

        val rfc822Data = Rfc822MessageParser.encodeToRfc822Data(
            from = emailAddress.emailAddress,
            to = listOf(emailAddress.emailAddress),
            subject = "New Draft",
        )
        val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
        emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

        val finalResult = emailClient.listDraftEmailMessageMetadata(emailAddress.id)

        finalResult.size shouldBe 3
    }

    @Test
    fun listDraftEmailMessageMetadataShouldNotListMessagesFromOtherAccounts() = runBlocking {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val emailAddress2 = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress2 shouldNotBe null
        emailAddressList.add(emailAddress2)

        for (i in 0 until 2) {
            val rfc822Data = Rfc822MessageParser.encodeToRfc822Data(
                from = emailAddress.emailAddress,
                to = listOf(emailAddress.emailAddress),
                subject = "Draft $i",
            )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
            emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
        }

        val rfc822Data = Rfc822MessageParser.encodeToRfc822Data(
            from = emailAddress2.emailAddress,
            to = listOf(emailAddress2.emailAddress),
            subject = "Not yours",
        )
        val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress2.id)
        emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

        val result = emailClient.listDraftEmailMessageMetadata(emailAddress.id)

        result.size shouldBe 2
    }
}
