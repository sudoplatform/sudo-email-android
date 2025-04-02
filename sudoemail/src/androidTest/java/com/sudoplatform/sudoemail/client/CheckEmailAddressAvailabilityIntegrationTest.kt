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
import com.sudoplatform.sudoemail.types.inputs.CheckEmailAddressAvailabilityInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.checkEmailAddressAvailability].
 */
@RunWith(AndroidJUnit4::class)
class CheckEmailAddressAvailabilityIntegrationTest : BaseIntegrationTest() {
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
    fun checkEmailAddressAvailabilityShouldSucceed() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailDomains = getEmailDomains(emailClient)
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val localPart1 = generateSafeLocalPart()
        val localPart2 = generateSafeLocalPart()
        val localParts = listOf(
            localPart1,
            localPart2,
        )

        var input = CheckEmailAddressAvailabilityInput(
            localParts,
            domains = emailDomains,
        )
        val emailAddresses = emailClient.checkEmailAddressAvailability(input)
        emailAddresses.isEmpty() shouldBe false
        emailAddresses.forEach { address ->
            val parts = address.split("@")
            emailDomains shouldContain parts[1]

            // Provision it to prove it's real
            val provisionedAddress = provisionEmailAddress(emailClient, ownershipProof, address)
            emailClient.deprovisionEmailAddress(provisionedAddress.id)
        }

        // Check without the domains
        input = CheckEmailAddressAvailabilityInput(
            localParts,
            domains = null,
        )
        emailClient.checkEmailAddressAvailability(input)
    }

    @Test
    fun checkEmailAddressAvailabilityWithBadInputShouldFail() = runTest {
        val emailDomains = getEmailDomains(emailClient)
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val localPart = generateSafeLocalPart()
        val localParts = listOf(localPart)

        var input = CheckEmailAddressAvailabilityInput(
            localParts,
            domains = listOf("gmail.com"),
        )
        shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
            emailClient.checkEmailAddressAvailability(input)
        }

        input = CheckEmailAddressAvailabilityInput(
            localParts = listOf("foo@gmail.com"),
            domains = emailDomains,
        )
        shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
            emailClient.checkEmailAddressAvailability(input)
        }

        input = CheckEmailAddressAvailabilityInput(
            localParts = listOf(""),
            domains = emailDomains,
        )
        shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
            emailClient.checkEmailAddressAvailability(input)
        }
    }
}
