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
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.DeleteDraftEmailMessagesInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of [SudoEmailClient.deleteDraftEmailMessages].
 */
@RunWith(AndroidJUnit4::class)
class DeleteDraftEmailMessagesIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()
    private val draftRateLimit = 10

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
    fun deleteDraftEmailMessagesShouldThrowErrorIfSenderEmailAddressNotFound() = runBlocking<Unit> {
        val mockDraftId = UUID.randomUUID()
        val mockEmailAddressId = "non-existent-email-address-id"

        val input = DeleteDraftEmailMessagesInput(
            listOf(mockDraftId.toString()),
            mockEmailAddressId,
        )
        shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
            emailClient.deleteDraftEmailMessages(input)
        }
    }

    @Test
    fun deleteDraftEmailMessagesShouldThrowErrorIfInputSizeExceedsRateLimit() = runBlocking<Unit> {
        val mockDraftIds = (0..(draftRateLimit + 1)).map { UUID.randomUUID().toString() }
        val mockEmailAddressId = "non-existent-email-address-id"

        val input = DeleteDraftEmailMessagesInput(
            mockDraftIds,
            mockEmailAddressId,
        )
        shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
            emailClient.deleteDraftEmailMessages(input)
        }
    }

    @Test
    fun deleteDraftEmailMessagesShouldReturnSuccessResult() = runBlocking {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val rfc822Data = Rfc822MessageDataProcessor().encodeToInternetMessageData(
            from = emailAddress.emailAddress,
            to = listOf(emailAddress.emailAddress),
            subject = "Test Draft",
        )

        val createDraftInput = CreateDraftEmailMessageInput(
            rfc822Data = rfc822Data,
            senderEmailAddressId = emailAddress.id,
        )

        val draftId = emailClient.createDraftEmailMessage(createDraftInput)

        val deleteDraftEmailMessagesInput = DeleteDraftEmailMessagesInput(
            listOf(draftId),
            emailAddress.id,
        )
        when (val result = emailClient.deleteDraftEmailMessages(deleteDraftEmailMessagesInput)) {
            is BatchOperationResult.SuccessOrFailureResult -> {
                result.status shouldBe BatchOperationStatus.SUCCESS
            }
            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }

        val listDraftEmailMessagesResult = emailClient.listDraftEmailMessageMetadata(emailAddress.id)
        listDraftEmailMessagesResult.find { it.id == draftId } shouldBe null
    }
}
