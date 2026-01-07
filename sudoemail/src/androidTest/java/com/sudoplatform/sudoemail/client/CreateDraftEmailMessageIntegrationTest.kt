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
import com.sudoplatform.sudoemail.internal.util.DefaultEmailMessageDataProcessor
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.string.shouldMatch
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.createDraftEmailMessage].
 */
@RunWith(AndroidJUnit4::class)
class CreateDraftEmailMessageIntegrationTest : BaseIntegrationTest() {
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
    fun createDraftEmailMessageShouldFailWithBogusSenderAddress() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAddress.emailAddress,
                    to = listOf(emailAddress.emailAddress),
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, "bogusId")

            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            }
        }

    @Test
    fun createDraftEmailMessageShouldReturnUUIDOnSuccessWithOutNetworkMessage() =
        runTest {
            val uuidRegex = Regex("^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}\$")
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAddress.emailAddress,
                    to = listOf(successSimulatorAddress),
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
            val response = emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

            response shouldMatch uuidRegex
        }

    @Test
    fun createDraftEmailMessageShouldReturnUUIDOnSuccessWithInNetworkMessage() =
        runTest {
            val uuidRegex = Regex("^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}\$")
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val recipientAddress = provisionEmailAddress(emailClient, ownershipProof, mixedCaseEmail = true)
            recipientAddress shouldNotBe null
            emailAddressList.add(recipientAddress)
            // Make sure display name has special characters that require encoding
            val recipientDisplayName = "Recipient; Name"

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAddress.emailAddress,
                    to =
                        listOf(
                            EmailMessage
                                .EmailAddress(
                                    recipientAddress.emailAddress,
                                    recipientDisplayName,
                                ).toString(),
                        ),
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
            val response = emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

            response shouldMatch uuidRegex
        }
}
