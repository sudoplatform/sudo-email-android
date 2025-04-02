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
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailFolder
import com.sudoplatform.sudoemail.types.inputs.CreateCustomEmailFolderInput
import com.sudoplatform.sudoemail.types.inputs.UpdateCustomEmailFolderInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.updateCustomEmailFolder].
 */
@RunWith(AndroidJUnit4::class)
class UpdateCustomEmailFolderIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()
    val customFolderName = "TEST"
    val updatedCustomFolderName = "UPDATED_TEST"

    @Before
    fun setup() {
        sudoClient.reset()
        sudoClient.generateEncryptionKey()
    }

    @After
    fun teardown() = runTest {
        emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        sudoList.map { sudoClient.deleteSudo(it) }
        sudoClient.reset()
    }

    private suspend fun createCustomEmailFolder(emailAddressId: String, emailFolderName: String): EmailFolder {
        val input = CreateCustomEmailFolderInput(emailAddressId, emailFolderName)

        val result = emailClient.createCustomEmailFolder(input)
        return result
    }

    @Test
    fun updateCustomEmailFolderShouldThrowErrorForUnknownEmailAddress() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val customFolder = createCustomEmailFolder(emailAddress.id, customFolderName)

        val input = UpdateCustomEmailFolderInput("bad-id", customFolder.id, updatedCustomFolderName)

        shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
            emailClient.updateCustomEmailFolder(input)
        }
    }

    @Test
    fun updateCustomEmailFolderShouldThrowErrorForUnknownEmailFolderAddress() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val customFolder = createCustomEmailFolder(emailAddress.id, customFolderName)

        val input = UpdateCustomEmailFolderInput(emailAddress.id, "bad-id", updatedCustomFolderName)

        shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
            emailClient.updateCustomEmailFolder(input)
        }
    }

    @Test
    fun updateCustomEmailFolderShouldReturnUpdatedFolderOnSuccess() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val customFolder = createCustomEmailFolder(emailAddress.id, customFolderName)

        val input = UpdateCustomEmailFolderInput(emailAddress.id, customFolder.id, updatedCustomFolderName)
        val result = emailClient.updateCustomEmailFolder(input)

        result.id shouldBe customFolder.id
        result.customFolderName shouldBe updatedCustomFolderName
    }
}
