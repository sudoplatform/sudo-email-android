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
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.UnsealedBlockedAddressStatus
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.getEmailAddressBlocklist]
 */
@RunWith(AndroidJUnit4::class)
class GetEmailAddressBlocklistIntegrationTest : BaseIntegrationTest() {
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
    fun getEmailAddressBlocklistReturnsEmptyArrayIfNoAddressesBlocked() =
        runTest {
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

            val result = emailClient.getEmailAddressBlocklist()

            result.size shouldBe 0
        }

    @Test
    fun getEmailAddressBlocklistReturnsUnsealedBlockedAddresses() = runTest {
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

        val result = emailClient.getEmailAddressBlocklist()

        result.size shouldBe 1
        result.first().address shouldBe emailAddressToBlock.emailAddress
        result.first().status shouldBe UnsealedBlockedAddressStatus.Completed
        result.first().hashedBlockedValue shouldNotBe null
    }
}
