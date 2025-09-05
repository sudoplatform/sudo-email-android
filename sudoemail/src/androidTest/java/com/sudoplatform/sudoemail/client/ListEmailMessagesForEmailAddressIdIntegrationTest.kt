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
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.types.DateRange
import com.sudoplatform.sudoemail.types.Direction
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EmailMessageDateRange
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.PartialEmailMessage
import com.sudoplatform.sudoemail.types.SortOrder
import com.sudoplatform.sudoemail.types.State
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.matchers.numerics.shouldBeLessThan
import io.kotlintest.matchers.numerics.shouldBeLessThanOrEqual
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.awaitility.kotlin.has
import org.awaitility.kotlin.untilCallTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.UUID

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
    fun teardown() =
        runTest {
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
            sudoList.map { sudoClient.deleteSudo(it) }
            sudoClient.reset()
        }

    @Test
    fun listEmailMessagesForEmailAddressIdShouldReturnEmailMessageListResult() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput()
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items
                        .first()
                        .emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val fromEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            fromEmailAddress shouldNotBe null
            emailAddressList.add(fromEmailAddress)

            val messageCount = 2
            for (i in 0 until messageCount) {
                sendEmailMessage(
                    client = emailClient,
                    fromAddress = fromEmailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = emailAddress.emailAddress)),
                    subject =
                        "listEmailMessagesForEmailAddressIdShouldReturnEmailMessageListResult" +
                            " ${UUID.randomUUID()}",
                )
            }

            when (
                val listEmailMessages =
                    waitForMessages(
                        messageCount,
                        ListEmailMessagesForEmailAddressIdInput(
                            emailAddress.id,
                        ),
                    )
            ) {
                is ListAPIResult.Success -> {
                    val outbound =
                        listEmailMessages.result.items.filter {
                            it.direction == Direction.OUTBOUND
                        }
                    val inbound =
                        listEmailMessages.result.items.filter {
                            it.direction == Direction.INBOUND
                        }
                    outbound.size shouldBe 0
                    inbound.size shouldBe messageCount
                    with(inbound[0]) {
                        from.firstOrNull()?.emailAddress shouldBe fromEmailAddress.emailAddress
                        to.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
                        hasAttachments shouldBe false
                        size shouldBeGreaterThan 0.0
                        date.shouldBeInstanceOf<Date>()
                    }
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailMessagesForEmailAddressIdShouldReturnEmailMessageListResultForInNetworkMessage() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress =
                provisionEmailAddress(
                    emailClient,
                    ownershipProofToken = ownershipProof,
                    alias = "Ted Bear",
                )
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val messageCount = 2
            for (i in 0 until messageCount) {
                val result =
                    sendEmailMessage(
                        emailClient,
                        emailAddress,
                        toAddresses =
                            listOf(
                                EmailMessage.EmailAddress(emailAddress.emailAddress, emailAddress.alias),
                            ),
                    )
                result.id.isBlank() shouldBe false
            }

            when (
                val listEmailMessages =
                    waitForMessages(
                        messageCount * 2,
                        ListEmailMessagesForEmailAddressIdInput(
                            emailAddress.id,
                        ),
                    )
            ) {
                is ListAPIResult.Success -> {
                    val outbound =
                        listEmailMessages.result.items.filter {
                            it.direction == Direction.OUTBOUND
                        }
                    val inbound =
                        listEmailMessages.result.items.filter {
                            it.direction == Direction.INBOUND
                        }
                    outbound.size shouldBe messageCount
                    with(outbound[0]) {
                        from shouldBe
                            listOf(
                                EmailMessage.EmailAddress(
                                    emailAddress.emailAddress,
                                    emailAddress.alias,
                                ),
                            )
                        to shouldBe
                            listOf(
                                EmailMessage.EmailAddress(
                                    emailAddress.emailAddress,
                                    emailAddress.alias,
                                ),
                            )
                        hasAttachments shouldBe false
                        size shouldBeGreaterThan 0.0
                        date.shouldBeInstanceOf<Date>()
                    }
                    inbound.size shouldBe messageCount
                    with(inbound[0]) {
                        from shouldBe
                            listOf(
                                EmailMessage.EmailAddress(
                                    emailAddress.emailAddress,
                                    emailAddress.alias,
                                ),
                            )
                        to shouldBe
                            listOf(
                                EmailMessage.EmailAddress(
                                    emailAddress.emailAddress,
                                    emailAddress.alias,
                                ),
                            )
                        hasAttachments shouldBe false
                        size shouldBeGreaterThan 0.0
                        date.shouldBeInstanceOf<Date>()
                    }
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailMessagesForEmailAddressIdShouldRespectLimit() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput()
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items
                        .first()
                        .emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val fromEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            fromEmailAddress shouldNotBe null
            emailAddressList.add(fromEmailAddress)

            val messageCount = 2
            for (i in 0 until messageCount) {
                sendEmailMessage(
                    client = emailClient,
                    fromAddress = fromEmailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = emailAddress.emailAddress)),
                    subject =
                        "listEmailMessagesForEmailAddressIdShouldRespectLimit" +
                            " ${UUID.randomUUID()}",
                )
            }

            // First, wait for all the messages to be there
            waitForMessagesByAddress(
                messageCount,
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                ),
            )

            // Now read them all paginated
            var nextToken: String? = null
            do {
                val listEmailMessagesInput =
                    ListEmailMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                        limit = 1,
                        nextToken = nextToken,
                    )

                when (val listEmailMessages = emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)) {
                    is ListAPIResult.Success -> {
                        listEmailMessages.result.items.size shouldBeLessThanOrEqual 1
                        nextToken = listEmailMessages.result.nextToken
                    }

                    else -> {
                        fail("Unable to list email messages for folder")
                    }
                }
            } while (nextToken != null)
        }

    @Test
    fun listEmailMessagesForEmailAddressIdShouldRespectSortDateRange() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput()
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items
                        .first()
                        .emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val fromEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            fromEmailAddress shouldNotBe null
            emailAddressList.add(fromEmailAddress)

            val messageCount = 3
            for (i in 0 until messageCount) {
                sendEmailMessage(
                    client = emailClient,
                    fromAddress = fromEmailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = emailAddress.emailAddress)),
                    subject =
                        "listEmailMessagesForEmailAddressIdShouldRespectSortDateRange" +
                            " ${UUID.randomUUID()}",
                )
            }

            val listEmailMessagesInput =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                    dateRange =
                        EmailMessageDateRange(
                            sortDate =
                                DateRange(
                                    startDate = emailAddress.createdAt,
                                    endDate = Date(emailAddress.createdAt.time + 100000),
                                ),
                        ),
                )
            when (
                val listEmailMessages =
                    waitForMessages(
                        messageCount,
                        listEmailMessagesInput,
                    )
            ) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.size shouldBe messageCount
                    listEmailMessages.result.nextToken shouldBe null
                    with(listEmailMessages.result) {
                        items.forEachIndexed { index, element ->
                            if (index < items.size - 1) {
                                element.sortDate.time shouldBeGreaterThan
                                    listEmailMessages.result.items[index + 1]
                                        .sortDate.time
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
    fun listEmailMessagesForEmailAddressIdShouldRespectUpdatedAtDateRange() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput()
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items
                        .first()
                        .emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val fromEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            fromEmailAddress shouldNotBe null
            emailAddressList.add(fromEmailAddress)

            val messageCount = 2
            for (i in 0 until messageCount) {
                sendEmailMessage(
                    client = emailClient,
                    fromAddress = fromEmailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = emailAddress.emailAddress)),
                    subject =
                        "listEmailMessagesForEmailAddressIdShouldRespectUpdatedAtDateRange" +
                            " ${UUID.randomUUID()}",
                )
            }

            val listEmailMessagesInput =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                    dateRange =
                        EmailMessageDateRange(
                            updatedAt =
                                DateRange(
                                    startDate = emailAddress.createdAt,
                                    endDate = Date(emailAddress.createdAt.time + 100000),
                                ),
                        ),
                )
            when (
                val listEmailMessages =
                    waitForMessages(
                        messageCount,
                        listEmailMessagesInput,
                    )
            ) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.size shouldBe messageCount
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
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput()
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items
                        .first()
                        .emailAddress shouldBe emailAddress.emailAddress
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

            val listEmailMessagesInput =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                    dateRange =
                        EmailMessageDateRange(
                            sortDate =
                                DateRange(
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
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput()
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items
                        .first()
                        .emailAddress shouldBe emailAddress.emailAddress
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

            val listEmailMessagesInput =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                    dateRange =
                        EmailMessageDateRange(
                            updatedAt =
                                DateRange(
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
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput()
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items
                        .first()
                        .emailAddress shouldBe emailAddress.emailAddress
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

            val listEmailMessagesInput =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                    dateRange =
                        EmailMessageDateRange(
                            sortDate =
                                DateRange(
                                    startDate = emailAddress.createdAt,
                                    endDate = Date(emailAddress.createdAt.time + 100000),
                                ),
                            updatedAt =
                                DateRange(
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
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput()
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items
                        .first()
                        .emailAddress shouldBe emailAddress.emailAddress
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

            val listEmailMessagesInput =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                    dateRange =
                        EmailMessageDateRange(
                            sortDate =
                                DateRange(
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
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput()
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items
                        .first()
                        .emailAddress shouldBe emailAddress.emailAddress
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

            val listEmailMessagesInput =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                    dateRange =
                        EmailMessageDateRange(
                            updatedAt =
                                DateRange(
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
    fun listEmailMessagesForEmailAddressIdShouldReturnEmailMessageListAscending() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput()
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items
                        .first()
                        .emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val fromEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            fromEmailAddress shouldNotBe null
            emailAddressList.add(fromEmailAddress)

            val messageCount = 3
            for (i in 0 until messageCount) {
                sendEmailMessage(
                    client = emailClient,
                    fromAddress = fromEmailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = emailAddress.emailAddress)),
                    subject =
                        "listEmailMessagesForEmailAddressIdShouldReturnEmailMessageListAscending" +
                            " ${UUID.randomUUID()}",
                )
            }

            val listEmailMessagesInput =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                    dateRange =
                        EmailMessageDateRange(
                            sortDate =
                                DateRange(
                                    startDate = emailAddress.createdAt,
                                    endDate = Date(emailAddress.createdAt.time + 100000),
                                ),
                        ),
                    sortOrder = SortOrder.ASC,
                )
            when (
                val listEmailMessages =
                    waitForMessages(
                        messageCount,
                        listEmailMessagesInput,
                    )
            ) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.size shouldBe messageCount
                    listEmailMessages.result.nextToken shouldBe null
                    with(listEmailMessages.result) {
                        items.forEachIndexed { index, element ->
                            if (index < items.size - 1) {
                                element.sortDate.time shouldBeLessThan
                                    listEmailMessages.result.items[index + 1]
                                        .sortDate.time
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
    fun listEmailMessagesForEmailAddressIdShouldReturnEmailMessageListDescending() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput()
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items
                        .first()
                        .emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val fromEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            fromEmailAddress shouldNotBe null
            emailAddressList.add(fromEmailAddress)

            val messageCount = 3
            for (i in 0 until messageCount) {
                sendEmailMessage(
                    client = emailClient,
                    fromAddress = fromEmailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = emailAddress.emailAddress)),
                    subject =
                        "listEmailMessagesForEmailAddressIdShouldReturnEmailMessageListDescending" +
                            " ${UUID.randomUUID()}",
                )
            }

            val listEmailMessagesInput =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                    sortOrder = SortOrder.DESC,
                )
            when (
                val listEmailMessages =
                    waitForMessages(
                        messageCount,
                        listEmailMessagesInput,
                    )
            ) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.size shouldBe messageCount
                    listEmailMessages.result.nextToken shouldBe null
                    with(listEmailMessages.result) {
                        items.forEachIndexed { index, element ->
                            if (index < items.size - 1) {
                                element.sortDate.time shouldBeGreaterThan
                                    listEmailMessages.result.items[index + 1]
                                        .sortDate.time
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
        runTest {
            val listEmailMessagesInput =
                ListEmailMessagesForEmailAddressIdInput(
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
    fun listEmailMessagesForEmailAddressIdShouldReturnPartialResult() =
        runTest(timeout = kotlin.time.Duration.parse("2m")) {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput()
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items
                        .first()
                        .emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val fromEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            fromEmailAddress shouldNotBe null
            emailAddressList.add(fromEmailAddress)

            val messageCount = 2
            for (i in 0 until messageCount) {
                sendEmailMessage(
                    client = emailClient,
                    fromAddress = fromEmailAddress,
                    toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = emailAddress.emailAddress)),
                    subject =
                        "listEmailMessagesForEmailAddressIdShouldReturnPartialResult" +
                            " ${UUID.randomUUID()}",
                )
            }

            val listEmailMessagesInput =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddress.id,
                )

            waitForMessages(messageCount, listEmailMessagesInput)

            // Reset client to cause key not found errors
            emailClient.reset()

            when (
                val listEmailMessages =
                    emailClient.listEmailMessagesForEmailAddressId(listEmailMessagesInput)
            ) {
                is ListAPIResult.Partial -> {
                    listEmailMessages.result.items.size shouldBe 0
                    listEmailMessages.result.failed.size shouldBe messageCount
                    listEmailMessages.result.nextToken shouldBe null
                    listEmailMessages.result.failed[0]
                        .cause
                        .shouldBeInstanceOf<DeviceKeyManager.DeviceKeyManagerException.DecryptionException>()
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailMessagesForEmailAddressIdShouldRespectIncludeDeletedMessagesFlag() =
        runTest(timeout = kotlin.time.Duration.parse("3m")) {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesInput()
            when (val listEmailAddresses = emailClient.listEmailAddresses(input)) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items
                        .first()
                        .emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            var messageId = ""
            val fromEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            fromEmailAddress shouldNotBe null
            emailAddressList.add(fromEmailAddress)

            val messageCount = 3
            for (i in 0 until messageCount) {
                val sendResult =
                    sendEmailMessage(
                        client = emailClient,
                        fromAddress = fromEmailAddress,
                        toAddresses = listOf(EmailMessage.EmailAddress(emailAddress = emailAddress.emailAddress)),
                        subject =
                            "listEmailMessagesForEmailAddressIdShouldRespectIncludeDeletedMessagesFlag" +
                                " ${UUID.randomUUID()}",
                    )
                messageId = sendResult.id
            }
            when (
                val listEmailMessages =
                    waitForMessages(
                        messageCount,
                        ListEmailMessagesForEmailAddressIdInput(
                            fromEmailAddress.id,
                        ),
                    )
            ) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items shouldHaveSize messageCount
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val deleteResult = emailClient.deleteEmailMessage(messageId)
            deleteResult shouldNotBe null

            // Without flag
            val inputWithoutFlag =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = fromEmailAddress.id,
                )
            when (
                val listEmailMessages = emailClient.listEmailMessagesForEmailAddressId(inputWithoutFlag)
            ) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items shouldHaveSize messageCount - 1
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            // With flag
            val inputWithFlag =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = fromEmailAddress.id,
                    includeDeletedMessages = true,
                )
            when (
                val listEmailMessages = emailClient.listEmailMessagesForEmailAddressId(inputWithFlag)
            ) {
                is ListAPIResult.Success -> {
                    // Allowing for messages that were retried and deleted here
                    listEmailMessages.result.items.size shouldBeGreaterThanOrEqual messageCount
                    listEmailMessages.result.items
                        .first { it.id == messageId }
                        .state shouldBe State.DELETED
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    /** Wait for multiple messages to arrive. */
    private fun waitForMessages(
        count: Int,
        listInput: ListEmailMessagesForEmailAddressIdInput,
    ): ListAPIResult<EmailMessage, PartialEmailMessage> =
        await
            .atMost(Duration.ONE_MINUTE)
            .pollInterval(Duration.TWO_HUNDRED_MILLISECONDS)
            .untilCallTo {
                runBlocking {
                    with(emailClient) {
                        listEmailMessagesForEmailAddressId(listInput)
                    }
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == count }
}
