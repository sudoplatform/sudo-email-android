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
import com.sudoplatform.sudoemail.types.inputs.ProvisionEmailAddressInput
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.deprovisionEmailAddress].
 */
@RunWith(AndroidJUnit4::class)
class DeprovisionEmailAddressIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()

    @After
    fun teardown() =
        runTest {
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        }

    @Test
    fun deprovisionEmailAddressShouldReturnEmailAddressResult() =
        runTest {
            val emailDomains = getEmailDomains(emailClient)
            emailDomains.size shouldBeGreaterThanOrEqual 1

            val ownershipProof = sharedOwnershipProof()

            val localPart = generateSafeLocalPart()
            val emailAddress = localPart + "@" + emailDomains.first()
            val aliasInput = "John Doe"
            val provisionInput =
                ProvisionEmailAddressInput(
                    emailAddress = emailAddress,
                    ownershipProofToken = ownershipProof,
                    displayName = aliasInput,
                )
            val provisionedAddress = emailClient.provisionEmailAddress(provisionInput)
            provisionedAddress shouldNotBe null

            val deprovisionedEmailAddress = emailClient.deprovisionEmailAddress(provisionedAddress.id)
            deprovisionedEmailAddress shouldNotBe null

            with(deprovisionedEmailAddress) {
                id shouldBe provisionedAddress.id
                owner shouldBe provisionedAddress.owner
                owners shouldBe provisionedAddress.owners
                deprovisionedEmailAddress.emailAddress shouldBe provisionedAddress.emailAddress
                size shouldBe provisionedAddress.size
                version shouldBe provisionedAddress.version
                createdAt.time shouldBe provisionedAddress.createdAt.time
                updatedAt.time shouldBe provisionedAddress.updatedAt.time
                lastReceivedAt shouldBe provisionedAddress.lastReceivedAt
            }
        }

    @Test
    fun deprovisionEmailAddressShouldThrowWithNonExistentAddress() =
        runTest {
            val emailDomains = getEmailDomains(emailClient)
            emailDomains.size shouldBeGreaterThanOrEqual 1

            val localPart = generateSafeLocalPart()
            val emailAddressInput = localPart + "@" + emailDomains.first()
            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                emailClient.deprovisionEmailAddress(emailAddressInput)
            }
        }
}
