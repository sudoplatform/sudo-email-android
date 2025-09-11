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
import com.sudoplatform.sudoemail.types.BlockedEmailAddressAction
import com.sudoplatform.sudoemail.types.BlockedEmailAddressLevel
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
    private val hashedBlockedValueList = mutableListOf<String>()

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
            if (hashedBlockedValueList.isNotEmpty()) {
                emailClient.unblockEmailAddressesByHashedValue(hashedBlockedValueList)
                hashedBlockedValueList.clear()
            }
            sudoClient.reset()
        }

    private fun blockAddresses(
        addresses: List<String>,
        action: BlockedEmailAddressAction = BlockedEmailAddressAction.DROP,
        emailAddressId: String? = null,
        level: BlockedEmailAddressLevel? = BlockedEmailAddressLevel.ADDRESS,
    ) = runTest {
        val result = emailClient.blockEmailAddresses(addresses, action, emailAddressId, level)
        result.status shouldBe BatchOperationStatus.SUCCESS
    }

    @Test
    fun getEmailAddressBlocklistReturnsEmptyArrayIfNoAddressesBlocked() =
        runTest {
            val result = emailClient.getEmailAddressBlocklist()

            result.size shouldBe 0
        }

    @Test
    fun getEmailAddressBlocklistReturnsUnsealedBlockedAddresses() =
        runTest {
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

            result.forEach { hashedBlockedValueList.add(it.hashedBlockedValue) }
        }

    @Test
    fun getEmailAddressBlocklistReturnsMultipleUnsealedBlockedAddresses() =
        runTest {
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
            val outOfNetworkAddressToBlock = "spammyMcSpamface${UUID.randomUUID()}@spambot.com"
            blockAddresses(
                listOf(
                    emailAddressToBlock.emailAddress,
                    outOfNetworkAddressToBlock,
                ),
                action = BlockedEmailAddressAction.SPAM,
            )

            val result = emailClient.getEmailAddressBlocklist()

            result.size shouldBe 2
            val blockedInNetworkEmailAddress = result.filter { it.address == emailAddressToBlock.emailAddress }
            blockedInNetworkEmailAddress.size shouldBe 1
            blockedInNetworkEmailAddress.first().address shouldBe emailAddressToBlock.emailAddress
            blockedInNetworkEmailAddress.first().status shouldBe UnsealedBlockedAddressStatus.Completed
            blockedInNetworkEmailAddress.first().hashedBlockedValue shouldNotBe null
            blockedInNetworkEmailAddress.first().action shouldBe BlockedEmailAddressAction.SPAM
            blockedInNetworkEmailAddress.first().emailAddressId shouldBe null

            result.forEach { hashedBlockedValueList.add(it.hashedBlockedValue) }
        }

    @Test
    fun getEmailAddressBlocklistReturnsUnsealedBlockedAddressesIncludingEmailAddressId() =
        runTest {
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

            result.forEach { hashedBlockedValueList.add(it.hashedBlockedValue) }
        }

    @Test
    fun getEmailAddressBlocklistDoesNotReturnAddressThatHasBeenUnblocked() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddressToBlock = provisionEmailAddress(emailClient, ownershipProof, prefix = "sender-${UUID.randomUUID()}")
            emailAddressToBlock shouldNotBe null
            emailAddressList.add(emailAddressToBlock)
            val outOfNetworkAddressToBlock = "spammyMcSpamface${UUID.randomUUID()}@spambot.com"
            val addressesToBlock =
                listOf(
                    emailAddressToBlock.emailAddress,
                    outOfNetworkAddressToBlock,
                )
            blockAddresses(
                addressesToBlock,
                action = BlockedEmailAddressAction.SPAM,
            )

            var result = emailClient.getEmailAddressBlocklist()

            result.size shouldBe addressesToBlock.size

            result.forEach { hashedBlockedValueList.add(it.hashedBlockedValue) }

            emailClient.unblockEmailAddresses(listOf(emailAddressToBlock.emailAddress))

            result = emailClient.getEmailAddressBlocklist()

            result.size shouldBe addressesToBlock.size - 1
        }

    @Test
    fun getEmailAddressBlocklistReturnsAddressBlockedAtDomainLevel() =
        runTest {
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
            val domainToBlock = emailAddressToBlock.emailAddress.split("@").last()
            blockAddresses(listOf(emailAddressToBlock.emailAddress), level = BlockedEmailAddressLevel.DOMAIN)

            val result = emailClient.getEmailAddressBlocklist()

            result.size shouldBe 1
            result.first().address shouldBe domainToBlock
            result.first().status shouldBe UnsealedBlockedAddressStatus.Completed
            result.first().hashedBlockedValue shouldNotBe null
            result.first().action shouldBe BlockedEmailAddressAction.DROP
            result.first().emailAddressId shouldBe null

            result.forEach { hashedBlockedValueList.add(it.hashedBlockedValue) }
        }
}
