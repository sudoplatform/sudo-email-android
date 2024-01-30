/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.shouldBe
import kotlinx.coroutines.runBlocking
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
    fun teardown() = runBlocking {
        emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        sudoList.map { sudoClient.deleteSudo(it) }
        sudoClient.reset()
    }

    @Test
    fun getConfigurationDataShouldReturnConfigurationData() = runBlocking {
        val configurationData = emailClient.getConfigurationData()
        with(configurationData) {
            deleteEmailMessagesLimit shouldBe 100
            updateEmailMessagesLimit shouldBe 100
            emailMessageMaxInboundMessageSize shouldBe 10485760
            emailMessageMaxOutboundMessageSize shouldBe 10485760
        }
    }
}
