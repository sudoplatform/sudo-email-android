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
import com.sudoplatform.sudoemail.types.inputs.GetEmailAddressInput
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.getEmailAddress].
 */
@RunWith(AndroidJUnit4::class)
class GetEmailAddressIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()

    @After
    fun teardown() =
        runTest {
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        }

    @Test
    fun getEmailAddressShouldReturnEmailAddressResult() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val aliasInput = "John Doe"
            val emailAddress = provisionEmailAddress(emailClient, ownershipProof, alias = aliasInput)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val getAddressInput = GetEmailAddressInput(emailAddress.id)
            val retrievedEmailAddress =
                emailClient.getEmailAddress(getAddressInput)
                    ?: throw AssertionError("should not be null")

            with(retrievedEmailAddress) {
                id shouldBe emailAddress.id
                owner shouldBe emailAddress.owner
                owners shouldBe emailAddress.owners
                retrievedEmailAddress.emailAddress shouldBe emailAddress.emailAddress
                size shouldBe emailAddress.size
                numberOfEmailMessages shouldBe 0
                version shouldBe emailAddress.version
                createdAt.time shouldBe emailAddress.createdAt.time
                updatedAt.time shouldBe emailAddress.createdAt.time
                lastReceivedAt shouldBe emailAddress.lastReceivedAt
                displayName shouldBe aliasInput
            }
            when (retrievedEmailAddress.folders.size) {
                4 -> {
                    retrievedEmailAddress.folders.map { it.folderName } shouldContainExactlyInAnyOrder
                        listOf("INBOX", "SENT", "TRASH", "OUTBOX")
                }
                5 -> {
                    retrievedEmailAddress.folders.map { it.folderName } shouldContainExactlyInAnyOrder
                        listOf("INBOX", "SENT", "TRASH", "OUTBOX", "SPAM")
                }
                else -> {
                    throw AssertionError("Unexpected number of folders: ${retrievedEmailAddress.folders.size}")
                }
            }
        }

    @Test
    fun getEmailAddressShouldReturnNullForNonExistentAddress() =
        runTest {
            val emailDomains = getEmailDomains(emailClient)
            emailDomains.size shouldBeGreaterThanOrEqual 1

            val localPart = generateSafeLocalPart()
            val emailAddressInput = localPart + "@" + emailDomains.first()
            val getAddressInput = GetEmailAddressInput(emailAddressInput)
            val retrievedEmailAddress = emailClient.getEmailAddress(getAddressInput)
            retrievedEmailAddress shouldBe null
        }
}
