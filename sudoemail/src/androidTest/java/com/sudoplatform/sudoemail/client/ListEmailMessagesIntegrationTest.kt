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
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.DateRange
import com.sudoplatform.sudoemail.types.Direction
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EmailMessageDateRange
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.SortOrder
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeLessThan
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Test the operation of [SudoEmailClient.listEmailMessages].
 */
@RunWith(AndroidJUnit4::class)
class ListEmailMessagesIntegrationTest : BaseIntegrationTest() {
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
    fun listEmailMessagesShouldReturnEmailMessageListResult() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        when (val listEmailMessages = waitForMessages(messageCount * 2)) {
            is ListAPIResult.Success -> {
                val outbound = listEmailMessages.result.items.filter {
                    it.direction == Direction.OUTBOUND
                }
                val inbound = listEmailMessages.result.items.filter {
                    it.direction == Direction.INBOUND
                }
                outbound.size shouldBe messageCount
                with(outbound[0]) {
                    from.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
                    to.firstOrNull()?.emailAddress shouldBe toSimulatorAddress
                    hasAttachments shouldBe false
                    size shouldBeGreaterThan 0.0
                }
                inbound.size shouldBe messageCount
                with(inbound[0]) {
                    from.firstOrNull()?.emailAddress shouldBe fromSimulatorAddress
                    to.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
                    hasAttachments shouldBe false
                    size shouldBeGreaterThan 0.0
                }
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesShouldReturnEmailMessageListResultForInNetworkMessage() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(
            emailClient,
            ownershipProofToken = ownershipProof,
            alias = "Ted Bear",
        )
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(
                emailClient,
                emailAddress,
                toAddresses = listOf(
                    EmailMessage.EmailAddress(emailAddress.emailAddress, emailAddress.alias),
                ),
            )
            result.id.isBlank() shouldBe false
        }

        when (val listEmailMessages = waitForMessages(messageCount * 2)) {
            is ListAPIResult.Success -> {
                val outbound = listEmailMessages.result.items.filter {
                    it.direction == Direction.OUTBOUND
                }
                val inbound = listEmailMessages.result.items.filter {
                    it.direction == Direction.INBOUND
                }
                outbound.size shouldBe messageCount
                with(outbound[0]) {
                    from shouldBe listOf(
                        EmailMessage.EmailAddress(
                            emailAddress.emailAddress,
                            emailAddress.alias,
                        ),
                    )
                    to shouldBe listOf(
                        EmailMessage.EmailAddress(
                            emailAddress.emailAddress,
                            emailAddress.alias,
                        ),
                    )
                    hasAttachments shouldBe false
                    size shouldBeGreaterThan 0.0
                }
                inbound.size shouldBe messageCount
                with(inbound[0]) {
                    from shouldBe listOf(
                        EmailMessage.EmailAddress(
                            emailAddress.emailAddress,
                            emailAddress.alias,
                        ),
                    )
                    to shouldBe listOf(
                        EmailMessage.EmailAddress(
                            emailAddress.emailAddress,
                            emailAddress.alias,
                        ),
                    )
                    hasAttachments shouldBe false
                    size shouldBeGreaterThan 0.0
                }
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesShouldRespectLimit() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        when (val listEmailMessages = waitForMessages(1, ListEmailMessagesInput(limit = 1))) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.size shouldBe 1
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesShouldRespectSortDateRange() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        val listEmailMessagesInput = ListEmailMessagesInput(
            dateRange = EmailMessageDateRange(
                sortDate = DateRange(
                    startDate = emailAddress.createdAt,
                    endDate = Date(emailAddress.createdAt.time + 100000),
                ),
            ),
        )
        when (val listEmailMessages = waitForMessages(messageCount * 2, listEmailMessagesInput)) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.size shouldBe messageCount * 2
                listEmailMessages.result.nextToken shouldBe null
                with(listEmailMessages.result) {
                    items.forEachIndexed { index, element ->
                        if (index < items.size - 1) {
                            element.sortDate.time shouldBeGreaterThan listEmailMessages.result.items[index + 1].sortDate.time
                        }
                    }
                }
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesShouldRespectUpdatedAtDateRange() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        val listEmailMessagesInput = ListEmailMessagesInput(
            dateRange = EmailMessageDateRange(
                updatedAt = DateRange(
                    startDate = emailAddress.createdAt,
                    endDate = Date(emailAddress.createdAt.time + 100000),
                ),
            ),
        )
        when (val listEmailMessages = waitForMessages(messageCount * 2, listEmailMessagesInput)) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.size shouldBe messageCount * 2
                listEmailMessages.result.nextToken shouldBe null
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesShouldReturnEmptyListForOutOfDateRangeSortDate() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        val listEmailMessagesInput = ListEmailMessagesInput(
            dateRange = EmailMessageDateRange(
                sortDate = DateRange(
                    startDate = sudo.createdAt,
                    endDate = emailAddress.createdAt,
                ),
            ),
        )
        when (val listEmailMessages = emailClient.listEmailMessages(listEmailMessagesInput)) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.isEmpty() shouldBe true
                listEmailMessages.result.nextToken shouldBe null
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesShouldReturnEmptyListForOutOfDateRangeUpdatedAtDate() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        val listEmailMessagesInput = ListEmailMessagesInput(
            dateRange = EmailMessageDateRange(
                updatedAt = DateRange(
                    startDate = sudo.createdAt,
                    endDate = emailAddress.createdAt,
                ),
            ),
        )
        when (val listEmailMessages = emailClient.listEmailMessages(listEmailMessagesInput)) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.isEmpty() shouldBe true
                listEmailMessages.result.nextToken shouldBe null
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesShouldThrowForMultipleDateRangeSpecified() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        val listEmailMessagesInput = ListEmailMessagesInput(
            dateRange = EmailMessageDateRange(
                sortDate = DateRange(
                    startDate = emailAddress.createdAt,
                    endDate = Date(emailAddress.createdAt.time + 100000),
                ),
                updatedAt = DateRange(
                    startDate = emailAddress.createdAt,
                    endDate = Date(emailAddress.createdAt.time + 100000),
                ),
            ),
        )
        shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
            emailClient.listEmailMessages(listEmailMessagesInput)
        }
    }

    @Test
    fun listEmailMessagesShouldReturnWhenNeitherDateRangeSpecified() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        val listEmailMessagesInput = ListEmailMessagesInput(
            dateRange = EmailMessageDateRange(
                sortDate = null,
                updatedAt = null,
            ),
        )
        when (val listEmailMessages = waitForMessages(messageCount * 2, listEmailMessagesInput)) {
            is ListAPIResult.Success -> {
                val outbound = listEmailMessages.result.items.filter {
                    it.direction == Direction.OUTBOUND
                }
                val inbound = listEmailMessages.result.items.filter {
                    it.direction == Direction.INBOUND
                }
                outbound.size shouldBe messageCount
                with(outbound[0]) {
                    from.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
                    to.firstOrNull()?.emailAddress shouldBe toSimulatorAddress
                    hasAttachments shouldBe false
                    size shouldBeGreaterThan 0.0
                }
                inbound.size shouldBe messageCount
                with(inbound[0]) {
                    from.firstOrNull()?.emailAddress shouldBe fromSimulatorAddress
                    to.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
                    hasAttachments shouldBe false
                    size shouldBeGreaterThan 0.0
                }
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesShouldThrowWhenInputStartDateGreaterThanEndDateForSortDateRange() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val messageCount = 2
            for (i in 0 until messageCount) {
                val result = sendEmailMessage(emailClient, emailAddress)
                result.id.isBlank() shouldBe false
            }

            val listEmailMessagesInput = ListEmailMessagesInput(
                dateRange = EmailMessageDateRange(
                    sortDate = DateRange(
                        startDate = Date(emailAddress.createdAt.time + 100000),
                        endDate = emailAddress.createdAt,
                    ),
                ),
            )
            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                emailClient.listEmailMessages(listEmailMessagesInput)
            }
        }

    @Test
    fun listEmailMessagesShouldThrowWhenInputStartDateGreaterThanEndDateForUpdatedAtDateRange() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val messageCount = 2
            for (i in 0 until messageCount) {
                val result = sendEmailMessage(emailClient, emailAddress)
                result.id.isBlank() shouldBe false
            }

            val listEmailMessagesInput = ListEmailMessagesInput(
                dateRange = EmailMessageDateRange(
                    updatedAt = DateRange(
                        startDate = Date(emailAddress.createdAt.time + 100000),
                        endDate = emailAddress.createdAt,
                    ),
                ),
            )
            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                emailClient.listEmailMessages(listEmailMessagesInput)
            }
        }

    @Test
    fun listEmailMessagesShouldReturnEmailMessageListAscending() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        val listEmailMessagesInput = ListEmailMessagesInput(
            dateRange = EmailMessageDateRange(
                sortDate = DateRange(
                    startDate = emailAddress.createdAt,
                    endDate = Date(emailAddress.createdAt.time + 100000),
                ),
            ),
            sortOrder = SortOrder.ASC,
        )
        when (val listEmailMessages = waitForMessages(messageCount * 2, listEmailMessagesInput)) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.size shouldBe messageCount * 2
                listEmailMessages.result.nextToken shouldBe null
                with(listEmailMessages.result) {
                    items.forEachIndexed { index, element ->
                        if (index < items.size - 1) {
                            element.sortDate.time shouldBeLessThan listEmailMessages.result.items[index + 1].sortDate.time
                        }
                    }
                }
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesShouldReturnEmailMessageListDescending() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        val listEmailMessagesInput = ListEmailMessagesInput(
            sortOrder = SortOrder.DESC,
        )
        when (val listEmailMessages = waitForMessages(messageCount * 2, listEmailMessagesInput)) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.size shouldBe messageCount * 2
                listEmailMessages.result.nextToken shouldBe null
                with(listEmailMessages.result) {
                    items.forEachIndexed { index, element ->
                        if (index < items.size - 1) {
                            element.sortDate.time shouldBeGreaterThan listEmailMessages.result.items[index + 1].sortDate.time
                        }
                    }
                }
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesShouldReturnPartialResult() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        waitForMessages(messageCount * 2)

        // Reset client to cause key not found errors
        emailClient.reset()

        val listEmailMessagesInput = ListEmailMessagesInput()
        when (val listEmailMessages = emailClient.listEmailMessages(listEmailMessagesInput)) {
            is ListAPIResult.Partial -> {
                listEmailMessages.result.items.size shouldBe 0
                listEmailMessages.result.failed.size shouldBe messageCount * 2
                listEmailMessages.result.nextToken shouldBe null
                listEmailMessages.result.failed[0].cause
                    .shouldBeInstanceOf<DeviceKeyManager.DeviceKeyManagerException.DecryptionException>()
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesShouldRespectIncludeDeletedMessagesFlag() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        var messageId = ""
        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
            messageId = result.id
        }

        when (val listEmailMessages = waitForMessages(messageCount * 2)) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items shouldHaveSize messageCount * 2
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val deleteResult = emailClient.deleteEmailMessage(messageId)
        deleteResult shouldNotBe null

        // Without flag
        val inputWithoutFlag = ListEmailMessagesInput()
        when (
            val listEmailMessages = emailClient.listEmailMessages(inputWithoutFlag)
        ) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items shouldHaveSize (messageCount * 2) - 1
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        // With flag
        val inputWithFlag = ListEmailMessagesInput(
            includeDeletedMessages = true,
        )
        when (
            val listEmailMessages = emailClient.listEmailMessages(inputWithFlag)
        ) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items shouldHaveSize messageCount * 2
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }
}
