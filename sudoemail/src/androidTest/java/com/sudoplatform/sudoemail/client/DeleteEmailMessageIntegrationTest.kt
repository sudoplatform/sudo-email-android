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
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.awaitility.kotlin.has
import org.awaitility.kotlin.untilCallTo
import org.awaitility.kotlin.withPollInterval
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.deleteEmailMessage].
 */
@RunWith(AndroidJUnit4::class)
class DeleteEmailMessageIntegrationTest : BaseIntegrationTest() {
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
    fun deleteEmailMessageShouldSucceed() = runBlocking {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val sendResult = sendEmailMessage(emailClient, emailAddress)
        sendResult.id.isBlank() shouldBe false
        sendResult.createdAt shouldNotBe null

        // Wait for all the messages to arrive
        await.atMost(Duration.ONE_MINUTE) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
            runBlocking {
                val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                )
                emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
            }
        } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == 2 }

        val result = emailClient.deleteEmailMessage(sendResult.id)
        result shouldBe sendResult.id
    }

    @Test
    fun deleteEmailMessageShouldReturnNullForNonExistentMessage() = runBlocking {
        val result = emailClient.deleteEmailMessage("nonExistentId")
        result shouldBe null
    }
}
