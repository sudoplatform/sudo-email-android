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
import com.sudoplatform.sudoemail.subscribeToEmailMessages
import com.sudoplatform.sudoemail.subscription.EmailMessageSubscriber
import com.sudoplatform.sudoemail.subscription.Subscriber
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.DeleteEmailMessageSuccessResult
import com.sudoplatform.sudoemail.types.Direction
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailMessagesInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.string.shouldMatch
import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.awaitility.kotlin.until
import org.awaitility.kotlin.withPollInterval
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of:
 *  - [SudoEmailClient.subscribeToEmailMessages]
 *  - [SudoEmailClient.unsubscribeFromEmailMessages]
 *  - [SudoEmailClient.unsubscribeAllFromEmailMessages]
 *  - [SudoEmailClient.close]
 */
@RunWith(AndroidJUnit4::class)
class EmailMessageSubscriberIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

    private val emailMessageSubscriber = object : EmailMessageSubscriber {
        override fun connectionStatusChanged(state: Subscriber.ConnectionState) { }
        override fun emailMessageChanged(emailMessage: EmailMessage, type: EmailMessageSubscriber.ChangeType) { }
    }

    @Before
    fun setup() {
        sudoClient.reset()
        sudoClient.generateEncryptionKey()
    }

    @After
    fun teardown() = runTest {
        emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        sudoList.map { sudoClient.deleteSudo(it) }
        emailClient.unsubscribeAllFromEmailMessages()
        sudoClient.reset()
    }

    @Test
    fun subscribeUnsubscribeShouldNotFail() = runTest {
        if (!userClient.isRegistered()) {
            registerSignInAndEntitle()
        }

        emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)
        emailClient.unsubscribeAllFromEmailMessages()

        emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)
        emailClient.unsubscribeFromEmailMessages("id")

        emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)

        emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)
        emailClient.unsubscribeAllFromEmailMessages()

        emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)
        emailClient.unsubscribeFromEmailMessages("id")

        emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)

        emailClient.close()
    }

    @Test
    fun subscribeShouldThrowWhenNotAuthenticated() = runTest {
        if (userClient.isRegistered()) {
            deregister()
        }

        emailClient.unsubscribeFromEmailMessages("id")
        emailClient.unsubscribeAllFromEmailMessages()

        shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
            emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)
        }

        shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
            emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)
        }

        emailClient.unsubscribeFromEmailMessages("id")
        emailClient.unsubscribeAllFromEmailMessages()
        shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
            emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)
        }

        shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
            emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)
        }
    }

    @Test
    fun subscribeShouldGetUpdatesFromEmailMessageCreateUpdateAndDelete() = runTest {
        if (!userClient.isRegistered()) {
            registerSignInAndEntitle()
        }
        var sentMessageId = ""
        val id = UUID.randomUUID().toString()
        var isCreated = false
        var isUpdated = false
        var isDeleted = false
        val emailMessageSubscriber = object : EmailMessageSubscriber {
            override fun connectionStatusChanged(state: Subscriber.ConnectionState) { }
            override fun emailMessageChanged(emailMessage: EmailMessage, type: EmailMessageSubscriber.ChangeType) {
                when (type) {
                    EmailMessageSubscriber.ChangeType.CREATED -> {
                        isCreated = true
                        if (emailMessage.direction == Direction.OUTBOUND) {
                            emailMessage.id shouldMatch sentMessageId
                        } else {
                            emailMessage.id shouldStartWith "em-msg-"
                        }
                    }
                    EmailMessageSubscriber.ChangeType.UPDATED -> {
                        isUpdated = true
                        emailMessage.id shouldMatch sentMessageId
                    }
                    EmailMessageSubscriber.ChangeType.DELETED -> {
                        isDeleted = true
                        emailMessage.id shouldMatch sentMessageId
                    }
                }
            }
        }
        emailClient.subscribeToEmailMessages(id, emailMessageSubscriber)

        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val sendResult = sendEmailMessage(emailClient, emailAddress)
        sentMessageId = sendResult.id
        sentMessageId.isBlank() shouldBe false
        sendResult.createdAt shouldNotBe null

        val updateResult = emailClient.updateEmailMessages(
            UpdateEmailMessagesInput(
                listOf(sentMessageId),
                UpdateEmailMessagesInput.UpdatableValues(folderId = null, seen = false),
            ),
        )
        updateResult.status shouldBe BatchOperationStatus.SUCCESS
        updateResult.successValues?.first()?.id shouldBe sendResult.id
        updateResult.failureValues?.isEmpty() shouldBe true

        val deleteResult = emailClient.deleteEmailMessage(sentMessageId)
        deleteResult shouldBe DeleteEmailMessageSuccessResult(sentMessageId)

        delay(3000L)
        // Ensure that the callback has been called for each change type
        await.atMost(Duration.ONE_MINUTE) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS until { isCreated }
        await.atMost(Duration.ONE_MINUTE) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS until { isUpdated }
        await.atMost(Duration.ONE_MINUTE) withPollInterval Duration.TWO_HUNDRED_MILLISECONDS until { isDeleted }
    }
}
