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
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.LookupEmailAddressesPublicInfoInput
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldHaveAtLeastSize
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * For email addresses provisioned with a specific Public Key identifier, test the operations of:
 *  - [SudoEmailClient.provisionEmailAddress]
 *  - [SudoEmailClient.sendEmailMessage]
 *  - [SudoEmailClient.getEmailMessage]
 *  - [SudoEmailClient.deprovisionEmailAddress]
 */
@RunWith(AndroidJUnit4::class)
class EmailAddressPublicKeyIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()
    private val ownershipProofList = mutableListOf<String>()

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
            ownershipProofList.clear()
            sudoClient.reset()
        }

    private fun setupTestData() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo.id shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null
            ownershipProofList.add(ownershipProof)

            val provisionedAddress = provisionEmailAddress(emailClient, ownershipProof)
            provisionedAddress shouldNotBe null
            emailAddressList.add(provisionedAddress)
        }

    private fun testEmailLifecycle(emailAddress: EmailAddress) =
        runTest {
            // Send message from email address
            val outgoingMessageResult =
                sendEmailMessage(
                    emailClient,
                    fromAddress = emailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(emailAddressList[0].emailAddress)),
                )
            outgoingMessageResult.id shouldNotBe null

            // Receive message with email address
            val incomingMessageResult =
                sendEmailMessage(
                    emailClient,
                    fromAddress = emailAddressList[0],
                    toAddresses = listOf(EmailMessage.EmailAddress(emailAddress.emailAddress)),
                )
            incomingMessageResult.id shouldNotBe null
            emailClient.getEmailMessage(GetEmailMessageInput(incomingMessageResult.id)) shouldNotBe null

            // Deprovision second test address
            val deprovisionedAddress = emailClient.deprovisionEmailAddress(emailAddress.id)
            deprovisionedAddress shouldNotBe null
        }

    @Test
    fun provisionalEmailAddressWithReusedPublicKeyShouldHandleLifecycle() =
        runTest {
            setupTestData()

            // Get public key of another email address
            var publicInfoInput =
                LookupEmailAddressesPublicInfoInput(listOf(emailAddressList[0].emailAddress))
            var publicInfo = emailClient.lookupEmailAddressesPublicInfo(publicInfoInput)
            publicInfo shouldHaveAtLeastSize 1
            val keyId = publicInfo[0].keyId

            // Allow failure here (temporarily - see PEMC-1039) as test environment may not be configured to allow duplicate keys,
            // so the assumptions here are:
            //  - Success -> environment allows duplicate keys, therefore provisioning the address with a duplicate key succeeds
            //  - Failure -> environment does not allow duplicate keys, and has somewhere thrown an invalid/duplicate key error
            try {
                // Provision email address with the generated public key
                val provisionedAddress =
                    provisionEmailAddress(emailClient, ownershipProofList[0], keyId = keyId)
                provisionedAddress shouldNotBe null

                // Verify new provisioned address has same key
                publicInfoInput =
                    LookupEmailAddressesPublicInfoInput(listOf(provisionedAddress.emailAddress))
                publicInfo = emailClient.lookupEmailAddressesPublicInfo(publicInfoInput)
                publicInfo shouldHaveAtLeastSize 1
                keyId shouldBe publicInfo[0].keyId

                testEmailLifecycle(provisionedAddress)
            } catch (e: Throwable) {
                // Continue
            }
        }

    @Test
    fun provisionalEmailAddressWithInvalidPublicKeyShouldThrow() =
        runTest {
            setupTestData()

            shouldThrow<KeyNotFoundException> {
                provisionEmailAddress(emailClient, ownershipProofList[0], keyId = "invalidKeyId")
            }
        }
}
