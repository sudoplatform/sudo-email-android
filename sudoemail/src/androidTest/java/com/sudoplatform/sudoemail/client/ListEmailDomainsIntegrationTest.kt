/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.types.EmailDomain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.listEmailDomains].
 */
@RunWith(AndroidJUnit4::class)
class ListEmailDomainsIntegrationTest : BaseIntegrationTest() {
    @Before
    fun setup() {
        runTest {
            sudoClient.reset()
        }
    }

    @After
    fun teardown() =
        runTest {
            sudoClient.reset()
        }

    @Test
    fun listEmailDomainsReturnsExpectedOutput() =
        runTest {
            val config = emailClient.getConfigurationData()
            val generalDomains = getEmailDomains(emailClient)
            var maskDomains: List<String> = emptyList()
            if (config.emailMasksEnabled) {
                maskDomains = getMaskDomains(emailClient)
            }

            val domainsList = emailClient.listEmailDomains()
            domainsList shouldNotBe null

            val matchedDomains = mutableListOf<EmailDomain>()

            generalDomains.forEach { generalDomain ->
                val match = domainsList.find { it.domain == generalDomain }
                match shouldNotBe null
                match?.isMaskDomain shouldBe false
                matchedDomains.add(match!!)
            }
            maskDomains.forEach { maskDomain ->
                val match = domainsList.find { it.domain == maskDomain }
                match shouldNotBe null
                match?.isMaskDomain shouldBe true
                matchedDomains.add(match!!)
            }

            matchedDomains.size shouldBe generalDomains.size + maskDomains.size
        }
}
