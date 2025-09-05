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
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageRfc822DataInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
    fun teardown() =
        runTest {
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
            sudoList.map { sudoClient.deleteSudo(it) }
            sudoClient.reset()
        }

    @Test
    fun getEmailMessageRFC822DataShouldReturnSuccessfulResult() =
        runTest {
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

            waitForMessage(sendResult.id)

            val emailMessage =
                emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
                    ?: fail("Email message not found")

            val input = GetEmailMessageRfc822DataInput(sendResult.id, emailAddress.id)
            val result =
                emailClient.getEmailMessageRfc822Data(input)
                    ?: throw AssertionError("should not be null")
            result.id shouldBe sendResult.id

            val simplifiedMessage =
                Rfc822MessageDataProcessor(context).parseInternetMessageData(result.rfc822Data)
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
    fun getEmailMessageRFC822DataShouldReturnNullForNonExistentMessageId() =
        runTest {
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
