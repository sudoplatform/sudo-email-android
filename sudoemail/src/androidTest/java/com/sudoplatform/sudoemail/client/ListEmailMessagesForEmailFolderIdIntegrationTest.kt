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
import com.sudoplatform.sudoemail.types.DateRange
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EmailMessageDateRange
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.SortOrder
import com.sudoplatform.sudoemail.types.State
import com.sudoplatform.sudoemail.types.inputs.CreateCustomEmailFolderInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailMessagesInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeLessThan
import io.kotlintest.matchers.numerics.shouldBeLessThanOrEqual
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
 * Test the operation of [SudoEmailClient.listEmailMessagesForEmailFolderId].
 */
@RunWith(AndroidJUnit4::class)
class ListEmailMessagesForEmailFolderIdIntegrationTest : BaseIntegrationTest() {
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
    fun listEmailMessagesForEmailFolderIdShouldReturnEmailMessageListResult() = runTest {
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
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
            ?: fail("EmailFolder could not be found")

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        when (
            val listEmailMessages = waitForMessagesByFolder(
                messageCount,
                ListEmailMessagesForEmailFolderIdInput(
                    inboxFolder.id,
                ),
            )
        ) {
            is ListAPIResult.Success -> {
                val inbound = listEmailMessages.result.items
                inbound.size shouldBe messageCount
                with(inbound[0]) {
                    from.firstOrNull()?.emailAddress shouldBe fromSimulatorAddress
                    to.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
                    hasAttachments shouldBe false
                    size shouldBeGreaterThan 0.0
                    folderId shouldBe inboxFolder.id
                    date.shouldBeInstanceOf<Date>()
                }
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesForEmailFolderIdShouldReturnEmailMessageListResultForInNetworkMessage() =
        runTest {
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

            val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
                ?: fail("EmailFolder could not be found")

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

            when (
                val listEmailMessages = waitForMessagesByFolder(
                    messageCount,
                    ListEmailMessagesForEmailFolderIdInput(
                        inboxFolder.id,
                    ),
                )
            ) {
                is ListAPIResult.Success -> {
                    val inbound = listEmailMessages.result.items
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
                        folderId shouldBe inboxFolder.id
                        date.shouldBeInstanceOf<Date>()
                    }
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailMessagesForEmailFolderIdShouldRespectLimit() = runTest {
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

        val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
            ?: fail("EmailFolder could not be found")

        // First, wait for all the messages to be there
        waitForMessagesByFolder(
            2,
            ListEmailMessagesForEmailFolderIdInput(
                inboxFolder.id,
            ),
        )

        // Now read them all paginated
        var nextToken: String? = null
        do {
            val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
                folderId = inboxFolder.id,
                limit = 1,
                nextToken = nextToken,
            )

            when (val listEmailMessages = emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)) {
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
    fun listEmailMessagesForEmailFolderIdShouldRespectSortDateRange() = runTest {
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
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
            ?: fail("EmailFolder could not be found")

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
            folderId = inboxFolder.id,
            dateRange = EmailMessageDateRange(
                sortDate = DateRange(
                    startDate = emailAddress.createdAt,
                    endDate = Date(emailAddress.createdAt.time + 100000),
                ),
            ),
        )
        when (
            val listEmailMessages = waitForMessagesByFolder(
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
    fun listEmailMessagesForEmailFolderIdShouldRespectUpdatedAtDateRange() = runTest {
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
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
            ?: fail("EmailFolder could not be found")

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
            folderId = inboxFolder.id,
            dateRange = EmailMessageDateRange(
                updatedAt = DateRange(
                    startDate = emailAddress.createdAt,
                    endDate = Date(emailAddress.createdAt.time + 100000),
                ),
            ),
        )
        when (
            val listEmailMessages = waitForMessagesByFolder(
                messageCount,
                listEmailMessagesInput,
            )
        ) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.size shouldBe messageCount
                listEmailMessages.result.nextToken shouldBe null
                listEmailMessages.result.items.forEach {
                    it.folderId shouldBe inboxFolder.id
                }
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesForEmailFolderIdShouldReturnEmptyListForOutOfDateRangeSortDate() =
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
                    listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
                ?: fail("EmailFolder could not be found")

            val messageCount = 2
            for (i in 0 until messageCount) {
                val result = sendEmailMessage(emailClient, emailAddress)
                result.id.isBlank() shouldBe false
            }

            val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
                folderId = inboxFolder.id,
                dateRange = EmailMessageDateRange(
                    sortDate = DateRange(
                        startDate = sudo.createdAt,
                        endDate = emailAddress.createdAt,
                    ),
                ),
            )
            when (
                val listEmailMessages =
                    emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
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
    fun listEmailMessagesForEmailFolderIdShouldReturnEmptyListForOutOfDateRangeUpdatedAtDate() =
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
                    listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
                ?: fail("EmailFolder could not be found")

            val messageCount = 2
            for (i in 0 until messageCount) {
                val result = sendEmailMessage(emailClient, emailAddress)
                result.id.isBlank() shouldBe false
            }

            val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
                folderId = inboxFolder.id,
                dateRange = EmailMessageDateRange(
                    updatedAt = DateRange(
                        startDate = sudo.createdAt,
                        endDate = emailAddress.createdAt,
                    ),
                ),
            )
            when (
                val listEmailMessages =
                    emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
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
    fun listEmailMessagesForEmailFolderIdShouldThrowForMultipleDateRangeSpecified() =
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
                    listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
                ?: fail("EmailFolder could not be found")

            val messageCount = 2
            for (i in 0 until messageCount) {
                val result = sendEmailMessage(emailClient, emailAddress)
                result.id.isBlank() shouldBe false
            }

            val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
                folderId = inboxFolder.id,
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
                emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
            }
        }

    @Test
    fun listEmailMessagesForEmailFolderIdShouldThrowWhenInputStartDateGreaterThanEndDateForSortDateRange() =
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
                    listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
                ?: fail("EmailFolder could not be found")

            val messageCount = 2
            for (i in 0 until messageCount) {
                val result = sendEmailMessage(emailClient, emailAddress)
                result.id.isBlank() shouldBe false
            }

            val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
                folderId = inboxFolder.id,
                dateRange = EmailMessageDateRange(
                    sortDate = DateRange(
                        startDate = Date(emailAddress.createdAt.time + 100000),
                        endDate = emailAddress.createdAt,
                    ),
                ),
            )
            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
            }
        }

    @Test
    fun listEmailMessagesForEmailFolderIdShouldThrowWhenInputStartDateGreaterThanEndDateForUpdatedAtDateRange() =
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
                    listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
                ?: fail("EmailFolder could not be found")

            val messageCount = 2
            for (i in 0 until messageCount) {
                val result = sendEmailMessage(emailClient, emailAddress)
                result.id.isBlank() shouldBe false
            }

            val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
                folderId = inboxFolder.id,
                dateRange = EmailMessageDateRange(
                    updatedAt = DateRange(
                        startDate = Date(emailAddress.createdAt.time + 100000),
                        endDate = emailAddress.createdAt,
                    ),
                ),
            )
            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
            }
        }

