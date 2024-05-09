/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.samples

import android.content.Context
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.SudoEmailNotifiableClient
import com.sudoplatform.sudoemail.SudoEmailNotificationHandler
import com.sudoplatform.sudoemail.types.EmailMessageReceivedNotification
import com.sudoplatform.sudonotification.SudoNotificationClient
import com.sudoplatform.sudouser.SudoUserClient
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

/**
 * These are sample snippets of code that are included in the generated documentation. They are
 * placed here in the test code so that at least we know they will compile.
 */
@RunWith(RobolectricTestRunner::class)
@Suppress("UNUSED_VARIABLE")
class Samples : BaseTests() {

    private val context by before { mock<Context>() }

    @Test
    fun mockTest() {
        // Just to keep junit happy
    }

    fun sudoEmailClient(sudoUserClient: SudoUserClient) {
        val emailClient = SudoEmailClient.builder()
            .setContext(context)
            .setSudoUserClient(sudoUserClient)
            .build()
    }

    fun sudoEmailNotifiableClient(sudoUserClient: SudoUserClient) {
        val notificationHandler = object : SudoEmailNotificationHandler {
            override fun onEmailMessageReceived(message: EmailMessageReceivedNotification) {
                // Handle messageReceived notification
            }
        }

        val notifiableClient = SudoEmailNotifiableClient.builder()
            .setContext(context)
            .setNotificationHandler(notificationHandler)
            .build()

        val sudoNotificationClient = SudoNotificationClient.builder()
            .setSudoUserClient(sudoUserClient)
            .setNotifiableClients(listOf(notifiableClient))
            .build()
    }
}
