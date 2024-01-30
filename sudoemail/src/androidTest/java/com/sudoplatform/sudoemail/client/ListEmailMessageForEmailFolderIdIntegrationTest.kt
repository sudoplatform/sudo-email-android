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
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.SortOrder
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeLessThan
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
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
 * Test the operation of [SudoEmailClient.listEmailMessagesForEmailFolderId].
 */
@RunWith(AndroidJUnit4::class)
class ListEmailMessageForEmailFolderIdIntegrationTest : BaseIntegrationTest() {
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
    fun listEmailMessagesForEmailFolderIdShouldReturnEmailMessageListResult() = runBlocking {
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

        val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
            ?: fail("EmailFolder could not be found")

        val messageCount = 2
        for (i in 0 until messageCount) {
            val emailId = sendEmailMessage(emailClient, emailAddress)
            emailId.isBlank() shouldBe false
        }

        // Wait for all the messages to arrive
        val listEmailMessages =
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
                        folderId = inboxFolder.id,
                    )
                    emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == messageCount }

        when (listEmailMessages) {
            is ListAPIResult.Success -> {
                val inbound = listEmailMessages.result.items
                inbound.size shouldBe messageCount
                with(inbound[0]) {
                    from.firstOrNull()?.emailAddress shouldBe fromSimulatorAddress
                    to.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
                    hasAttachments shouldBe false
                    size shouldBeGreaterThan 0.0
                    folderId shouldBe inboxFolder.id
                }
            }
            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesForEmailFolderIdShouldRespectLimit() = runBlocking {
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
            val emailId = sendEmailMessage(emailClient, emailAddress)
            emailId.isBlank() shouldBe false
        }

        val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
            ?: fail("EmailFolder could not be found")

        // Wait for all the messages to arrive
        val listEmailMessages =
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
                        folderId = inboxFolder.id,
                        limit = 1,
                    )
                    emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == 1 }

        when (listEmailMessages) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.size shouldBe 1
                listEmailMessages.result.items[0].folderId shouldBe inboxFolder.id
            }
            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesForEmailFolderIdShouldRespectDateRange() = runBlocking {
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

        val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
            ?: fail("EmailFolder could not be found")

        val messageCount = 2
        for (i in 0 until messageCount) {
            val emailId = sendEmailMessage(emailClient, emailAddress)
            emailId.isBlank() shouldBe false
        }

        // Wait for all the messages to arrive
        val listEmailMessages =
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
                        folderId = inboxFolder.id,
                        dateRange = DateRange(
                            startDate = emailAddress.createdAt,
                            endDate = Date(emailAddress.createdAt.time + 100000),
                        ),
                    )
                    emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == messageCount }

        when (listEmailMessages) {
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
    fun listEmailMessagesForEmailFolderIdShouldReturnEmptyListForOutOfDateRange() = runBlocking {
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

        val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
            ?: fail("EmailFolder could not be found")

        val messageCount = 2
        for (i in 0 until messageCount) {
            val emailId = sendEmailMessage(emailClient, emailAddress)
            emailId.isBlank() shouldBe false
        }

        val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
            folderId = inboxFolder.id,
            dateRange = DateRange(
                startDate = sudo.createdAt,
                endDate = emailAddress.createdAt,
            ),
        )
        when (val listEmailMessages = emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)) {
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
    fun listEmailMessagesForEmailFolderIdShouldReturnEmailMessageListAscending() = runBlocking {
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

        val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
            ?: fail("EmailFolder could not be found")

        val messageCount = 2
        for (i in 0 until messageCount) {
            val emailId = sendEmailMessage(emailClient, emailAddress)
            emailId.isBlank() shouldBe false
        }

        // Wait for all the messages to arrive
        val listEmailMessages =
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
                        folderId = inboxFolder.id,
                        dateRange = DateRange(
                            startDate = emailAddress.createdAt,
                            endDate = Date(emailAddress.createdAt.time + 100000),
                        ),
                        sortOrder = SortOrder.ASC,
                    )
                    emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == messageCount }

        when (listEmailMessages) {
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
    fun listEmailMessagesForEmailFolderIdShouldReturnEmailMessageListDescending() = runBlocking {
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

        val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
            ?: fail("EmailFolder could not be found")

        val messageCount = 2
        for (i in 0 until messageCount) {
            val emailId = sendEmailMessage(emailClient, emailAddress)
            emailId.isBlank() shouldBe false
        }

        // Wait for all the messages to arrive
        val listEmailMessages =
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
                        folderId = inboxFolder.id,
                        sortOrder = SortOrder.DESC,
                    )
                    emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == messageCount }

        when (listEmailMessages) {
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
    fun listEmailMessagesForEmailFolderIdShouldReturnEmptyListWhenFolderContainsNoMessages() = runBlocking {
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

        val trashFolder = getFolderByName(emailClient, emailAddress.id, "TRASH")
            ?: fail("EmailFolder could not be found")

        val messageCount = 2
        for (i in 0 until messageCount) {
            val emailId = sendEmailMessage(emailClient, emailAddress)
            emailId.isBlank() shouldBe false
        }

        // Wait for all the messages to arrive
        val listEmailMessages =
            await.atMost(Duration.TEN_SECONDS.multiply(6)) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS untilCallTo {
                runBlocking {
                    val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
                        folderId = trashFolder.id,
                        sortOrder = SortOrder.DESC,
                    )
                    emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.isEmpty() }

        when (listEmailMessages) {
            is ListAPIResult.Success -> {
                listEmailMessages.result.items.size shouldBe 0
            }
            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }

    @Test
    fun listEmailMessagesForEmailFolderIdShouldReturnEmptyListForNonExistingEmailFolder() = runBlocking {
        val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
            folderId = "nonExistentId",
        )
        when (val listEmailMessages = emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)) {
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
    fun listEmailMessagesForEmailFolderIdShouldReturnPartialResult() = runBlocking {
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

        val inboxFolder = getFolderByName(emailClient, emailAddress.id, "INBOX")
            ?: fail("EmailFolder could not be found")

        val messageCount = 2
        for (i in 0 until messageCount) {
            val emailId = sendEmailMessage(emailClient, emailAddress)
            emailId.isBlank() shouldBe false
        }
        delay(2000)

        // Reset client to cause key not found errors
        emailClient.reset()

        val listEmailMessagesInput = ListEmailMessagesForEmailFolderIdInput(
            folderId = inboxFolder.id,
        )
        when (val listEmailMessages = emailClient.listEmailMessagesForEmailFolderId(listEmailMessagesInput)) {
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
}
