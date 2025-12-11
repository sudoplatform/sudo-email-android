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
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.listDraftEmailMessageMetadata].
 */
@RunWith(AndroidJUnit4::class)
class ListDraftEmailMessageMetadataIntegrationTest : BaseIntegrationTest() {
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
    fun listDraftEmailMessageMetadataShouldReturnEmptyListIfNoDrafts() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val result = emailClient.listDraftEmailMessageMetadata()

            result.size shouldBe 0
        }

    @Test
    fun listDraftEmailMessageMetadataShouldReturnListOfDraftMessageMetadata() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val draftIds = mutableListOf<String>()
            for (i in 0 until 2) {
                val rfc822Data =
                    DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                        from = emailAddress.emailAddress,
                        to = listOf(emailAddress.emailAddress),
                        subject = "Draft $i",
                    )
                val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
                draftIds.add(emailClient.createDraftEmailMessage(createDraftEmailMessageInput))
            }
            draftIds.sort()
            val result = emailClient.listDraftEmailMessageMetadata()

            result.size shouldBe 2
            result.sortedBy { it.id }.forEachIndexed { index, draftEmailMessageMetadata ->
                draftEmailMessageMetadata.id shouldBe draftIds[index]
                draftEmailMessageMetadata.emailAddressId shouldBe emailAddress.id
            }
        }

    @Test
    fun listDraftEmailMessageMetadataShouldUpdateWhenNewDraftIsAdded() =
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
                        to = listOf(emailAddress.emailAddress),
                        subject = "Draft $i",
                    )
                val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
                emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
            }

            val result = emailClient.listDraftEmailMessageMetadata()

            result.size shouldBe 2

            val rfc822Data =
                DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                    from = emailAddress.emailAddress,
                    to = listOf(emailAddress.emailAddress),
                    subject = "New Draft",
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
            emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

            val updatedResult = emailClient.listDraftEmailMessageMetadata()

            updatedResult.size shouldBe 3
        }

    @Test
    fun listDraftEmailMessageMetadataShouldListMessagesForEachAddress() =
        runTest {
            val sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

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
                    subject = "Not yours",
                )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress2.id)
            emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

            val result = emailClient.listDraftEmailMessageMetadata()

            result.size shouldBe 3
        }
}
