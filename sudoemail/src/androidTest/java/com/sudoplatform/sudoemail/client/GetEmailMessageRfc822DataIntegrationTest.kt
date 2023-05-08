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
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageRfc822DataInput
import com.sudoplatform.sudoemail.util.Rfc822MessageParser
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
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
 * Test the operation of [SudoEmailClient.getEmailMessageRfc822Data].
 */
@RunWith(AndroidJUnit4::class)
class GetEmailMessageRfc822DataIntegrationTest : BaseIntegrationTest() {
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
    fun getEmailMessageRFC822DataShouldReturnSuccessfulResult() = runBlocking {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val emailId = sendEmailMessage(emailClient, emailAddress)
        emailId.isBlank() shouldBe false

        // Wait for all the messages to arrive
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    emailClient.getEmailMessage(GetEmailMessageInput(emailId)) != null
                }
            }

        val emailMessage = emailClient.getEmailMessage(GetEmailMessageInput(emailId))
            ?: fail("Email message not found")

        val input = GetEmailMessageRfc822DataInput(emailId, emailAddress.id)
        val result = emailClient.getEmailMessageRfc822Data(input)
            ?: throw AssertionError("should not be null")
        result.id shouldBe emailId

        val simplifiedMessage = Rfc822MessageParser.parseRfc822Data(result.rfc822Data)
        with(simplifiedMessage) {
            to.shouldContainExactlyInAnyOrder(emailMessage.to.map { it.emailAddress })
            from.shouldContainExactlyInAnyOrder(emailMessage.from.map { it.emailAddress })
            cc.shouldContainExactlyInAnyOrder(emailMessage.cc.map { it.emailAddress })
            bcc.shouldContainExactlyInAnyOrder(emailMessage.bcc.map { it.emailAddress })
            subject shouldBe emailMessage.subject
            body shouldBe body
        }
    }

    @Test
    fun getEmailMessageRFC822DataShouldReturnNullForNonExistentMessageId() = runBlocking {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = GetEmailMessageRfc822DataInput("nonExistentId", emailAddress.id)
        val result = emailClient.getEmailMessageRfc822Data(input)
        result shouldBe null
    }
}
