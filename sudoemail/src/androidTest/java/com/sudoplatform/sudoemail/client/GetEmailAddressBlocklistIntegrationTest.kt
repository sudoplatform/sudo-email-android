/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.BlockedEmailAddressAction
import com.sudoplatform.sudoemail.types.BlockedEmailAddressLevel
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.UnsealedBlockedAddressStatus
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of [SudoEmailClient.getEmailAddressBlocklist]
 */
@RunWith(AndroidJUnit4::class)
class GetEmailAddressBlocklistIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val hashedBlockedValueList = mutableListOf<String>()

    @After
    fun teardown() =
        runTest {
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
            if (hashedBlockedValueList.isNotEmpty()) {
                emailClient.unblockEmailAddressesByHashedValue(hashedBlockedValueList)
                hashedBlockedValueList.clear()
            }
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

    /**
     * Generates a unique, email-shaped block target. blockEmailAddresses only
     * hashes the value, so it need not be a provisioned address.
     */
    private fun generateBlockTargetAddress(): String = "blocked-${UUID.randomUUID()}@spambot.com"

    @Test
    fun getEmailAddressBlocklistReturnsEmptyArrayIfNoAddressesBlocked() =
        runTest {
            val result = emailClient.getEmailAddressBlocklist()

            result.size shouldBe 0
        }

    @Test
    fun getEmailAddressBlocklistReturnsUnsealedBlockedAddresses() =
        runTest {
            val ownershipProof = sharedOwnershipProof()
            ownershipProof shouldNotBe null

            // A real provisioned address is required here because the client still need the symmetric key
            // generated at provision time to encrypt the blocked address
            val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
            receiverEmailAddress shouldNotBe null
            emailAddressList.add(receiverEmailAddress)

            // blockEmailAddresses only hashes the address string, so the blocked
            // target does not need to be a provisioned address.
            val addressToBlock = generateBlockTargetAddress()
            blockAddresses(listOf(addressToBlock))

            val result = emailClient.getEmailAddressBlocklist()

            result.size shouldBe 1
            result.first().address shouldBe addressToBlock
            result.first().status shouldBe UnsealedBlockedAddressStatus.Completed
            result.first().hashedBlockedValue shouldNotBe null
            result.first().action shouldBe BlockedEmailAddressAction.DROP
            result.first().emailAddressId shouldBe null

            result.forEach { hashedBlockedValueList.add(it.hashedBlockedValue) }
        }

    @Test
    fun getEmailAddressBlocklistReturnsMultipleUnsealedBlockedAddresses() =
        runTest {
            val addressToBlock = generateBlockTargetAddress()
            val outOfNetworkAddressToBlock = "spammyMcSpamface${UUID.randomUUID()}@spambot.com"
            blockAddresses(
                listOf(
                    addressToBlock,
                    outOfNetworkAddressToBlock,
                ),
                action = BlockedEmailAddressAction.SPAM,
            )

            val result = emailClient.getEmailAddressBlocklist()

            result.size shouldBe 2
            val blockedInNetworkEmailAddress = result.filter { it.address == addressToBlock }
            blockedInNetworkEmailAddress.size shouldBe 1
            blockedInNetworkEmailAddress.first().address shouldBe addressToBlock
            blockedInNetworkEmailAddress.first().status shouldBe UnsealedBlockedAddressStatus.Completed
            blockedInNetworkEmailAddress.first().hashedBlockedValue shouldNotBe null
            blockedInNetworkEmailAddress.first().action shouldBe BlockedEmailAddressAction.SPAM
            blockedInNetworkEmailAddress.first().emailAddressId shouldBe null

            result.forEach { hashedBlockedValueList.add(it.hashedBlockedValue) }
        }

    @Test
    fun getEmailAddressBlocklistReturnsUnsealedBlockedAddressesIncludingEmailAddressId() =
        runTest {
            val ownershipProof = sharedOwnershipProof()
            ownershipProof shouldNotBe null

            // A real provisioned address is required here because the block is scoped
            // to its id; the blocked target itself can be an arbitrary address string.
            val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
            receiverEmailAddress shouldNotBe null
            emailAddressList.add(receiverEmailAddress)

            val addressToBlock = generateBlockTargetAddress()
            blockAddresses(listOf(addressToBlock), emailAddressId = receiverEmailAddress.id)

            val result = emailClient.getEmailAddressBlocklist()

            result.size shouldBe 1
            result.first().address shouldBe addressToBlock
            result.first().status shouldBe UnsealedBlockedAddressStatus.Completed
            result.first().hashedBlockedValue shouldNotBe null
            result.first().action shouldBe BlockedEmailAddressAction.DROP
            result.first().emailAddressId shouldBe receiverEmailAddress.id

            result.forEach { hashedBlockedValueList.add(it.hashedBlockedValue) }
        }

    @Test
    fun getEmailAddressBlocklistDoesNotReturnAddressThatHasBeenUnblocked() =
        runTest {
            val ownershipProof = sharedOwnershipProof()
            ownershipProof shouldNotBe null

            // A real provisioned address is required here because the client still need the symmetric key
            // generated at provision time to encrypt the blocked address
            val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
            receiverEmailAddress shouldNotBe null
            emailAddressList.add(receiverEmailAddress)

            val addressToBlock = generateBlockTargetAddress()
            val outOfNetworkAddressToBlock = "spammyMcSpamface${UUID.randomUUID()}@spambot.com"
            val addressesToBlock =
                listOf(
                    addressToBlock,
                    outOfNetworkAddressToBlock,
                )
            blockAddresses(
                addressesToBlock,
                action = BlockedEmailAddressAction.SPAM,
            )

            var result = emailClient.getEmailAddressBlocklist()

            result.size shouldBe addressesToBlock.size

            result.forEach { hashedBlockedValueList.add(it.hashedBlockedValue) }

            val hashedValueToUnblock = result.first { it.address == addressToBlock }.hashedBlockedValue
            emailClient.unblockEmailAddressesByHashedValue(listOf(hashedValueToUnblock))

            result = emailClient.getEmailAddressBlocklist()

            result.size shouldBe addressesToBlock.size - 1
        }

    @Test
    fun getEmailAddressBlocklistReturnsAddressBlockedAtDomainLevel() =
        runTest {
            val ownershipProof = sharedOwnershipProof()
            ownershipProof shouldNotBe null

            // A real provisioned address is required here because the client still need the symmetric key
            // generated at provision time to encrypt the blocked address
            val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
            receiverEmailAddress shouldNotBe null
            emailAddressList.add(receiverEmailAddress)

            val addressToBlock = generateBlockTargetAddress()
            val domainToBlock = addressToBlock.split("@").last()
            blockAddresses(listOf(addressToBlock), level = BlockedEmailAddressLevel.DOMAIN)

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
