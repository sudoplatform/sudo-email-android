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
import com.sudoplatform.sudoemail.internal.util.DefaultEmailMessageDataProcessor
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.ListDraftEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.listDraftEmailMessageMetadataForEmailAddressId].
 */
@RunWith(AndroidJUnit4::class)
class ListDraftEmailMessagesForEmailAddressIdIntegrationTest : BaseIntegrationTest() {
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
    fun listDraftEmailMessagesForEmailAddressIdShouldThrowErrorIfSenderEmailAddressNotFound() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                emailClient.listDraftEmailMessagesForEmailAddressId(ListDraftEmailMessagesForEmailAddressIdInput("bogusEmailId"))
            }
        }

    @Test
    fun listDraftEmailMessagesForEmailAddressIdShouldReturnEmptyListWhenDraftMessagesNotFound() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val result = emailClient.listDraftEmailMessagesForEmailAddressId(ListDraftEmailMessagesForEmailAddressIdInput(emailAddress.id))

            result.items.size shouldBe 0
        }

    @Test
    fun listDraftEmailMessagesForEmailAddressIdShouldReturnListOfDraftOutNetworkMessages() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            for (i in 0 until 2) {
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAddress.emailAddress,
                        to = listOf(successSimulatorAddress),
                        subject = "Draft $i",
                    )
                val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
                emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            }

            val result = emailClient.listDraftEmailMessagesForEmailAddressId(ListDraftEmailMessagesForEmailAddressIdInput(emailAddress.id))

            result.items.size shouldBe 2

            result.items.forEach { item ->
                item.emailAddressId shouldBe emailAddress.id

                val parsedMessage = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(item.rfc822Data)
                parsedMessage.to shouldContain successSimulatorAddress
                parsedMessage.from shouldContain emailAddress.emailAddress
                parsedMessage.subject shouldContain "Draft"
            }
        }

    @Test
    fun listDraftEmailMessagesForEmailAddressIdShouldReturnListOfDraftInNetworkMessages() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)
            val recipientAddress = provisionEmailAddress(emailClient, ownershipProof)
            recipientAddress shouldNotBe null
            emailAddressList.add(recipientAddress)

            for (i in 0 until 2) {
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAddress.emailAddress,
                        to = listOf(recipientAddress.emailAddress),
                        subject = "Draft $i",
                        body = "Draft body $i",
                    )
                val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
                emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            }

            val result = emailClient.listDraftEmailMessagesForEmailAddressId(ListDraftEmailMessagesForEmailAddressIdInput(emailAddress.id))

            result.items.size shouldBe 2

            result.items.forEach { item ->
                item.emailAddressId shouldBe emailAddress.id

                val parsedMessage = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(item.rfc822Data)
                parsedMessage.to shouldContain recipientAddress.emailAddress
                parsedMessage.from shouldContain emailAddress.emailAddress
                parsedMessage.subject shouldContain "Draft"
                parsedMessage.body shouldContain "Draft body"
            }
        }

    @Test
    fun listDraftEmailMessagesForEmailAddressShouldReturnListOfDraftInAndOutNetworkMessages() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)
            val recipientAddress = provisionEmailAddress(emailClient, ownershipProof)
            recipientAddress shouldNotBe null
            emailAddressList.add(recipientAddress)

            var outNetworkRfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAddress.emailAddress,
                    to = listOf(recipientAddress.emailAddress),
                    subject = "In-network Draft",
                )
            var createDraftEmailMessageInput = CreateDraftEmailMessageInput(outNetworkRfc822Data, emailAddress.id)
            emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

            val inNetworkRfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAddress.emailAddress,
                    to = listOf(successSimulatorAddress),
                    subject = "Out-network Draft",
                )
            createDraftEmailMessageInput = CreateDraftEmailMessageInput(inNetworkRfc822Data, emailAddress.id)
            emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

            val result = emailClient.listDraftEmailMessagesForEmailAddressId(ListDraftEmailMessagesForEmailAddressIdInput(emailAddress.id))

            result.items.size shouldBe 2

            result.items.forEach { item ->
                item.emailAddressId shouldBe emailAddress.id

                val parsedMessage = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(item.rfc822Data)
                parsedMessage.subject shouldNotBe null
                if (parsedMessage.subject!!.startsWith("Out-network")) {
                    parsedMessage.to shouldContain successSimulatorAddress
                } else {
                    parsedMessage.to shouldContain recipientAddress.emailAddress
                }
                parsedMessage.from shouldContain emailAddress.emailAddress
                parsedMessage.subject shouldContain "Draft"
            }
        }

    @Test
    fun listDraftEmailMessagesForEmailAddressIdShouldRespectLimit() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            // Create 5 draft messages
            for (i in 0 until 5) {
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAddress.emailAddress,
                        to = listOf(successSimulatorAddress),
                        subject = "Draft $i",
                    )
                val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
                emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            }

            // Request only 2 items
            val result =
                emailClient.listDraftEmailMessagesForEmailAddressId(
                    ListDraftEmailMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                        limit = 2,
                    ),
                )

            result.items.size shouldBe 2
            result.nextToken shouldNotBe null
        }

    @Test
    fun listDraftEmailMessagesForEmailAddressIdShouldReturnNextToken() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            // Create 10 draft messages
            for (i in 0 until 10) {
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAddress.emailAddress,
                        to = listOf(successSimulatorAddress),
                        subject = "Draft $i",
                    )
                val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
                emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            }

            // First page
            val firstResult =
                emailClient.listDraftEmailMessagesForEmailAddressId(
                    ListDraftEmailMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                        limit = 3,
                    ),
                )

            firstResult.items.size shouldBe 3
            firstResult.nextToken shouldNotBe null

            // Second page using nextToken
            val secondResult =
                emailClient.listDraftEmailMessagesForEmailAddressId(
                    ListDraftEmailMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                        limit = 3,
                        nextToken = firstResult.nextToken,
                    ),
                )

            secondResult.items.size shouldBe 3
            secondResult.nextToken shouldNotBe null

            // Verify items are different between pages
            val firstIds = firstResult.items.map { it.id }.toSet()
            val secondIds = secondResult.items.map { it.id }.toSet()
            firstIds.intersect(secondIds).size shouldBe 0
        }

    @Test
    fun listDraftEmailMessagesForEmailAddressIdShouldReturnAllItemsAcrossPages() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            // Create 7 draft messages
            val totalDrafts = 7
            for (i in 0 until totalDrafts) {
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAddress.emailAddress,
                        to = listOf(successSimulatorAddress),
                        subject = "Draft $i",
                    )
                val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
                emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            }

            // Collect all items using pagination
            val allItems = mutableListOf<String>()
            var nextToken: String? = null

            do {
                val result =
                    emailClient.listDraftEmailMessagesForEmailAddressId(
                        ListDraftEmailMessagesForEmailAddressIdInput(
                            emailAddressId = emailAddress.id,
                            limit = 3,
                            nextToken = nextToken,
                        ),
                    )
                allItems.addAll(result.items.map { it.id })
                nextToken = result.nextToken
            } while (nextToken != null)

            allItems.size shouldBe totalDrafts
            allItems.toSet().size shouldBe totalDrafts // All unique
        }

    @Test
    fun listDraftEmailMessagesForEmailAddressIdShouldReturnNullNextTokenOnLastPage() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            // Create 3 draft messages
            for (i in 0 until 3) {
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAddress.emailAddress,
                        to = listOf(successSimulatorAddress),
                        subject = "Draft $i",
                    )
                val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
                emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            }

            // Request with limit larger than total items
            val result =
                emailClient.listDraftEmailMessagesForEmailAddressId(
                    ListDraftEmailMessagesForEmailAddressIdInput(
                        emailAddressId = emailAddress.id,
                        limit = 10,
                    ),
                )

            result.items.size shouldBe 3
            result.nextToken shouldBe null
        }
}
