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
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of [SudoEmailClient.unblockEmailAddresses]
 */
@RunWith(AndroidJUnit4::class)
class UnblockEmailAddressesIntegrationTest : BaseIntegrationTest() {
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

    private fun blockAddresses(addresses: List<String>) = runTest {
        val result = emailClient.blockEmailAddresses(addresses)
        result.status shouldBe BatchOperationStatus.SUCCESS
    }

    @Test
    fun unblockEmailAddressesThrowsAnErrorIfPassedAnEmptyAddressesArray() =
        runTest {
            shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                emailClient.unblockEmailAddresses(emptyList())
            }
        }

    @Test
    fun unblockEmailAddressesShouldThrowAnErrorIfPassedDuplicateAddresses() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof)
        emailAddressToBlock shouldNotBe null
        emailAddressList.add(emailAddressToBlock)

        shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
            emailClient.unblockEmailAddresses(
                listOf(
                    emailAddressToBlock.emailAddress.lowercase(),
                    emailAddressToBlock.emailAddress.uppercase(),
                ),
            )
        }
    }

    @Test
    fun unblockingAnAddressThatIsNotBlockedShouldReturnSuccess() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof)
        emailAddressToBlock shouldNotBe null
        emailAddressList.add(emailAddressToBlock)

        val result = emailClient.unblockEmailAddresses(listOf(emailAddressToBlock.emailAddress))
        result.status shouldBe BatchOperationStatus.SUCCESS
    }

    @Test
    fun unblockingABlockedAddressShouldReturnSuccess() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof)
        emailAddressToBlock shouldNotBe null
        emailAddressList.add(emailAddressToBlock)

        blockAddresses(listOf(emailAddressToBlock.emailAddress))

        val result = emailClient.unblockEmailAddresses(listOf(emailAddressToBlock.emailAddress))
        result.status shouldBe BatchOperationStatus.SUCCESS
    }

    @Test
    fun unblockingMultipleBlockedAddressesShouldReturnSuccess() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof)
        emailAddressToBlock shouldNotBe null
        emailAddressList.add(emailAddressToBlock)

        val spamAddress = "spammyMcSpamface${UUID.randomUUID()}@spambot.com"
        blockAddresses(listOf(emailAddressToBlock.emailAddress, spamAddress))

        val result = emailClient.unblockEmailAddresses(
            listOf(
                emailAddressToBlock.emailAddress,
                spamAddress,
            ),
        )
        result.status shouldBe BatchOperationStatus.SUCCESS
    }
}
