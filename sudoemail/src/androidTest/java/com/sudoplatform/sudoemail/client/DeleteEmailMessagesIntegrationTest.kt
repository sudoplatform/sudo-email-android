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
import com.sudoplatform.sudoemail.types.BatchOperationResult
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.GetEmailAddressInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
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
    fun teardown() = runBlocking {
        emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        sudoList.map { sudoClient.deleteSudo(it) }
        sudoClient.reset()
    }

    @Test
    fun deleteEmailMessagesShouldReturnSuccessWhenDeletingMultipleMessages() = runBlocking {
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

        // Wait for all the messages to arrive
        await.atMost(Duration.TEN_SECONDS.multiply(9)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
            runBlocking {
                val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                )
                emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
            }
        } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == messageCount * 2 }
        var updatedEmailAddress = emailClient.getEmailAddress(GetEmailAddressInput(emailAddress.id))
        updatedEmailAddress!!.numberOfEmailMessages shouldBe messageCount * 2

        when (val result = emailClient.deleteEmailMessages(sentEmailIds.toList())) {
            is BatchOperationResult.SuccessOrFailureResult -> {
                result.status shouldBe BatchOperationStatus.SUCCESS
            }

            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }
        updatedEmailAddress = emailClient.getEmailAddress(GetEmailAddressInput(emailAddress.id))
        updatedEmailAddress!!.numberOfEmailMessages shouldBe messageCount
    }

    @Test
    fun deleteEmailMessagesShouldReturnPartialResultWhenDeletingExistingAndNonExistingMessages() =
        runBlocking {
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

            // Wait for all the messages to arrive
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                    )
                    emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == messageCount * 2 }

            val nonExistentIds = listOf("nonExistentId")
            val input = mutableListOf<String>()
            input.addAll(sentEmailIds)
            input.addAll(nonExistentIds)

            when (val result = emailClient.deleteEmailMessages(input)) {
                is BatchOperationResult.PartialResult -> {
                    result.status shouldBe BatchOperationStatus.PARTIAL
                    result.successValues shouldContainExactlyInAnyOrder sentEmailIds
                    result.failureValues shouldContainExactlyInAnyOrder nonExistentIds
                }

                else -> {
                    fail("Unexpected BatchOperationResult")
                }
            }
            val updatedEmailAddress =
                emailClient.getEmailAddress(GetEmailAddressInput(emailAddress.id))
            updatedEmailAddress!!.numberOfEmailMessages shouldBe messageCount
        }

    @Test
    fun deleteEmailMessagesShouldThrowWithInvalidArgumentWithEmptyInput() = runBlocking<Unit> {
        shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
            emailClient.deleteEmailMessages(emptyList())
        }
    }

    @Test
    fun deleteEmailMessagesShouldReturnFailureWhenDeletingMultipleNonExistingMessages() =
        runBlocking {
            when (val result = emailClient.deleteEmailMessages(listOf("id1", "id2", "id3"))) {
                is BatchOperationResult.SuccessOrFailureResult -> {
                    result.status shouldBe BatchOperationStatus.FAILURE
                }

                else -> {
                    fail("Unexpected BatchOperationResult")
                }
            }
        }

    @Test
    fun deleteEmailMessagesShouldAllowInputLimitEdgeCase() = runBlocking {
        val input = Array(100) { it.toString() }.toList()
        when (val result = emailClient.deleteEmailMessages(input)) {
            is BatchOperationResult.SuccessOrFailureResult -> {
                result.status shouldBe BatchOperationStatus.FAILURE
            }

            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }
    }

    @Test
    fun deleteEmailMessagesShouldThrowWhenInputLimitExceeded() = runBlocking<Unit> {
        val input = Array(101) { it.toString() }.toList()
        shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
            emailClient.deleteEmailMessages(input)
        }
    }
}
