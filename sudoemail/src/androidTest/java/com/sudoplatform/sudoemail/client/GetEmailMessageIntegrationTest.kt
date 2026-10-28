/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import io.kotlintest.fail
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Test the operation of [SudoEmailClient.getEmailMessage].
 */
@RunWith(AndroidJUnit4::class)
class GetEmailMessageIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()

    @After
    fun teardown() =
        runTest {
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        }

    @Test
    fun getEmailMessageShouldReturnEmailMessageResult() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false

            waitForMessage(result.id)

            val getMessageInput = GetEmailMessageInput(result.id)
            val retrievedEmailMessage =
                emailClient.getEmailMessage(getMessageInput)
                    ?: throw AssertionError("should not be null")

            with(retrievedEmailMessage) {
                from.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
                to.firstOrNull()?.emailAddress shouldBe successSimulatorAddress
                hasAttachments shouldBe false
                size shouldBeGreaterThan 0.0
                date.shouldBeInstanceOf<Date>()
            }
        }

    @Test
    fun getEmailMessageShouldReturnEmailMessageResultForOutOfNetworkMessageWithAlias() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress =
                provisionEmailAddress(
                    emailClient,
                    ownershipProofToken = ownershipProof,
                    alias = "Ted Bear",
                )
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val sendResult =
                sendEmailMessage(
                    emailClient,
                    emailAddress,
                    toAddresses =
                        listOf(
                            EmailMessage.EmailAddress(successSimulatorAddress, emailAddress.displayName),
                        ),
                )
            sendResult.id.isBlank() shouldBe false

            waitForMessage(sendResult.id)

            val retrievedEmailMessage =
                emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
                    ?: fail("Email message not found")
            with(retrievedEmailMessage) {
                from.firstOrNull() shouldBe
                    EmailMessage.EmailAddress(
                        emailAddress.emailAddress,
                        emailAddress.displayName,
                    )
                to shouldBe listOf(EmailMessage.EmailAddress(successSimulatorAddress, emailAddress.displayName))
                hasAttachments shouldBe false
                size shouldBeGreaterThan 0.0
                date.shouldBeInstanceOf<Date>()
            }
        }

    @Test
    fun getEmailMessageShouldReturnEmailMessageResultForInNetworkMessageWithAlias() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress =
                provisionEmailAddress(
                    emailClient,
                    ownershipProofToken = ownershipProof,
                    alias = "Ted Bear",
                )
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val sendResult =
                sendEmailMessage(
                    emailClient,
                    emailAddress,
                    toAddresses =
                        listOf(
                            EmailMessage.EmailAddress(emailAddress.emailAddress, emailAddress.displayName),
                        ),
                )
            sendResult.id.isBlank() shouldBe false

            waitForMessage(sendResult.id)

            val retrievedEmailMessage =
                emailClient.getEmailMessage(GetEmailMessageInput(sendResult.id))
                    ?: fail("Email message not found")
            with(retrievedEmailMessage) {
                from.firstOrNull() shouldBe
                    EmailMessage.EmailAddress(
                        emailAddress.emailAddress,
                        emailAddress.displayName,
                    )
                to shouldBe
                    listOf(
                        EmailMessage.EmailAddress(
                            emailAddress.emailAddress,
                            emailAddress.displayName,
                        ),
                    )
                hasAttachments shouldBe false
                size shouldBeGreaterThan 0.0
                date.shouldBeInstanceOf<Date>()
            }
        }

    @Test
    fun getEmailMessageShouldReturnNullForNonExistentMessage() =
        runTest {
            val getMessageInput = GetEmailMessageInput("nonExistentId")
            val retrievedEmailMessage = emailClient.getEmailMessage(getMessageInput)
            retrievedEmailMessage shouldBe null
        }
}
