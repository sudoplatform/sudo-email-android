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
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesForSudoIdInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.listEmailAddressesForSudoId].
 */
@RunWith(AndroidJUnit4::class)
class ListEmailAddressesForSudoIdIntegrationTest : BaseIntegrationTest() {
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
    fun listEmailAddressesForSudoIdShouldReturnSingleEmailAddressListOutputResult() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo.id shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val aliasInput = "John Doe"
            val emailAddress = provisionEmailAddress(emailClient, ownershipProof, alias = aliasInput)
            emailAddress shouldNotBe null
            emailAddressList.add(emailAddress)

            val input = ListEmailAddressesForSudoIdInput(sudo.id!!)
            val listEmailAddresses = emailClient.listEmailAddressesForSudoId(input)
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
                    }
                    val folders = listEmailAddresses.result.items[0].folders
                    when (folders.size) {
                        4 -> {
                            folders.map { it.folderName } shouldContainExactlyInAnyOrder listOf("INBOX", "SENT", "TRASH", "OUTBOX")
                        }
                        5 -> {
                            folders.map { it.folderName } shouldContainExactlyInAnyOrder listOf("INBOX", "SENT", "TRASH", "OUTBOX", "SPAM")
                        }
                        else -> {
                            throw AssertionError("Unexpected number of folders: ${folders.size}")
                        }
                    }
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailAddressesForSudoIdShouldReturnPartialResultForMissingKey() =
        runTest {
            val sudo = sudoClient.createSudo(TestData.sudo)
            sudo.id shouldNotBe null
            sudoList.add(sudo)

            val ownershipProof = getOwnershipProof(sudo)
            ownershipProof shouldNotBe null

            val aliasInput = "John Doe"
            val emailAddress = provisionEmailAddress(emailClient, ownershipProof, alias = aliasInput)
            emailAddress shouldNotBe null

            emailClient.reset()

            val input = ListEmailAddressesForSudoIdInput(sudo.id!!)
            val listEmailAddresses = emailClient.listEmailAddressesForSudoId(input)
            listEmailAddresses shouldNotBe null

            when (listEmailAddresses) {
                is ListAPIResult.Partial -> {
                    listEmailAddresses.result.items.size shouldBe 0
                    listEmailAddresses.result.failed.size shouldBe 1
                    listEmailAddresses.result.nextToken shouldBe null

                    with(listEmailAddresses.result.failed[0]) {
                        partial.id shouldBe emailAddress.id
                        partial.owner shouldBe emailAddress.owner
                        partial.owners shouldBe emailAddress.owners
                        listEmailAddresses.result.failed[0]
                            .partial.emailAddress shouldBe emailAddress.emailAddress
                        partial.size shouldBe emailAddress.size
                        partial.numberOfEmailMessages shouldBe 0
                        partial.version shouldBe emailAddress.version
                        partial.createdAt.time shouldBe emailAddress.createdAt.time
                        partial.updatedAt.time shouldBe emailAddress.createdAt.time
                        partial.lastReceivedAt shouldBe emailAddress.lastReceivedAt
                        cause.shouldBeInstanceOf<DeviceKeyManager.DeviceKeyManagerException.DecryptionException>()
                    }
                    val folders =
                        listEmailAddresses.result.failed[0]
                            .partial.folders
                    when (folders.size) {
                        4 -> {
                            folders.map { it.folderName } shouldContainExactlyInAnyOrder listOf("INBOX", "SENT", "TRASH", "OUTBOX")
                        }
                        5 -> {
                            folders.map { it.folderName } shouldContainExactlyInAnyOrder listOf("INBOX", "SENT", "TRASH", "OUTBOX", "SPAM")
                        }
                        else -> {
                            throw AssertionError("Unexpected number of folders: ${folders.size}")
                        }
                    }
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailAddressesForSudoIdShouldReturnEmptyListResultForNonExistingSudo() =
        runTest {
            val input = ListEmailAddressesForSudoIdInput("nonExistentId")
            val listEmailAddresses = emailClient.listEmailAddressesForSudoId(input)
            listEmailAddresses shouldNotBe null

            when (listEmailAddresses) {
                is ListAPIResult.Success -> {
                    listEmailAddresses.result.items.isEmpty() shouldBe true
                    listEmailAddresses.result.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }
}
