/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageWithBodyInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.util.Date
import java.util.UUID
import kotlin.time.Duration

/**
 * These tests are designed to ensure that our service is able to successfully send and
 * receive emails between us and Gmail, Yahoo, Outlook, iCloud, and Proton. We have set up accounts
 * in each of those services (credentials can be found in the Sudo Platform Engineering
 * vault of 1Password). They each have an auto-reply message set up (except Proton
 * because they charge you for that feature), so we send a message to each account
 * and await the auto-reply, ensuring that they body text is as expected.
 */
@RunWith(value = Parameterized::class)
class MailServiceInteroperabilityTest(private val externalAddress: String) : BaseIntegrationTest() {

    companion object {
        @JvmStatic
        @Parameters
        fun data(): List<String> {
            return listOf(
                "sudo.platform.testing@gmail.com",
                "sudo_platform_testing@yahoo.com",
                "sudo.platform.testing@outlook.com",
                "sudo.platform.testing@proton.me",
//                "sudoplatformtesting@icloud.com", // No iCloud testing for now until we can get a long-lived user with a consistent address
            )
        }
    }

    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

    @Before
    fun setup() {
        sudoClient.reset()
        sudoClient.generateEncryptionKey()
    }

    @After
    fun teardown() = runTest {
        emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        sudoList.map { sudoClient.deleteSudo(it) }
        sudoClient.reset()
    }

    @Test
    fun shouldBeAbleToSendToAndReceiveFromEachExternalAddress() =
        runTest(timeout = Duration.parse("2m")) {
            val enabled = isInteropTestEnabled() // Set variable to true in order to run test case
            Assume.assumeTrue("Test skipped due to email interop tests not being enabled.", enabled)

            val timestamp = Date().toString()
            logger.info("externalAddress: $externalAddress")
            logger.info("timestamp: $timestamp")
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress =
                provisionEmailAddress(emailClient, ownershipProof, prefix = "em-test-interop-an-")
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val listInput = ListEmailAddressesInput()
            when (val listEmailAddresses = emailClient.listEmailAddresses(listInput)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val attachment = EmailAttachment(
                fileName = "goodExtension.pdf",
                contentId = UUID.randomUUID().toString(),
                mimeType = "application/pdf",
                inlineAttachment = false,
                data = "This file has a valid file extension".toByteArray(),
            )

            val sendResult = sendEmailMessage(
                emailClient,
                emailAddress,
                subject = "Test $timestamp",
                body = "Test Body $timestamp",
                attachments = listOf(attachment),
                toAddresses = listOf(EmailMessage.EmailAddress(externalAddress)),
            )
            sendResult.id.isBlank() shouldBe false
            sendResult.createdAt shouldNotBe null

            if (
                !externalAddress.endsWith("proton.me") && // Auto-replies are a paid feature in proton, so not checking for it here
                !externalAddress.endsWith("yahoo.com") // And yahoo keeps sending us to spam and not sending auto-reply
            ) {
                val incomingMessageId: String
                val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
                    ?: fail("EmailFolder could not be found")

                when (
                    val messageList = waitForMessagesByFolder(
                        1,
                        ListEmailMessagesForEmailFolderIdInput(
                            inboxFolder.id,
                        ),
                    )
                ) {
                    is ListAPIResult.Success -> {
                        val inbound = messageList.result.items
                        inbound.size shouldBe 1
                        inbound[0].id shouldNotBe null
                        incomingMessageId = inbound[0].id
                    }

                    else -> {
                        fail("Unexpected ListAPIResult")
                    }
                }

                emailClient.getEmailMessage(GetEmailMessageInput(incomingMessageId))
                    ?: fail("Email message not found")

                val messageWithBodyInput =
                    GetEmailMessageWithBodyInput(incomingMessageId, emailAddress.id)
                val result = emailClient.getEmailMessageWithBody(messageWithBodyInput)
                    ?: throw AssertionError("should not be null")
                result.id shouldNotBe null
                result.body shouldContain "Message received. This is an auto-reply"
                result.attachments.isEmpty() shouldBe true
                result.inlineAttachments.isEmpty() shouldBe true
            }
        }

    private fun isInteropTestEnabled(): Boolean {
        var enabled = false
        val argumentValue =
            InstrumentationRegistry.getArguments().getString("ENABLE_EMAIL_INTEROP_TESTS")?.trim()
        if (argumentValue != null) {
            enabled = argumentValue.toBoolean()
        }
        // disable until a more reliable design is implemented, see PEMC-1441
        return false
    }
}
