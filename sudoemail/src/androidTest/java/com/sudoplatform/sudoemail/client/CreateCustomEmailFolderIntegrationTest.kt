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
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.inputs.CreateCustomEmailFolderInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.createCustomEmailFolder].
 */
@RunWith(AndroidJUnit4::class)
class CreateCustomEmailFolderIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

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

    @Test
    fun createCustomEmailFolderShouldFailWithInvalidEmailAddressId() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = CreateCustomEmailFolderInput("bad-id", "TEST")

        shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
            emailClient.createCustomEmailFolder(input)
        }
    }

    @Test
    fun createCustomEmailFolderShouldReturnNewFolderOnSuccess() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = CreateCustomEmailFolderInput(emailAddress.id, "TEST")

        val result = emailClient.createCustomEmailFolder(input)
        result.id shouldStartWith emailAddress.id
        result.customFolderName shouldBe "TEST"
    }
}
