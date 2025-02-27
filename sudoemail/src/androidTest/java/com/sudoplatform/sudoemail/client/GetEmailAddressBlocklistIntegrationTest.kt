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
import com.sudoplatform.sudoemail.types.BlockedEmailAddressAction
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
import java.util.UUID

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

    private fun blockAddresses(
        addresses: List<String>,
        action: BlockedEmailAddressAction = BlockedEmailAddressAction.DROP,
        emailAddressId: String? = null,
    ) = runTest {
        val result = emailClient.blockEmailAddresses(addresses, action, emailAddressId)
        result.status shouldBe BatchOperationStatus.SUCCESS
    }

    @Test
    fun getEmailAddressBlocklistReturnsEmptyArrayIfNoAddressesBlocked() =
        runTest {
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

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender-${UUID.randomUUID()}")
        emailAddressToBlock shouldNotBe null
        emailAddressList.add(emailAddressToBlock)

        blockAddresses(listOf(emailAddressToBlock.emailAddress))

        val result = emailClient.getEmailAddressBlocklist()

        result.size shouldBe 1
        result.first().address shouldBe emailAddressToBlock.emailAddress
        result.first().status shouldBe UnsealedBlockedAddressStatus.Completed
        result.first().hashedBlockedValue shouldNotBe null
        result.first().action shouldBe BlockedEmailAddressAction.DROP
        result.first().emailAddressId shouldBe null

        emailClient.unblockEmailAddresses(listOf(emailAddressToBlock.emailAddress))
    }

    @Test
    fun getEmailAddressBlocklistReturnsMultipleUnsealedBlockedAddresses() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender-${UUID.randomUUID()}")
        emailAddressToBlock shouldNotBe null
        emailAddressList.add(emailAddressToBlock)

        blockAddresses(
            listOf(
                emailAddressToBlock.emailAddress,
                "spammyMcSpamface${UUID.randomUUID()}@spambot.com",
            ),
            action = BlockedEmailAddressAction.SPAM,
        )

        val result = emailClient.getEmailAddressBlocklist()

        result.size shouldBe 2
        result.first().address shouldBe emailAddressToBlock.emailAddress
        result.first().status shouldBe UnsealedBlockedAddressStatus.Completed
        result.first().hashedBlockedValue shouldNotBe null
        result.first().action shouldBe BlockedEmailAddressAction.SPAM
        result.first().emailAddressId shouldBe null

        emailClient.unblockEmailAddresses(
            listOf(
                emailAddressToBlock.emailAddress,
                "spammyMcSpamface${UUID.randomUUID()}@spambot.com",
            ),
        )
    }

    @Test
    fun getEmailAddressBlocklistReturnsUnsealedBlockedAddressesIncludingEmailAddressId() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
        receiverEmailAddress shouldNotBe null
        emailAddressList.add(receiverEmailAddress)

        val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender-${UUID.randomUUID()}")
        emailAddressToBlock shouldNotBe null
        emailAddressList.add(emailAddressToBlock)

        blockAddresses(listOf(emailAddressToBlock.emailAddress), emailAddressId = receiverEmailAddress.id)

        val result = emailClient.getEmailAddressBlocklist()

        result.size shouldBe 1
        result.first().address shouldBe emailAddressToBlock.emailAddress
        result.first().status shouldBe UnsealedBlockedAddressStatus.Completed
        result.first().hashedBlockedValue shouldNotBe null
        result.first().action shouldBe BlockedEmailAddressAction.DROP
        result.first().emailAddressId shouldBe receiverEmailAddress.id

        emailClient.unblockEmailAddressesByHashedValue(listOf(result.first().hashedBlockedValue))
    }
}
