/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.BaseIntegrationTest.Companion.fini
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.TestData
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailAlias
import com.sudoplatform.sudoemail.types.EmailAliasListItem
import com.sudoplatform.sudoemail.types.EmailAliasStatus
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.DeprovisionEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.DisableEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.EmailAliasFilterInput
import com.sudoplatform.sudoemail.types.inputs.EnableEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.EqualStatusFilter
import com.sudoplatform.sudoemail.types.inputs.ListEmailAliasesInput
import com.sudoplatform.sudoemail.types.inputs.NotOneOfStatusFilter
import com.sudoplatform.sudoemail.types.inputs.OneOfStatusFilter
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.listEmailAliases].
 *
 * Provisioning aliases is slow, so all aliases needed by the tests are provisioned once
 * (on the first test to run) and shared by every test in the class. Tests that mutate an
 * alias (e.g. disabling it) must restore its original state before finishing.
 */
@RunWith(AndroidJUnit4::class)
class ListEmailAliasesIntegrationTest : BaseIntegrationTest() {
    companion object {
        private const val NUM_ALIASES = 4
        private const val NUM_SUDOS = 2

        // Shared fixtures, populated once by the first test's @Before.
        private var setupComplete = false
        private val sudos = mutableListOf<Sudo>()
        private val provisionedAliases = mutableListOf<EmailAlias>()
        private var realAddress: EmailAddress? = null

        // Captures the (instance-scoped) clients so @AfterClass can clean up.
        private var cleanup: (suspend () -> Unit)? = null

        /**
         * Runs before the base class's [fini], so the user is still registered and signed in.
         */
        @AfterClass
        @JvmStatic
        fun cleanupSharedAliases() =
            runTest {
                runCatching { cleanup?.invoke() }
                cleanup = null
                sudos.clear()
                provisionedAliases.clear()
                realAddress = null
                setupComplete = false
            }
    }

    @Before
    fun setup() =
        runTest {
            // Skip all tests if email aliases are not enabled
            val enabled = emailClient.getConfigurationData().emailAliasesEnabled
            Assume.assumeTrue("Test suite skipped due to aliases not being enabled.", enabled)

            if (setupComplete) return@runTest

            val aliasDomains = getAliasDomains(emailClient)
            aliasDomains.size shouldBeGreaterThanOrEqual 1

            // Sudo 0 is the shared sudo (deleted by the base class), sudo 1 is created here.
            val secondSudo = sudoClient.createSudo(TestData.sudo)
            secondSudo.id shouldNotBe null
            sudos.add(sharedSudo())
            sudos.add(secondSudo)

            val address = provisionEmailAddress(emailClient, sharedOwnershipProof())
            realAddress = address

            cleanup = {
                provisionedAliases.forEach {
                    runCatching { emailClient.deprovisionEmailAlias(DeprovisionEmailAliasInput(it.id)) }
                }
                runCatching { emailClient.deprovisionEmailAddress(address.id) }
                runCatching { sudoClient.deleteSudo(secondSudo) }
            }

            // Two aliases for each sudo
            repeat(NUM_ALIASES) { i ->
                val sudo = sudos[i % NUM_SUDOS]
                val aliasAddress = "${generateSafeLocalPart("alias$i")}@${aliasDomains.first()}"
                provisionedAliases.add(
                    provisionEmailAlias(aliasAddress, address.emailAddress, getOwnershipProof(sudo)),
                )
            }

            setupComplete = true
        }

    private suspend fun list(input: ListEmailAliasesInput): ListAPIResult.Success<EmailAliasListItem> {
        val result = emailClient.listEmailAliases(input)
        return when (result) {
            is ListAPIResult.Success -> result
            else -> fail("Unexpected ListAPIResult: $result")
        }
    }

    @Test
    fun listEmailAliasesShouldReturnEmptyListForSudoWithNoAliases() =
        runTest {
            val emptySudo = sudoClient.createSudo(TestData.sudo)
            try {
                val result = list(ListEmailAliasesInput(sudoId = emptySudo.id!!))

                result.result.items.size shouldBe 0
                result.result.nextToken shouldBe null
            } finally {
                runCatching { sudoClient.deleteSudo(emptySudo) }
            }
        }

    @Test
    fun listEmailAliasesShouldReturnEmptyListForNonExistentSudoId() =
        runTest {
            val result = list(ListEmailAliasesInput(sudoId = "nonExistentId"))

            result.result.items.size shouldBe 0
            result.result.nextToken shouldBe null
        }

