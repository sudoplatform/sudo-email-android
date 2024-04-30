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
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageWithBodyInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Test the operation of [SudoEmailClient.getEmailMessageWithBody].
 */
@RunWith(AndroidJUnit4::class)
class GetEmailMessageWithBodyIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

    private val messageBody =
        "A programmer had a problem. They thought they could solve the problem with threads. have Now problems. two they"

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
    fun getEmailMessageWithBodyShouldReturnSuccessfulResult() = runBlocking {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val sendResult = sendEmailMessage(emailClient, emailAddress, body = messageBody)
        sendResult.id.isBlank() shouldBe false

        // Wait for all the messages to arrive
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id)) != null
                }
            }

        emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
            ?: fail("Email message not found")

        val input = GetEmailMessageWithBodyInput(sendResult.id, emailAddress.id)
        val result = emailClient.getEmailMessageWithBody(input)
            ?: throw AssertionError("should not be null")
        result.id shouldBe sendResult.id
        result.body shouldBe messageBody
        result.attachments.isEmpty() shouldBe true
        result.inlineAttachments.isEmpty() shouldBe true
    }

    @Test
    fun getEmailMessageWithBodyForEncryptedMessageShouldReturnUnencryptedMessageBody() =
        runBlocking {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            receiverEmailAddress shouldNotBe null
            emailAddressList.add(receiverEmailAddress)

            val sendResult = sendEmailMessage(
                emailClient,
                emailAddress,
                toAddresses = listOf(receiverEmailAddress.emailAddress),
                body = messageBody,
            )
            sendResult.id.isBlank() shouldBe false

            // Wait for all the messages to arrive
            Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until {
                    runBlocking {
                        emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id)) != null
                    }
                }

            emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
                ?: fail("Email message not found")

            val input = GetEmailMessageWithBodyInput(sendResult.id, emailAddress.id)
            val result = emailClient.getEmailMessageWithBody(input)
                ?: throw AssertionError("should not be null")
            result.id shouldBe sendResult.id
            result.body shouldBe messageBody
            result.attachments.isEmpty() shouldBe true
            result.inlineAttachments.isEmpty() shouldBe true
        }
}
