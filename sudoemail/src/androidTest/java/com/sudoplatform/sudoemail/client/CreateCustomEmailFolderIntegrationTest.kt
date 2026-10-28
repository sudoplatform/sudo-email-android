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
import com.sudoplatform.sudoemail.types.inputs.CreateCustomEmailFolderInput
import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.createCustomEmailFolder].
 */
@RunWith(AndroidJUnit4::class)
class CreateCustomEmailFolderIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()

    @After
    fun teardown() =
        runTest {
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        }

    @Test
    fun createCustomEmailFolderShouldFailWithInvalidEmailAddressId() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = CreateCustomEmailFolderInput("bad-id", "TEST")

            shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
                emailClient.createCustomEmailFolder(input)
            }
        }

    @Test
    fun createCustomEmailFolderShouldReturnNewFolderOnSuccess() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = CreateCustomEmailFolderInput(emailAddress.id, "TEST")

            val result = emailClient.createCustomEmailFolder(input)
            result.id shouldStartWith emailAddress.id
            result.customFolderName shouldBe "TEST"
        }

    @Test
    fun createCustomEmailFolderShouldWorkMultipleTimes() =
        runTest {
            val ownershipProof = sharedOwnershipProof()

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)

            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input1 = CreateCustomEmailFolderInput(emailAddress.id, "TEST1")

            val result1 = emailClient.createCustomEmailFolder(input1)
            result1.id shouldStartWith emailAddress.id
            result1.customFolderName shouldBe "TEST1"

            val input2 = CreateCustomEmailFolderInput(emailAddress.id, "TEST2")

            val result2 = emailClient.createCustomEmailFolder(input2)
            result2.id shouldStartWith emailAddress.id
            result2.customFolderName shouldBe "TEST2"
        }
}
