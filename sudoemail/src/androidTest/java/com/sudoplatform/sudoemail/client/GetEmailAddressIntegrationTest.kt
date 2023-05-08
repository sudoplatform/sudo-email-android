/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.TestData
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.inputs.GetEmailAddressInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.getEmailAddress].
 */
@RunWith(AndroidJUnit4::class)
class GetEmailAddressIntegrationTest : BaseIntegrationTest() {
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
    fun getEmailAddressShouldReturnEmailAddressResult() = runBlocking {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val aliasInput = "John Doe"
        val emailAddress = provisionEmailAddress(emailClient, ownershipProof, alias = aliasInput)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val getAddressInput = GetEmailAddressInput(emailAddress.id)
        val retrievedEmailAddress = emailClient.getEmailAddress(getAddressInput)
            ?: throw AssertionError("should not be null")

        with(retrievedEmailAddress) {
            id shouldBe emailAddress.id
            owner shouldBe emailAddress.owner
            owners shouldBe emailAddress.owners
            retrievedEmailAddress.emailAddress shouldBe emailAddress.emailAddress
            size shouldBe emailAddress.size
            version shouldBe emailAddress.version
            createdAt.time shouldBe emailAddress.createdAt.time
            updatedAt.time shouldBe emailAddress.createdAt.time
            lastReceivedAt shouldBe emailAddress.lastReceivedAt
            alias shouldBe aliasInput
            folders.size shouldBe 4
            folders.map { it.folderName } shouldContainExactlyInAnyOrder listOf("INBOX", "SENT", "TRASH", "OUTBOX")
        }
    }

    @Test
    fun getEmailAddressShouldReturnNullForNonExistentAddress() = runBlocking {
        val emailDomains = getEmailDomains(emailClient)
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val localPart = generateSafeLocalPart()
        val emailAddressInput = localPart + "@" + emailDomains.first()
        val getAddressInput = GetEmailAddressInput(emailAddressInput)
        val retrievedEmailAddress = emailClient.getEmailAddress(getAddressInput)
        retrievedEmailAddress shouldBe null
    }
}
