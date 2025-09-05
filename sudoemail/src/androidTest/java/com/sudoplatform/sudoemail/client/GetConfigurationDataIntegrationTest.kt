/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.getConfigurationData].
 */
@RunWith(AndroidJUnit4::class)
class GetConfigurationDataIntegrationTest : BaseIntegrationTest() {
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
    fun getConfigurationDataShouldReturnConfigurationData() =
        runTest {
            val configurationData = emailClient.getConfigurationData()
            with(configurationData) {
                deleteEmailMessagesLimit shouldBeGreaterThanOrEqual 1
                updateEmailMessagesLimit shouldBeGreaterThanOrEqual 1
                emailMessageMaxInboundMessageSize shouldBeGreaterThanOrEqual 1
                emailMessageMaxOutboundMessageSize shouldBeGreaterThanOrEqual 1
                emailMessageRecipientsLimit shouldBeGreaterThanOrEqual 1
                encryptedEmailMessageRecipientsLimit shouldBeGreaterThanOrEqual 1
            }
        }
}
