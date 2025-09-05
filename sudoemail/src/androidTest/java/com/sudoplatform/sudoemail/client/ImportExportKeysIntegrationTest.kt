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
import com.sudoplatform.sudoemail.types.inputs.GetEmailAddressInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.exportKeys, SudoEmailClient.importKeys()].
 */
@RunWith(AndroidJUnit4::class)
class ImportExportKeysIntegrationTest : BaseIntegrationTest() {
    private val sudoList = mutableListOf<Sudo>()

    @Before
    fun setup() {
        sudoClient.reset()
        sudoClient.generateEncryptionKey()
    }

    @After
    fun teardown() =
        runBlocking {
            sudoList.map { sudoClient.deleteSudo(it) }
            sudoClient.reset()
        }

    @Test
    fun exportImportKeysShouldSucceed() =
        runBlocking {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null

            val result = sendEmailMessage(emailClient, emailAddress)
            result.id.isBlank() shouldBe false

            delay(3000)

            val getMessageInput = GetEmailMessageInput(result.id)
            val storedEmailMessage =
                emailClient.getEmailMessage(getMessageInput)
                    ?: throw AssertionError("should not be null")

            with(storedEmailMessage) {
                from.firstOrNull()?.emailAddress shouldBe emailAddress.emailAddress
                to.firstOrNull()?.emailAddress shouldBe successSimulatorAddress
                hasAttachments shouldBe false
                size shouldBeGreaterThan 0.0
            }

            val getAddressInput = GetEmailAddressInput(emailAddress.id)
            val storedEmailAddress =
                emailClient.getEmailAddress(getAddressInput)
                    ?: throw AssertionError("should not be null")
            storedEmailAddress.emailAddress shouldBe emailAddress.emailAddress

            val exportedKeys = emailClient.exportKeys()
            exportedKeys shouldNotBe null

            // remove all crypto keys from KeyManager
            emailClient.reset()

            try {
                emailClient.getEmailMessage(getMessageInput)
                throw AssertionError("expected getEmailMessage to throw with no keys, but it succeeded")
            } catch (e: Throwable) {
                e.shouldBeInstanceOf<SudoEmailClient.EmailCryptographicKeysException.SecureKeyArchiveException>()
            }

            // restore keys
            emailClient.importKeys(exportedKeys)

            val restoredKeysMessage = emailClient.getEmailMessage(getMessageInput)
            restoredKeysMessage shouldBe storedEmailMessage

            val restoredKeysAddress = emailClient.getEmailAddress(getAddressInput)
            restoredKeysAddress shouldBe storedEmailAddress
        }
}
