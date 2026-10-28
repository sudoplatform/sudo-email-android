/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.util.DefaultEmailMessageDataProcessor
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.ListDraftEmailMessageMetadataForEmailAddressIdInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.listDraftEmailMessageMetadataForEmailAddressId].
 */
@RunWith(AndroidJUnit4::class)
class ListDraftEmailMessageMetadataForEmailAddressIdIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()

    @After
    fun teardown() =
        runTest {
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        }

    @Test
    fun listDraftEmailMessageMetadataForEmailAddressIdShouldThrowErrorIfSenderEmailAddressNotFound() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                emailClient.listDraftEmailMessageMetadataForEmailAddressId(
                    ListDraftEmailMessageMetadataForEmailAddressIdInput("bogusEmailId"),
                )
            }
        }

    @Test
    fun listDraftEmailMessageMetadataForEmailAddressIdShouldReturnEmptyListIfNoDrafts() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val result =
                emailClient.listDraftEmailMessageMetadataForEmailAddressId(
                    ListDraftEmailMessageMetadataForEmailAddressIdInput(emailAddress.id),
                )

            result.items.size shouldBe 0
        }

    @Test
    fun listDraftEmailMessageMetadataForEmailAddressIdShouldReturnListOfDraftMessageMetadata() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val createdDraftIds = mutableListOf<String>()

            for (i in 0 until 2) {
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAddress.emailAddress,
                        to = listOf(emailAddress.emailAddress),
                        subject = "Draft $i",
                    )
                val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
                createdDraftIds.add(emailClient.createDraftEmailMessage(createDraftEmailMessageInput))
            }
            createdDraftIds.sort()
            val result =
                emailClient.listDraftEmailMessageMetadataForEmailAddressId(
                    ListDraftEmailMessageMetadataForEmailAddressIdInput(emailAddress.id),
                )

            result.items.size shouldBe 2
            result.items.sortedBy { it.id }.forEachIndexed { index, draftEmailMessageMetadata ->
                draftEmailMessageMetadata.id shouldBe createdDraftIds[index]
                draftEmailMessageMetadata.emailAddressId shouldBe emailAddress.id
            }
        }

    @Test
    fun listDraftEmailMessageMetadataForEmailAddressIdShouldReturnEmailAliasIdInMetadata() =
        runTest {
            val config = emailClient.getConfigurationData()
            Assume.assumeTrue("Test skipped due to masks not being enabled", config.emailAliasesEnabled)

            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)
            val aliasDomains = getAliasDomains(emailClient)
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"
            val emailAlias = provisionEmailAlias(aliasAddress, emailAddress.emailAddress, ownershipProof)

            val numMessages = 2

            for (i in 0 until numMessages) {
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAlias.aliasAddress,
                        to = listOf(emailAlias.aliasAddress),
                        subject = "Draft $i",
                    )
                val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id, emailAlias.id)
                emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            }

            val result =
                emailClient.listDraftEmailMessageMetadataForEmailAddressId(
                    ListDraftEmailMessageMetadataForEmailAddressIdInput(emailAddress.id),
                )

            result.items.size shouldBe numMessages
            result.items.forEach { draftEmailMessageMetadata ->
                draftEmailMessageMetadata.emailAliasId shouldBe emailAlias.id
                draftEmailMessageMetadata.emailAddressId shouldBe emailAddress.id
            }
        }

    @Test
    fun listDraftEmailMessageMetadataForEmailAddressIdShouldReturnListOfDraftMessageMetadataAndHitsDefaultLimitAndSpecificLimit() =
        runTest {
            val numMessages = 11
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val createdDraftIds = mutableListOf<String>()

            // create 11
            for (i in 0 until numMessages) {
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAddress.emailAddress,
                        to = listOf(emailAddress.emailAddress),
                        subject = "Draft $i",
                    )
                val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
                createdDraftIds.add(emailClient.createDraftEmailMessage(createDraftEmailMessageInput))
            }
            createdDraftIds.sort()

            // only return 10
            var result =
                emailClient.listDraftEmailMessageMetadataForEmailAddressId(
                    ListDraftEmailMessageMetadataForEmailAddressIdInput(emailAddress.id),
                )

            result.items.size shouldBe 10

            result =
                emailClient.listDraftEmailMessageMetadataForEmailAddressId(
                    ListDraftEmailMessageMetadataForEmailAddressIdInput(emailAddress.id, limit = 2),
                )

            result.items.size shouldBe 2
        }

    @Test
    fun listDraftEmailMessageMetadataForEmailAddressIdShouldReturnListOfDraftMessageMetadataWithinLimitShouldBePaged() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val createdDraftIds = mutableListOf<String>()

            for (i in 0 until 3) {
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAddress.emailAddress,
                        to = listOf(emailAddress.emailAddress),
                        subject = "Draft $i",
                    )
                val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
                createdDraftIds.add(emailClient.createDraftEmailMessage(createDraftEmailMessageInput))
            }
            createdDraftIds.sort()
            var result =
                emailClient.listDraftEmailMessageMetadataForEmailAddressId(
                    ListDraftEmailMessageMetadataForEmailAddressIdInput(emailAddress.id, limit = 2),
                )
            result.items.size shouldBe 2
            val firstPageIds = result.items.map { it.id }.toSet()

            result =
                emailClient.listDraftEmailMessageMetadataForEmailAddressId(
                    ListDraftEmailMessageMetadataForEmailAddressIdInput(emailAddress.id, limit = 2, nextToken = result.nextToken),
                )
            result.items.size shouldBe 1
            val secondPageIds = result.items.map { it.id }.toSet()

            // Verify no items appear in both pages
            val intersection = firstPageIds.intersect(secondPageIds)
            intersection.size shouldBe 0

            // Verify all IDs from both pages are in the created drafts list
            val allPageIds = firstPageIds + secondPageIds
            allPageIds.forEach { pageId ->
                createdDraftIds.contains(pageId) shouldBe true
            }

            // Verify we got all the created drafts
            allPageIds.size shouldBe createdDraftIds.size
        }

    @Test
    fun listDraftEmailMessageMetadataForEmailAddressIdShouldUpdateWhenNewDraftIsAdded() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            for (i in 0 until 2) {
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAddress.emailAddress,
                        to = listOf(emailAddress.emailAddress),
                        subject = "Draft $i",
                    )
                val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
                emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            }

            val result =
                emailClient.listDraftEmailMessageMetadataForEmailAddressId(
                    ListDraftEmailMessageMetadataForEmailAddressIdInput(emailAddress.id),
                )

            result.items.size shouldBe 2

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAddress.emailAddress,
                    to = listOf(emailAddress.emailAddress),
                    subject = "New Draft",
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
            emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

            val finalResult =
                emailClient.listDraftEmailMessageMetadataForEmailAddressId(
                    ListDraftEmailMessageMetadataForEmailAddressIdInput(emailAddress.id),
                )

            finalResult.items.size shouldBe 3
        }

    @Test
    fun listDraftEmailMessageMetadataForEmailAddressIdShouldNotListMessagesFromOtherAccounts() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val emailAddress2 = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress2 shouldNotBe null
            emailAddressList.add(emailAddress2)

            for (i in 0 until 2) {
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAddress.emailAddress,
                        to = listOf(emailAddress.emailAddress),
                        subject = "Draft $i",
                    )
                val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
                emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            }

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAddress2.emailAddress,
                    to = listOf(emailAddress2.emailAddress),
                    subject = "Another Draft",
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress2.id)
            emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

            val result =
                emailClient.listDraftEmailMessageMetadataForEmailAddressId(
                    ListDraftEmailMessageMetadataForEmailAddressIdInput(emailAddress.id),
                )

            result.items.size shouldBe 2
        }
}
