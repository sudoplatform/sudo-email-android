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
import com.sudoplatform.sudoemail.types.inputs.GetEmailAddressInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeLessThan
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.delay
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
import java.util.Date

/**
 * Test the operation of [SudoEmailClient.listEmailMessagesForEmailAddressId].
 */
@RunWith(AndroidJUnit4::class)
class ListEmailMessagesForEmailAddressIdIntegrationTest : BaseIntegrationTest() {
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
    fun listEmailMessagesForEmailAddressIdShouldReturnEmailMessageListResult() = runBlocking {
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

        // Wait for all the messages to arrive
        val listEmailMessages =
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                    )
                    emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == messageCount * 2 }
        var updatedEmailAddress = emailClient.getEmailAddress(GetEmailAddressInput(emailAddress.id))
        updatedEmailAddress!!.numberOfEmailMessages shouldBe messageCount * 2

        when (listEmailMessages) {
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
    fun listEmailMessagesForEmailAddressIdShouldRespectLimit() = runBlocking {
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

        // Wait for all the messages to arrive
        val listEmailMessages =
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                        limit = 1,
                    )
                    emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == 1 }

        when (listEmailMessages) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.size shouldBe 1
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesForEmailAddressIdShouldRespectSortDateRange() = runBlocking {
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

        // Wait for all the messages to arrive
        val listEmailMessages =
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                        dateRange = EmailMessageDateRange(
                            sortDate = DateRange(
                                startDate = emailAddress.createdAt,
                                endDate = Date(emailAddress.createdAt.time + 100000),
                            ),
                        ),
                    )
                    emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == messageCount * 2 }

        when (listEmailMessages) {
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
    fun listEmailMessagesForEmailAddressIdShouldRespectUpdatedAtDateRange() = runBlocking {
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

        val listEmailMessages =
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                        dateRange = EmailMessageDateRange(
                            updatedAt = DateRange(
                                startDate = emailAddress.createdAt,
                                endDate = Date(emailAddress.createdAt.time + 100000),
                            ),
                        ),
                    )
                    emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == messageCount * 2 }

        when (listEmailMessages) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.size shouldBe messageCount * 2
                listEmailMessages.result.nextToken shouldBe null
                listEmailMessages.result.items.forEach {
                    it.emailAddressId shouldBe emailAddress.id
                }
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesForEmailAddressIdShouldReturnEmptyListForOutOfDateRangeSortDate() =
        runBlocking {
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

            val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = emailAddress.id,
                dateRange = EmailMessageDateRange(
                    sortDate = DateRange(
                        startDate = sudo.createdAt,
                        endDate = emailAddress.createdAt,
                    ),
                ),
            )
            when (
                val listEmailMessages =
                    emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
            ) {
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
    fun listEmailMessagesForEmailAddressIdShouldReturnEmptyListForOutOfDateRangeUpdatedAtDate() =
        runBlocking {
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

            val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = emailAddress.id,
                dateRange = EmailMessageDateRange(
                    updatedAt = DateRange(
                        startDate = sudo.createdAt,
                        endDate = emailAddress.createdAt,
                    ),
                ),
            )
            when (
                val listEmailMessages =
                    emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
            ) {
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
    fun listEmailMessagesForEmailAddressIdShouldThrowForMultipleDateRangeSpecified() =
        runBlocking<Unit> {
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

            val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = emailAddress.id,
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
                emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
            }
        }

    @Test
    fun listEmailMessagesForEmailAddressIdShouldThrowWhenInputStartDateGreaterThanEndDateForSortDateRange() =
        runBlocking<Unit> {
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

            val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = emailAddress.id,
                dateRange = EmailMessageDateRange(
                    sortDate = DateRange(
                        startDate = Date(emailAddress.createdAt.time + 100000),
                        endDate = emailAddress.createdAt,
                    ),
                ),
            )
            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
            }
        }

    @Test
    fun listEmailMessagesForEmailAddressIdShouldThrowWhenInputStartDateGreaterThanEndDateForUpdatedAtDateRange() =
        runBlocking<Unit> {
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

            val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = emailAddress.id,
                dateRange = EmailMessageDateRange(
                    updatedAt = DateRange(
                        startDate = Date(emailAddress.createdAt.time + 100000),
                        endDate = emailAddress.createdAt,
                    ),
                ),
            )
            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
            }
        }

    @Test
    fun listEmailMessagesForEmailAddressIdShouldReturnEmailMessageListAscending() = runBlocking {
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

        // Wait for all the messages to arrive
        val listEmailMessages =
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                        dateRange = EmailMessageDateRange(
                            sortDate = DateRange(
                                startDate = emailAddress.createdAt,
                                endDate = Date(emailAddress.createdAt.time + 100000),
                            ),
                        ),
                        sortOrder = SortOrder.ASC,
                    )
                    emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == messageCount * 2 }

        when (listEmailMessages) {
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
    fun listEmailMessagesForEmailAddressIdShouldReturnEmailMessageListDescending() = runBlocking {
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

        // Wait for all the messages to arrive
        val listEmailMessages =
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                        sortOrder = SortOrder.DESC,
                    )
                    emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == messageCount * 2 }

        when (listEmailMessages) {
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
    fun listEmailMessagesForEmailAddressIdShouldReturnEmptyListForNonExistingEmailAddress() =
        runBlocking {
            val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
                emailAddressId = "nonExistentId",
            )
            when (
                val listEmailMessages =
                    emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
            ) {
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
    fun listEmailMessagesForEmailAddressIdShouldReturnPartialResult() = runBlocking {
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
        delay(2000)

        // Reset client to cause key not found errors
        emailClient.reset()

        val listEmailMessagesInput = ListEmailMessagesForEmailAddressIdInput(
            emailAddressId = emailAddress.id,
        )
        when (
            val listEmailMessages =
                emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
        ) {
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
}