    @Test
    fun listEmailAliasesShouldReturnFullListWithNoFilters() =
        runTest {
            val result = list(ListEmailAliasesInput())

            result.result.items.size shouldBe NUM_ALIASES
            result.result.nextToken shouldBe null

            val expected = provisionedAliases.first()
            val matched = result.result.items.find { it.id == expected.id }
            matched shouldNotBe null
            matched!!.owner shouldBe expected.owner
            matched.owners shouldBe expected.owners
            matched.identityId shouldBe expected.identityId
            matched.aliasAddress shouldBe expected.aliasAddress
            matched.realAddressType shouldBe expected.realAddressType
            matched.status shouldBe expected.status
            matched.metadata shouldBe expected.metadata
            matched.createdAt shouldBe expected.createdAt

            result.result.items
                .map { it.id }
                .toSet() shouldBe provisionedAliases.map { it.id }.toSet()
        }

    @Test
    fun listEmailAliasesShouldPaginateProperly() =
        runTest {
            val limit = 2
            val allIds = mutableListOf<String>()
            var nextToken: String? = null
            var numPages = 0
            do {
                val result = list(ListEmailAliasesInput(limit = limit, nextToken = nextToken))
                (result.result.items.size <= limit) shouldBe true
                allIds.addAll(result.result.items.map { it.id })
                nextToken = result.result.nextToken
                numPages++
            } while (nextToken != null)

            allIds.size shouldBe NUM_ALIASES
            // No duplicates across pages
            allIds.toSet().size shouldBe NUM_ALIASES
            numPages shouldBeGreaterThanOrEqual 2
        }

    @Test
    fun listEmailAliasesShouldFilterByStatus() =
        runTest {
            val aliasToDisable = provisionedAliases[0]
            val numEnabled = NUM_ALIASES - 1

            try {
                emailClient.disableEmailAlias(DisableEmailAliasInput(aliasToDisable.id))

                // eq
                val eqResult =
                    list(
                        ListEmailAliasesInput(
                            filter = EmailAliasFilterInput(status = EqualStatusFilter(EmailAliasStatus.ENABLED)),
                        ),
                    )
                eqResult.result.items.size shouldBe numEnabled
                eqResult.result.items.all { it.status == EmailAliasStatus.ENABLED } shouldBe true
                eqResult.result.items.any { it.id == aliasToDisable.id } shouldBe false

                // oneOf
                val oneOfResult =
                    list(
                        ListEmailAliasesInput(
                            filter = EmailAliasFilterInput(status = OneOfStatusFilter(listOf(EmailAliasStatus.ENABLED))),
                        ),
                    )
                oneOfResult.result.items.size shouldBe numEnabled
                oneOfResult.result.items.all { it.status == EmailAliasStatus.ENABLED } shouldBe true
                oneOfResult.result.items.any { it.id == aliasToDisable.id } shouldBe false

                // notOneOf
                val notOneOfResult =
                    list(
                        ListEmailAliasesInput(
                            filter = EmailAliasFilterInput(status = NotOneOfStatusFilter(listOf(EmailAliasStatus.ENABLED))),
                        ),
                    )
                notOneOfResult.result.items.size shouldBe 1
                notOneOfResult.result.items[0].id shouldBe aliasToDisable.id
                notOneOfResult.result.items[0].status shouldBe EmailAliasStatus.DISABLED
            } finally {
                // Re-enable so other tests see the expected state
                emailClient.enableEmailAlias(EnableEmailAliasInput(aliasToDisable.id))
            }
        }

    @Test
    fun listEmailAliasesShouldFilterBySudoId() =
        runTest {
            val sudoIndex = 0
            val sudoId = sudos[sudoIndex].id!!
            val expectedAliases = provisionedAliases.filter { alias -> alias.owners.any { it.id == sudoId } }
            expectedAliases.size shouldBe NUM_ALIASES / NUM_SUDOS

            val result = list(ListEmailAliasesInput(sudoId = sudoId))

            result.result.items.size shouldBe NUM_ALIASES / NUM_SUDOS
            result.result.items
                .map { it.id }
                .toSet() shouldBe expectedAliases.map { it.id }.toSet()
            result.result.items.all { alias -> alias.owners.any { it.id == sudoId } } shouldBe true
        }
}
