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
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of [SudoEmailClient.unblockEmailAddressesByHashedValue]
 */
@RunWith(AndroidJUnit4::class)
class UnblockEmailAddressesByHashedValueIntegrationTest : BaseIntegrationTest() {
    @After
    fun teardown() =
        runTest {
            // Clear any blocklist entries left by a failed test (the happy path
            // unblocks its own entries). No addresses/sudos are provisioned here.
            val blocklist = emailClient.getEmailAddressBlocklist()
            if (blocklist.isNotEmpty()) {
                emailClient.unblockEmailAddressesByHashedValue(blocklist.map { it.hashedBlockedValue })
            }
        }

    private fun blockAddresses(addresses: List<String>) =
        runTest {
            val result = emailClient.blockEmailAddresses(addresses)
            result.status shouldBe BatchOperationStatus.SUCCESS
        }

    @Test
    fun unblockEmailAddressesThrowsAnErrorIfPassedAnEmptyAddressesArray() =
        runTest {
            shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                emailClient.unblockEmailAddressesByHashedValue(emptyList())
            }
        }

    @Test
    fun unblockingABlockedAddressShouldReturnSuccess() =
        runTest {
            val ownershipProof = sharedOwnershipProof()
            ownershipProof shouldNotBe null

            // A real provisioned address is required here because the client still need the symmetric key
            // generated at provision time to encrypt the blocked address
            val receiverEmailAddress = provisionEmailAddress(emailClient, ownershipProof, prefix = "receiver-${UUID.randomUUID()}")
            receiverEmailAddress shouldNotBe null

            blockAddresses(listOf(generateBlockTargetAddress()))

            val blocklist = emailClient.getEmailAddressBlocklist()

            val hashedValues = blocklist.map { it.hashedBlockedValue }

            val result = emailClient.unblockEmailAddressesByHashedValue(hashedValues)
            result.status shouldBe BatchOperationStatus.SUCCESS
        }

    private fun generateBlockTargetAddress(): String = "blocked-${UUID.randomUUID()}@spambot.com"
}
