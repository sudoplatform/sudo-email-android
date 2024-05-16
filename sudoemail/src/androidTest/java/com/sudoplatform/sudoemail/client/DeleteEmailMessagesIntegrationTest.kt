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
import com.sudoplatform.sudoemail.types.inputs.GetEmailAddressInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.awaitility.kotlin.has
import org.awaitility.kotlin.untilCallTo
import org.awaitility.kotlin.withPollInterval
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.deleteEmailMessages].
 */
@RunWith(AndroidJUnit4::class)
class DeleteEmailMessagesIntegrationTest : BaseIntegrationTest() {
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
    fun deleteEmailMessagesShouldReturnSuccessWhenDeletingMultipleMessages() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddress.numberOfEmailMessages shouldBe 0
        emailAddressList.add(emailAddress)

        val messageCount = 2
        val sentEmailIds = mutableSetOf<String>()
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
            sentEmailIds.add(result.id)
        }
        sentEmailIds.size shouldBeGreaterThan 0

        waitForMessages(messageCount * 2)

        var updatedEmailAddress = emailClient.getEmailAddress(GetEmailAddressInput(emailAddress.id))
        updatedEmailAddress!!.numberOfEmailMessages shouldBe messageCount * 2

        val result = emailClient.deleteEmailMessages(sentEmailIds.toList())
        result.status shouldBe BatchOperationStatus.SUCCESS

        updatedEmailAddress = emailClient.getEmailAddress(GetEmailAddressInput(emailAddress.id))
        updatedEmailAddress!!.numberOfEmailMessages shouldBe messageCount
    }

    @Test
    fun deleteEmailMessagesShouldReturnPartialResultWhenDeletingExistingAndNonExistingMessages() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val messageCount = 2
            val sentEmailIds = mutableSetOf<String>()
            for (i in 0 until messageCount) {
                val result = sendEmailMessage(emailClient, emailAddress)
                result.id.isBlank() shouldBe false
                sentEmailIds.add(result.id)
            }
            sentEmailIds.size shouldBeGreaterThan 0

            waitForMessages(messageCount * 2)

            val nonExistentIds = listOf("nonExistentId")
            val input = mutableListOf<String>()
            input.addAll(sentEmailIds)
            input.addAll(nonExistentIds)

            val result = emailClient.deleteEmailMessages(input)
            result.status shouldBe BatchOperationStatus.PARTIAL
            result.successValues shouldContainExactlyInAnyOrder sentEmailIds
            result.failureValues shouldContainExactlyInAnyOrder nonExistentIds

            await.atMost(Duration.TEN_SECONDS) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    emailClient.getEmailAddress(GetEmailAddressInput(emailAddress.id))
                }
            } has { this.numberOfEmailMessages == messageCount }
        }

    @Test
    fun deleteEmailMessagesShouldThrowWithInvalidArgumentWithEmptyInput() = runTest {
        shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
            emailClient.deleteEmailMessages(emptyList())
        }
    }

    @Test
    fun deleteEmailMessagesShouldReturnFailureWhenDeletingMultipleNonExistingMessages() = runTest {
        val result = emailClient.deleteEmailMessages(listOf("id1", "id2", "id3"))
        result.status shouldBe BatchOperationStatus.FAILURE
    }

    @Test
    fun deleteEmailMessagesShouldAllowInputLimitEdgeCase() = runTest {
        val input = Array(100) { it.toString() }.toList()
        val result = emailClient.deleteEmailMessages(input)
        result.status shouldBe BatchOperationStatus.FAILURE
    }

    @Test
    fun deleteEmailMessagesShouldThrowWhenInputLimitExceeded() = runTest {
        val input = Array(101) { it.toString() }.toList()
        shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
            emailClient.deleteEmailMessages(input)
        }
    }
}
