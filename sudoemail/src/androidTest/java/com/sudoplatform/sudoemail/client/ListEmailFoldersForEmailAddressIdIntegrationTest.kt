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
import com.sudoplatform.sudoemail.types.inputs.CreateCustomEmailFolderInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailFoldersForEmailAddressIdInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldContainAll
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.listEmailFoldersForEmailAddressId].
 */
@RunWith(AndroidJUnit4::class)
class ListEmailFoldersForEmailAddressIdIntegrationTest : BaseIntegrationTest() {
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
    fun listEmailFoldersForEmailAddressIdShouldReturnAllEmailFolders() =
        runTest {
            val expectedFolderNames = listOf("INBOX", "OUTBOX", "SENT", "TRASH")
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val aliasInput = "John Doe"
            val emailAddress = provisionEmailAddress(emailClient, ownershipProof, alias = aliasInput)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailFoldersForEmailAddressIdInput(emailAddress.id)
            val listEmailFolders = emailClient.listEmailFoldersForEmailAddressId(input)
            listEmailFolders.items.isEmpty() shouldBe false
            listEmailFolders.items.size shouldBeGreaterThanOrEqual expectedFolderNames.size
            listEmailFolders.nextToken shouldBe null

            val emailFolders = listEmailFolders.items
            emailFolders.map { it.folderName } shouldContainAll expectedFolderNames
            emailFolders.map { it.emailAddressId shouldBe emailAddress.id }
            emailFolders.map { it.owner shouldBe emailAddress.owner }
            emailFolders.map { it.owners shouldBe emailAddress.owners }
        }

    @Test
    fun listEmailFoldersForEmailAddressIdShouldRespectLimit() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val aliasInput = "John Doe"
            val emailAddress = provisionEmailAddress(emailClient, ownershipProof, alias = aliasInput)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input =
                ListEmailFoldersForEmailAddressIdInput(
                    emailAddress.id,
                    limit = 1,
                )
            val listEmailFolders = emailClient.listEmailFoldersForEmailAddressId(input)
            listEmailFolders.items.size shouldBe 1
            listEmailFolders.nextToken shouldNotBe null

            val emailFolders = listEmailFolders.items
            with(emailFolders[0]) {
                id shouldNotBe null
                owner shouldBe emailAddress.owner
                owners shouldBe emailAddress.owners
                emailAddressId shouldBe emailAddress.id
                folderName shouldBe "INBOX"
                size shouldBe 0.0
                unseenCount shouldBe 0.0
                version shouldBe 1
                createdAt.time shouldBeGreaterThan 0L
                updatedAt.time shouldBeGreaterThan 0L
            }
        }

    @Test
    fun listEmailFoldersShouldBeAbleToReturnCustomFolders() =
        runTest {
            val customFolderName = "TEST"
            val standardFolderNames = listOf("INBOX", "SENT", "TRASH", "OUTBOX")
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val result = emailClient.createCustomEmailFolder(CreateCustomEmailFolderInput(emailAddress.id, customFolderName))
            result.id shouldStartWith emailAddress.id
            result.customFolderName shouldBe customFolderName

            val input = ListEmailFoldersForEmailAddressIdInput(emailAddress.id)
            val listEmailFolders = emailClient.listEmailFoldersForEmailAddressId(input)
            listEmailFolders.items.isEmpty() shouldBe false
            listEmailFolders.items.size shouldBeGreaterThanOrEqual standardFolderNames.size + 1
            listEmailFolders.nextToken shouldBe null

            val emailFolders = listEmailFolders.items
            emailFolders.map { it.folderName } shouldContainAll standardFolderNames
            emailFolders.map { it.emailAddressId shouldBe emailAddress.id }
            emailFolders.map { it.owner shouldBe emailAddress.owner }
            emailFolders.map { it.owners shouldBe emailAddress.owners }
            val customFolder = emailFolders.find { it.customFolderName == customFolderName }
            customFolder shouldNotBe null
            customFolder?.id shouldStartWith emailAddress.id
            customFolder?.id shouldContain "CUSTOM"
        }
}
