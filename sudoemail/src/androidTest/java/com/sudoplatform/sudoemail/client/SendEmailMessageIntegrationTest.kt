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
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesInput
import com.sudoplatform.sudoemail.types.inputs.SendEmailMessageInput
import com.sudoplatform.sudoemail.util.Rfc822MessageParser
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.sendEmailMessage].
 */
@RunWith(AndroidJUnit4::class)
class SendEmailMessageIntegrationTest : BaseIntegrationTest() {
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
    fun sendEmailShouldThrowWhenBogusEmailSent() = runBlocking<Unit> {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }
            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val sendEmailMessageInput = SendEmailMessageInput(ByteArray(42), emailAddress.id)
        shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
            emailClient.sendEmailMessage(sendEmailMessageInput)
        }
    }

    @Test
    fun sendEmailShouldThrowWhenBogusRecipientAddressUsed() = runBlocking<Unit> {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val rfc822Data = Rfc822MessageParser.encodeToRfc822Data(
            from = emailAddress.emailAddress,
            to = listOf("bogusEmailAddress"),
        )

        val sendEmailMessageInput = SendEmailMessageInput(rfc822Data, emailAddress.id)
        shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
            emailClient.sendEmailMessage(sendEmailMessageInput)
        }
    }

    @Test
    fun sendEmailShouldThrowWhenBogusSenderAddressUsed() = runBlocking<Unit> {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val rfc822Data = Rfc822MessageParser.encodeToRfc822Data(
            from = emailAddress.emailAddress,
            to = listOf(emailAddress.emailAddress),
        )
        val sendEmailMessageInput = SendEmailMessageInput(rfc822Data, "bogusEmailAddressId")
        shouldThrow<SudoEmailClient.EmailMessageException.UnauthorizedAddressException> {
            emailClient.sendEmailMessage(sendEmailMessageInput)
        }
    }
}
