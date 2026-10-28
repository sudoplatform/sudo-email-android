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
import com.sudoplatform.sudoemail.types.EmailAlias
import com.sudoplatform.sudoemail.types.EmailAliasRealAddressType
import com.sudoplatform.sudoemail.types.EmailAliasStatus
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.DeprovisionEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.DisableEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.EmailAliasFilterInput
import com.sudoplatform.sudoemail.types.inputs.EqualRealAddressTypeFilter
import com.sudoplatform.sudoemail.types.inputs.EqualStatusFilter
import com.sudoplatform.sudoemail.types.inputs.ListEmailAliasesForOwnerInput
import com.sudoplatform.sudoemail.types.inputs.NotEqualStatusFilter
import com.sudoplatform.sudoemail.types.inputs.NotOneOfStatusFilter
import com.sudoplatform.sudoemail.types.inputs.OneOfStatusFilter
import io.kotlintest.fail
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.listEmailAliasesForOwner].
 */
@RunWith(AndroidJUnit4::class)
class ListEmailAliasesForOwnerIntegrationTest : BaseIntegrationTest() {
    private val emailAliasList = mutableListOf<EmailAlias>()
    private val emailAddressList = mutableListOf<EmailAddress>()

    // Shared test resources
    private lateinit var ownershipProof: String
    private lateinit var testEmailAddress: EmailAddress
    private lateinit var aliasDomains: List<String>

    @Before
    fun setup() =
        runTest {
            // Check if email masks are enabled, skip all tests if not
            val enabled = emailClient.getConfigurationData().emailAliasesEnabled
            Assume.assumeTrue("Test suite skipped due to masks not being enabled.", enabled)

            // Create shared test resources
            ownershipProof = sharedOwnershipProof()

            aliasDomains = getAliasDomains(emailClient)
            aliasDomains.size shouldBeGreaterThanOrEqual 1

            // Create an email address for testing
            testEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddressList.add(testEmailAddress)
        }

    @After
    fun teardown() =
        runTest {
            emailAliasList.map {
                emailClient.deprovisionEmailAlias(DeprovisionEmailAliasInput(it.id))
            }
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        }

