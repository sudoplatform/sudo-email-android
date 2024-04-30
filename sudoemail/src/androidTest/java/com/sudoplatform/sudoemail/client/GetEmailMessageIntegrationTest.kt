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
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.getEmailMessage].
 */
@RunWith(AndroidJUnit4::class)
class GetEmailMessageIntegrationTest : BaseIntegrationTest() {
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
    fun getEmailMessageShouldReturnEmailMessageResult() = runBlocking {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val result = sendEmailMessage(emailClient, emailAddress)
        result.id.isBlank() shouldBe false

        delay(2000)

        val getMessageInput = GetEmailMessageInput(result.id)
        val retrievedEmailMessage = emailClient.getEmailMessage(getMessageInput)
            ?: throw AssertionError("should not be null")

        with(retrievedEmailMessage) {
            from.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
            to.firstOrNull()?.emailAddress shouldBe toSimulatorAddress
            hasAttachments shouldBe false
            size shouldBeGreaterThan 0.0
        }
    }

    @Test
    fun getEmailMessageShouldReturnNullForNonExistentMessage() = runBlocking {
        val getMessageInput = GetEmailMessageInput("nonExistentId")
        val retrievedEmailMessage = emailClient.getEmailMessage(getMessageInput)
        retrievedEmailMessage shouldBe null
    }
}
