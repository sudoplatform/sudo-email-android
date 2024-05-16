/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.subscribeToEmailMessages
import com.sudoplatform.sudoemail.subscription.EmailMessageSubscriber
import com.sudoplatform.sudoemail.subscription.Subscriber
import com.sudoplatform.sudoemail.types.EmailMessage
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of:
 *  - [SudoEmailClient.subscribeToEmailMessages]
 *  - [SudoEmailClient.unsubscribeFromEmailMessages]
 *  - [SudoEmailClient.unsubscribeAllFromEmailMessages]
 *  - [SudoEmailClient.close]
 */
@RunWith(AndroidJUnit4::class)
class EmailMessageSubscriberIntegrationTest : BaseIntegrationTest() {

    private val emailMessageSubscriber = object : EmailMessageSubscriber {
        override fun connectionStatusChanged(state: Subscriber.ConnectionState) { }
        override fun emailMessageChanged(emailMessage: EmailMessage) { }
    }

    @Before
    fun setup() {
        sudoClient.reset()
        sudoClient.generateEncryptionKey()
    }

    @After
    fun teardown() = runTest {
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

        emailClient.subscribeToEmailMessages("id") { }

        emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)
        emailClient.unsubscribeAllFromEmailMessages()

        emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)
        emailClient.unsubscribeFromEmailMessages("id")

        emailClient.subscribeToEmailMessages("id") { }

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
            emailClient.subscribeToEmailMessages("id") {}
        }

        emailClient.unsubscribeFromEmailMessages("id")
        emailClient.unsubscribeAllFromEmailMessages()
        shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
            emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)
        }

        shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
            emailClient.subscribeToEmailMessages("id") {}
        }
    }
}