    @Test
    fun listEmailMessagesForEmailFolderIdShouldReturnEmailMessageListAscending() = runTest {
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
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
            ?: fail("EmailFolder could not be found")

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
            folderId = inboxFolder.id,
            dateRange = EmailMessageDateRange(
                sortDate = DateRange(
                    startDate = emailAddress.createdAt,
                    endDate = Date(emailAddress.createdAt.time + 100000),
                ),
            ),
            sortOrder = SortOrder.ASC,
        )
        when (
            val listEmailMessages = waitForMessagesByFolder(
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
    fun listEmailMessagesForEmailFolderIdShouldReturnEmailMessageListDescending() = runTest {
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
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
            ?: fail("EmailFolder could not be found")

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
            folderId = inboxFolder.id,
            sortOrder = SortOrder.DESC,
        )
        when (
            val listEmailMessages = waitForMessagesByFolder(
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
    fun listEmailMessagesForEmailFolderIdShouldReturnEmptyListWhenFolderContainsNoMessages() =
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
                    listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            val trashFolder = getFolderByName(emailClient, emailAddress.id, "TRASH")
                ?: fail("EmailFolder could not be found")

            val messageCount = 2
            for (i in 0 until messageCount) {
                val result = sendEmailMessage(emailClient, emailAddress)
                result.id.isBlank() shouldBe false
            }

            val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
                folderId = trashFolder.id,
                sortOrder = SortOrder.DESC,
            )
            when (
                val listEmailMessages = waitForMessagesByFolder(
                    0,
                    listEmailMessagesInput,
                )
            ) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.size shouldBe 0
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailMessagesForEmailFolderIdShouldReturnEmptyListForNonExistingEmailFolder() =
        runTest {
            val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
                folderId = "nonExistentId",
            )
            when (
                val listEmailMessages =
                    emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
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
    fun listEmailMessagesForEmailFolderIdShouldReturnPartialResult() = runTest {
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
                listEmailAddresses.result.items.first().emailAddress shouldBe emailAddress.emailAddress
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
            ?: fail("EmailFolder could not be found")

        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
        }

        waitForMessages(messageCount * 2)

        // Reset client to cause key not found errors
        emailClient.reset()

        val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
            folderId = inboxFolder.id,
        )
        when (
            val listEmailMessages =
                emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
        ) {
            is ListAPIResult.Partial -> {
                listEmailMessages.result.items.size shouldBe 0
                listEmailMessages.result.failed.size shouldBe messageCount
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
    fun listEmailMessagesForEmailFolderIdShouldRespectIncludeDeletedMessagesFlag() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val sentFolder = getFolderByName(emailClient, emailAddress.id, "SENT")
            ?: fail("EmailFolder could not be found")

        val input = ListEmailAddressesInput()
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
        when (
            val listEmailMessages = waitForMessagesByFolder(
                messageCount,
                ListEmailMessagesForEmailFolderIdInput(
                    sentFolder.id,
                ),
            )
        ) {
            is ListAPIResult.Success -> {
                val inbound = listEmailMessages.result.items
                inbound.size shouldBe messageCount
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val deleteResult = emailClient.deleteEmailMessage(messageId)
        deleteResult shouldNotBe null

        // Without flag
        val inputWithoutFlag = ListEmailMessagesForEmailFolderIdInput(
            folderId = sentFolder.id,
        )
        when (
            val listEmailMessages = emailClient.listEmailMessagesForEmailFolderId(inputWithoutFlag)
        ) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items shouldHaveSize messageCount - 1
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        // With flag
        val inputWithFlag = ListEmailMessagesForEmailFolderIdInput(
            folderId = sentFolder.id,
            includeDeletedMessages = true,
        )
        when (
            val listEmailMessages = emailClient.listEmailMessagesForEmailFolderId(inputWithFlag)
        ) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items shouldHaveSize messageCount
                listEmailMessages.result.items.first { it.id == messageId }.state shouldBe State.DELETED
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesForEmailFolderIdShouldWorkForCustomFolders() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val sentFolder = getFolderByName(emailClient, emailAddress.id, "SENT")
            ?: fail("EmailFolder could not be found")

        var messageId = ""
        val messageCount = 2
        for (i in 0 until messageCount) {
            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false
            messageId = result.id
        }
        when (
            val listEmailMessages = waitForMessagesByFolder(
                messageCount,
                ListEmailMessagesForEmailFolderIdInput(
                    sentFolder.id,
                ),
            )
        ) {
            is ListAPIResult.Success -> {
                val messages = listEmailMessages.result.items
                messages.size shouldBe messageCount
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }

        val customFolder = emailClient.createCustomEmailFolder(CreateCustomEmailFolderInput(emailAddress.id, "CUSTOM"))

        emailClient.updateEmailMessages(
            UpdateEmailMessagesInput(listOf(messageId), UpdateEmailMessagesInput.UpdatableValues(folderId = customFolder.id)),
        )

        when (
            val listEmailMessages = waitForMessagesByFolder(
                1,
                ListEmailMessagesForEmailFolderIdInput(
                    customFolder.id,
                ),
            )
        ) {
            is ListAPIResult.Success -> {
                val messages = listEmailMessages.result.items
                messages.size shouldBe 1
                messages[0].id shouldBe messageId
            }

            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }
}
