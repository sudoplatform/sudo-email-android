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
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.matchers.string.shouldNotBeEqualIgnoringCase
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.provisionEmailAddress].
 */
@RunWith(AndroidJUnit4::class)
class ProvisionEmailAddressIntegrationTest : BaseIntegrationTest() {
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
    fun provisionEmailAddressShouldReturnEmailAddress() = runTest {
        val emailDomains = getEmailDomains(emailClient)
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val sudo = createSudo(TestData.sudo)
        sudo.id shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val result = provisionEmailAddress(emailClient, ownershipProof)
        with(result) {
            id shouldNotBe null
            owner shouldBe userClient.getSubject()
            owners.first().id shouldBe sudo.id
            owners.first().issuer shouldBe "sudoplatform.sudoservice"
            result.emailAddress shouldBe emailAddress
            size shouldBe 0.0
            numberOfEmailMessages shouldBe 0
            version shouldBe 1
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
            lastReceivedAt shouldBe null
            alias shouldBe null
            folders.size shouldBe 4
            folders.map { it.folderName } shouldContainExactlyInAnyOrder listOf("INBOX", "SENT", "TRASH", "OUTBOX")
        }
        emailAddressList.add(result)
    }

    @Test
    fun provisionEmailAddressShouldAllowMultipleEmailsPerSudo() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo.id shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val result1 = provisionEmailAddress(emailClient, ownershipProof)
        with(result1) {
            id shouldNotBe null
            owner shouldBe userClient.getSubject()
            owners.first().id shouldBe sudo.id
            owners.first().issuer shouldBe "sudoplatform.sudoservice"
            result1.emailAddress shouldBe emailAddress
            size shouldBe 0.0
            numberOfEmailMessages shouldBe 0
            version shouldBe 1
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
            lastReceivedAt shouldBe null
            alias shouldBe null
            folders.size shouldBe 4
            folders.map { it.folderName } shouldContainExactlyInAnyOrder listOf("INBOX", "SENT", "TRASH", "OUTBOX")
        }
        emailAddressList.add(result1)

        val result2 = provisionEmailAddress(emailClient, ownershipProof)
        with(result2) {
            id shouldNotBe null
            owner shouldBe userClient.getSubject()
            owners.first().id shouldBe sudo.id
            owners.first().issuer shouldBe "sudoplatform.sudoservice"
            result2.emailAddress shouldBe emailAddress
            size shouldBe 0.0
            numberOfEmailMessages shouldBe 0
            version shouldBe 1
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
            lastReceivedAt shouldBe null
            alias shouldBe null
            folders.size shouldBe 4
            folders.map { it.folderName } shouldContainExactlyInAnyOrder listOf("INBOX", "SENT", "TRASH", "OUTBOX")
        }
        emailAddressList.add(result2)

        result1.emailAddress shouldNotBeEqualIgnoringCase result2.emailAddress
    }

    @Test
    fun provisionEmailAddressWithAliasShouldReturnEmailAddress() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo.id shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val aliasInput = "John Doe"
        val result = provisionEmailAddress(emailClient, ownershipProof, alias = aliasInput)
        with(result) {
            id shouldNotBe null
            owner shouldBe userClient.getSubject()
            owners.first().id shouldBe sudo.id
            owners.first().issuer shouldBe "sudoplatform.sudoservice"
            result.emailAddress shouldBe emailAddress
            size shouldBe 0.0
            numberOfEmailMessages shouldBe 0
            version shouldBe 1
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
            lastReceivedAt shouldBe null
            alias shouldBe aliasInput
            folders.size shouldBe 4
            folders.map { it.folderName } shouldContainExactlyInAnyOrder listOf("INBOX", "SENT", "TRASH", "OUTBOX")
        }
        emailAddressList.add(result)
    }

    @Test
    fun provisionEmailAddressShouldThrowWithUnsupportedDomain() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailDomains = getEmailDomains(emailClient)
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val localPart = generateSafeLocalPart()
        val emailAddress = "$localPart@gmail.com"
        shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
            provisionEmailAddress(emailClient, ownershipProof, address = emailAddress)
        }
    }

    @Test
    fun provisionEmailAddressShouldFailWithInvalidLocalPart() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailDomains = getEmailDomains(emailClient)
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val emailAddress = "@" + emailDomains.first()
        shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
            provisionEmailAddress(emailClient, ownershipProof, emailAddress)
        }
    }

    @Test
    fun provisionEmailAddressShouldThrowWithExistingAddressExceptionAfterDeprovisioning() = runTest {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val result = provisionEmailAddress(emailClient, ownershipProof)
        result shouldNotBe null

        val deprovisionedEmailAddress = emailClient.deprovisionEmailAddress(result.id)
        deprovisionedEmailAddress shouldNotBe null

        // Attempt to provision with an already deprovisioned email address
        shouldThrow<SudoEmailClient.EmailAddressException.UnavailableEmailAddressException> {
            provisionEmailAddress(emailClient, ownershipProof, result.emailAddress)
        }
    }
}