    @Test
    fun listEmailAliasesForOwnerShouldReturnEmptyListWithNoAliases() =
        runTest {
            val input = ListEmailAliasesForOwnerInput()
            val result = emailClient.listEmailAliasesForOwner(input)

            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.size shouldBe 0
                    result.result.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailAliasesForOwnerShouldReturnMultipleAliases() =
        runTest {
            // Provision multiple email aliases using shared test resources
            val emailAliases = mutableListOf<EmailAlias>()
            repeat(3) { index ->
                val aliasLocalPart = generateSafeLocalPart("alias$index")
                val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"
                val emailAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
                emailAliases.add(emailAlias)
                emailAliasList.add(emailAlias)
            }

            val input = ListEmailAliasesForOwnerInput()
            val result = emailClient.listEmailAliasesForOwner(input)

            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.size shouldBe 3
                    result.result.nextToken shouldBe null

                    // Verify all aliases are present
                    val resultIds = result.result.items.map { it.id }
                    val expectedIds = emailAliases.map { it.id }
                    resultIds.containsAll(expectedIds) shouldBe true
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailAliasesForOwnerWithStatusFilterShouldReturnCorrectResults() =
        runTest {
            // Provision email aliases using shared test resources
            val aliasLocalPart1 = generateSafeLocalPart("enabled-alias")
            val aliasAddress1 = "$aliasLocalPart1@${aliasDomains.first()}"
            val enabledAlias = provisionEmailAlias(aliasAddress1, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(enabledAlias)

            val aliasLocalPart2 = generateSafeLocalPart("disabled-alias")
            val aliasAddress2 = "$aliasLocalPart2@${aliasDomains.first()}"
            val disabledAlias = provisionEmailAlias(aliasAddress2, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(disabledAlias)

            // Disable one alias
            emailClient.disableEmailAlias(DisableEmailAliasInput(disabledAlias.id))

            // Filter for enabled aliases only
            val filter = EmailAliasFilterInput(status = EqualStatusFilter(EmailAliasStatus.ENABLED))
            val input = ListEmailAliasesForOwnerInput(filter = filter)
            val result = emailClient.listEmailAliasesForOwner(input)

            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.size shouldBe 1
                    result.result.items[0].id shouldBe enabledAlias.id
                    result.result.items[0].status shouldBe EmailAliasStatus.ENABLED
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailAliasesForOwnerWithOneOfStatusFilterShouldReturnCorrectResults() =
        runTest {
            // Provision email aliases using shared test resources
            val aliasLocalPart1 = generateSafeLocalPart("enabled-alias")
            val aliasAddress1 = "$aliasLocalPart1@${aliasDomains.first()}"
            val enabledAlias = provisionEmailAlias(aliasAddress1, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(enabledAlias)

            val aliasLocalPart2 = generateSafeLocalPart("disabled-alias")
            val aliasAddress2 = "$aliasLocalPart2@${aliasDomains.first()}"
            val disabledAlias = provisionEmailAlias(aliasAddress2, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(disabledAlias)

            // Disable one alias
            emailClient.disableEmailAlias(DisableEmailAliasInput(disabledAlias.id))

            // Filter for multiple statuses
            val filter =
                EmailAliasFilterInput(
                    status = OneOfStatusFilter(listOf(EmailAliasStatus.ENABLED, EmailAliasStatus.DISABLED)),
                )
            val input = ListEmailAliasesForOwnerInput(filter = filter)
            val result = emailClient.listEmailAliasesForOwner(input)

            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.size shouldBe 2
                    val statuses = result.result.items.map { it.status }
                    statuses.contains(EmailAliasStatus.ENABLED) shouldBe true
                    statuses.contains(EmailAliasStatus.DISABLED) shouldBe true
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailAliasesForOwnerWithNotOneOfStatusFilterShouldReturnCorrectResults() =
        runTest {
            // Provision email aliases using shared test resources
            val aliasLocalPart1 = generateSafeLocalPart("enabled-alias")
            val aliasAddress1 = "$aliasLocalPart1@${aliasDomains.first()}"
            val enabledAlias = provisionEmailAlias(aliasAddress1, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(enabledAlias)

            val aliasLocalPart2 = generateSafeLocalPart("disabled-alias")
            val aliasAddress2 = "$aliasLocalPart2@${aliasDomains.first()}"
            val disabledAlias = provisionEmailAlias(aliasAddress2, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(disabledAlias)

            // Disable one alias
            emailClient.disableEmailAlias(DisableEmailAliasInput(disabledAlias.id))

            // Filter for statuses
            val filter =
                EmailAliasFilterInput(
                    status = NotOneOfStatusFilter(listOf(EmailAliasStatus.ENABLED)),
                )
            val input = ListEmailAliasesForOwnerInput(filter = filter)
            val result = emailClient.listEmailAliasesForOwner(input)

            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.size shouldBe 1
                    val statuses = result.result.items.map { it.status }
                    statuses.contains(EmailAliasStatus.DISABLED) shouldBe true
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailAliasesForOwnerWithNotEqualStatusFilterShouldReturnCorrectResults() =
        runTest {
            // Provision email aliases using shared test resources
            val aliasLocalPart1 = generateSafeLocalPart("enabled-alias")
            val aliasAddress1 = "$aliasLocalPart1@${aliasDomains.first()}"
            val enabledAlias = provisionEmailAlias(aliasAddress1, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(enabledAlias)

            val aliasLocalPart2 = generateSafeLocalPart("disabled-alias")
            val aliasAddress2 = "$aliasLocalPart2@${aliasDomains.first()}"
            val disabledAlias = provisionEmailAlias(aliasAddress2, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(disabledAlias)

            // Disable one alias
            emailClient.disableEmailAlias(DisableEmailAliasInput(disabledAlias.id))

            // Filter for not disabled aliases
            val filter = EmailAliasFilterInput(status = NotEqualStatusFilter(EmailAliasStatus.DISABLED))
            val input = ListEmailAliasesForOwnerInput(filter = filter)
            val result = emailClient.listEmailAliasesForOwner(input)

            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.size shouldBe 1
                    result.result.items[0].id shouldBe enabledAlias.id
                    result.result.items[0].status shouldBe EmailAliasStatus.ENABLED
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailAliasesForOwnerWithRealAddressTypeFilterShouldReturnCorrectResults() =
        runTest {
            // Provision email alias using shared test resources
            val aliasLocalPart = generateSafeLocalPart("internal-alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"
            val emailAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(emailAlias)

            // Filter for internal real address type
            val filter =
                EmailAliasFilterInput(
                    realAddressType = EqualRealAddressTypeFilter(EmailAliasRealAddressType.INTERNAL),
                )
            val input = ListEmailAliasesForOwnerInput(filter = filter)
            val result = emailClient.listEmailAliasesForOwner(input)

            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.size shouldBe 1
                    result.result.items[0].id shouldBe emailAlias.id
                    result.result.items[0].realAddressType shouldBe EmailAliasRealAddressType.INTERNAL
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
            // Filter for external real address type
            val extFilter =
                EmailAliasFilterInput(
                    realAddressType = EqualRealAddressTypeFilter(EmailAliasRealAddressType.EXTERNAL),
                )
            val extResult = emailClient.listEmailAliasesForOwner(ListEmailAliasesForOwnerInput(filter = extFilter))

            when (extResult) {
                is ListAPIResult.Success -> {
                    extResult.result.items.size shouldBe 0
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailAliasesForOwnerWithPaginationShouldReturnCorrectResults() =
        runTest {
            // Provision multiple email aliases using shared test resources
            repeat(5) { index ->
                val aliasLocalPart = generateSafeLocalPart("alias$index")
                val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"
                val emailAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
                emailAliasList.add(emailAlias)
            }

            // First page
            val firstInput = ListEmailAliasesForOwnerInput(limit = 2)
            val firstResult = emailClient.listEmailAliasesForOwner(firstInput)

            when (firstResult) {
                is ListAPIResult.Success -> {
                    firstResult.result.items.size shouldBe 2
                    firstResult.result.nextToken shouldNotBe null

                    // Second page
                    val secondInput =
                        ListEmailAliasesForOwnerInput(
                            limit = 2,
                            nextToken = firstResult.result.nextToken,
                        )
                    val secondResult = emailClient.listEmailAliasesForOwner(secondInput)

                    when (secondResult) {
                        is ListAPIResult.Success -> {
                            secondResult.result.items.size shouldBe 2
                            secondResult.result.nextToken shouldNotBe null

                            // Verify no overlap between pages
                            val firstPageIds = firstResult.result.items.map { it.id }
                            val secondPageIds = secondResult.result.items.map { it.id }
                            firstPageIds.intersect(secondPageIds.toSet()).isEmpty() shouldBe true
                        }
                        else -> {
                            fail("Unexpected ListAPIResult for second page")
                        }
                    }
                }
                else -> {
                    fail("Unexpected ListAPIResult for first page")
                }
            }
        }
}
