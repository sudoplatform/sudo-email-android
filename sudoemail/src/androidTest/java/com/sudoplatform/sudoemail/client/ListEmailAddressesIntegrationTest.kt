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
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.listEmailAddresses].
 */
@RunWith(AndroidJUnit4::class)
class ListEmailAddressesIntegrationTest : BaseIntegrationTest() {
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
    fun listEmailAddressesShouldReturnSingleEmailAddressListOutputResult() = runBlocking {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val aliasInput = "John Doe"
        val emailAddress = provisionEmailAddress(emailClient, ownershipProof, alias = aliasInput)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val input = ListEmailAddressesInput(CachePolicy.REMOTE_ONLY)
        val listEmailAddresses = emailClient.listEmailAddresses(input)
        listEmailAddresses shouldNotBe null

        when (listEmailAddresses) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.size shouldBe 1
                listEmailAddresses.result.nextToken shouldBe null

                with(listEmailAddresses.result.items[0]) {
                    id shouldBe emailAddress.id
                    owner shouldBe emailAddress.owner
                    owners shouldBe emailAddress.owners
                    listEmailAddresses.result.items[0].emailAddress shouldBe emailAddress.emailAddress
                    size shouldBe emailAddress.size
                    numberOfEmailMessages shouldBe 0
                    version shouldBe emailAddress.version
                    createdAt.time shouldBe emailAddress.createdAt.time
                    updatedAt.time shouldBe emailAddress.createdAt.time
                    lastReceivedAt shouldBe emailAddress.lastReceivedAt
                    alias shouldBe aliasInput
                    folders.size shouldBe 4
                    folders.map { it.folderName } shouldContainExactlyInAnyOrder listOf("INBOX", "SENT", "TRASH", "OUTBOX")
                }
            }
            else -> {
                fail("Unexpected ListAPIResult")
            }
        }
    }
}
