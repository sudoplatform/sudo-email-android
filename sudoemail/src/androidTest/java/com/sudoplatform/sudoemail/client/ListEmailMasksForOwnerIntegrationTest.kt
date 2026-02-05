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
import com.sudoplatform.sudoemail.types.EmailMask
import com.sudoplatform.sudoemail.types.EmailMaskRealAddressType
import com.sudoplatform.sudoemail.types.EmailMaskStatus
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.DeprovisionEmailMaskInput
import com.sudoplatform.sudoemail.types.inputs.DisableEmailMaskInput
import com.sudoplatform.sudoemail.types.inputs.EmailMaskFilterInput
import com.sudoplatform.sudoemail.types.inputs.EqualRealAddressTypeFilter
import com.sudoplatform.sudoemail.types.inputs.EqualStatusFilter
import com.sudoplatform.sudoemail.types.inputs.ListEmailMasksForOwnerInput
import com.sudoplatform.sudoemail.types.inputs.NotEqualStatusFilter
import com.sudoplatform.sudoemail.types.inputs.NotOneOfStatusFilter
import com.sudoplatform.sudoemail.types.inputs.OneOfStatusFilter
import com.sudoplatform.sudoprofiles.Sudo
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
 * Test the operation of [SudoEmailClient.listEmailMasksForOwner].
 */
@RunWith(AndroidJUnit4::class)
class ListEmailMasksForOwnerIntegrationTest : BaseIntegrationTest() {
    private val emailMaskList = mutableListOf<EmailMask>()
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

    // Shared test resources
    private lateinit var testSudo: Sudo
    private lateinit var ownershipProof: String
    private lateinit var testEmailAddress: EmailAddress
    private lateinit var maskDomains: List<String>

    @Before
    fun setup() =
        runTest {
            sudoClient.reset()
            sudoClient.generateEncryptionKey()

            // Check if email masks are enabled, skip all tests if not
            val enabled = emailClient.getConfigurationData().emailMasksEnabled
            Assume.assumeTrue("Test suite skipped due to masks not being enabled.", enabled)

            // Create shared test resources
            testSudo = createSudo(TestData.sudo)
            testSudo.id shouldNotBe null
            sudoList.add(testSudo)

            ownershipProof = getOwnershipProof(testSudo)
            ownershipProof shouldNotBe null

            maskDomains = getMaskDomains(emailClient)
            maskDomains.size shouldBeGreaterThanOrEqual 1

            // Create an email address for testing
            testEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddressList.add(testEmailAddress)
        }

    @After
    fun teardown() =
        runTest {
            emailMaskList.map {
                emailClient.deprovisionEmailMask(DeprovisionEmailMaskInput(it.id))
            }
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
            sudoList.map { sudoClient.deleteSudo(it) }
            sudoClient.reset()
        }

    @Test
    fun listEmailMasksForOwnerShouldReturnEmptyListWithNoMasks() =
        runTest {
            val input = ListEmailMasksForOwnerInput()
            val result = emailClient.listEmailMasksForOwner(input)

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
    fun listEmailMasksForOwnerShouldReturnMultipleMasks() =
        runTest {
            // Provision multiple email masks using shared test resources
            val emailMasks = mutableListOf<EmailMask>()
            repeat(3) { index ->
                val maskLocalPart = generateSafeLocalPart("mask$index")
                val maskAddress = "$maskLocalPart@${maskDomains.first()}"
                val emailMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
                emailMasks.add(emailMask)
                emailMaskList.add(emailMask)
            }

            val input = ListEmailMasksForOwnerInput()
            val result = emailClient.listEmailMasksForOwner(input)

            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.size shouldBe 3
                    result.result.nextToken shouldBe null

                    // Verify all masks are present
                    val resultIds = result.result.items.map { it.id }
                    val expectedIds = emailMasks.map { it.id }
                    resultIds.containsAll(expectedIds) shouldBe true
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailMasksForOwnerWithStatusFilterShouldReturnCorrectResults() =
        runTest {
            // Provision email masks using shared test resources
            val maskLocalPart1 = generateSafeLocalPart("enabled-mask")
            val maskAddress1 = "$maskLocalPart1@${maskDomains.first()}"
            val enabledMask = provisionEmailMask(maskAddress1, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(enabledMask)

            val maskLocalPart2 = generateSafeLocalPart("disabled-mask")
            val maskAddress2 = "$maskLocalPart2@${maskDomains.first()}"
            val disabledMask = provisionEmailMask(maskAddress2, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(disabledMask)

            // Disable one mask
            emailClient.disableEmailMask(DisableEmailMaskInput(disabledMask.id))

            // Filter for enabled masks only
            val filter = EmailMaskFilterInput(status = EqualStatusFilter(EmailMaskStatus.ENABLED))
            val input = ListEmailMasksForOwnerInput(filter = filter)
            val result = emailClient.listEmailMasksForOwner(input)

            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.size shouldBe 1
                    result.result.items[0].id shouldBe enabledMask.id
                    result.result.items[0].status shouldBe EmailMaskStatus.ENABLED
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailMasksForOwnerWithOneOfStatusFilterShouldReturnCorrectResults() =
        runTest {
            // Provision email masks using shared test resources
            val maskLocalPart1 = generateSafeLocalPart("enabled-mask")
            val maskAddress1 = "$maskLocalPart1@${maskDomains.first()}"
            val enabledMask = provisionEmailMask(maskAddress1, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(enabledMask)

            val maskLocalPart2 = generateSafeLocalPart("disabled-mask")
            val maskAddress2 = "$maskLocalPart2@${maskDomains.first()}"
            val disabledMask = provisionEmailMask(maskAddress2, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(disabledMask)

            // Disable one mask
            emailClient.disableEmailMask(DisableEmailMaskInput(disabledMask.id))

            // Filter for multiple statuses
            val filter =
                EmailMaskFilterInput(
                    status = OneOfStatusFilter(listOf(EmailMaskStatus.ENABLED, EmailMaskStatus.DISABLED)),
                )
            val input = ListEmailMasksForOwnerInput(filter = filter)
            val result = emailClient.listEmailMasksForOwner(input)

            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.size shouldBe 2
                    val statuses = result.result.items.map { it.status }
                    statuses.contains(EmailMaskStatus.ENABLED) shouldBe true
                    statuses.contains(EmailMaskStatus.DISABLED) shouldBe true
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailMasksForOwnerWithNotOneOfStatusFilterShouldReturnCorrectResults() =
        runTest {
            // Provision email masks using shared test resources
            val maskLocalPart1 = generateSafeLocalPart("enabled-mask")
            val maskAddress1 = "$maskLocalPart1@${maskDomains.first()}"
            val enabledMask = provisionEmailMask(maskAddress1, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(enabledMask)

            val maskLocalPart2 = generateSafeLocalPart("disabled-mask")
            val maskAddress2 = "$maskLocalPart2@${maskDomains.first()}"
            val disabledMask = provisionEmailMask(maskAddress2, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(disabledMask)

            // Disable one mask
            emailClient.disableEmailMask(DisableEmailMaskInput(disabledMask.id))

            // Filter for statuses
            val filter =
                EmailMaskFilterInput(
                    status = NotOneOfStatusFilter(listOf(EmailMaskStatus.ENABLED)),
                )
            val input = ListEmailMasksForOwnerInput(filter = filter)
            val result = emailClient.listEmailMasksForOwner(input)

            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.size shouldBe 1
                    val statuses = result.result.items.map { it.status }
                    statuses.contains(EmailMaskStatus.DISABLED) shouldBe true
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailMasksForOwnerWithNotEqualStatusFilterShouldReturnCorrectResults() =
        runTest {
            // Provision email masks using shared test resources
            val maskLocalPart1 = generateSafeLocalPart("enabled-mask")
            val maskAddress1 = "$maskLocalPart1@${maskDomains.first()}"
            val enabledMask = provisionEmailMask(maskAddress1, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(enabledMask)

            val maskLocalPart2 = generateSafeLocalPart("disabled-mask")
            val maskAddress2 = "$maskLocalPart2@${maskDomains.first()}"
            val disabledMask = provisionEmailMask(maskAddress2, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(disabledMask)

            // Disable one mask
            emailClient.disableEmailMask(DisableEmailMaskInput(disabledMask.id))

            // Filter for not disabled masks
            val filter = EmailMaskFilterInput(status = NotEqualStatusFilter(EmailMaskStatus.DISABLED))
            val input = ListEmailMasksForOwnerInput(filter = filter)
            val result = emailClient.listEmailMasksForOwner(input)

            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.size shouldBe 1
                    result.result.items[0].id shouldBe enabledMask.id
                    result.result.items[0].status shouldBe EmailMaskStatus.ENABLED
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
        }

    @Test
    fun listEmailMasksForOwnerWithRealAddressTypeFilterShouldReturnCorrectResults() =
        runTest {
            // Provision email mask using shared test resources
            val maskLocalPart = generateSafeLocalPart("internal-mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"
            val emailMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(emailMask)

            // Filter for internal real address type
            val filter =
                EmailMaskFilterInput(
                    realAddressType = EqualRealAddressTypeFilter(EmailMaskRealAddressType.INTERNAL),
                )
            val input = ListEmailMasksForOwnerInput(filter = filter)
            val result = emailClient.listEmailMasksForOwner(input)

            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.size shouldBe 1
                    result.result.items[0].id shouldBe emailMask.id
                    result.result.items[0].realAddressType shouldBe EmailMaskRealAddressType.INTERNAL
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }
            // Filter for external real address type
            val extFilter =
                EmailMaskFilterInput(
                    realAddressType = EqualRealAddressTypeFilter(EmailMaskRealAddressType.EXTERNAL),
                )
            val extResult = emailClient.listEmailMasksForOwner(ListEmailMasksForOwnerInput(filter = extFilter))

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
    fun listEmailMasksForOwnerWithPaginationShouldReturnCorrectResults() =
        runTest {
            // Provision multiple email masks using shared test resources
            repeat(5) { index ->
                val maskLocalPart = generateSafeLocalPart("mask$index")
                val maskAddress = "$maskLocalPart@${maskDomains.first()}"
                val emailMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
                emailMaskList.add(emailMask)
            }

            // First page
            val firstInput = ListEmailMasksForOwnerInput(limit = 2)
            val firstResult = emailClient.listEmailMasksForOwner(firstInput)

            when (firstResult) {
                is ListAPIResult.Success -> {
                    firstResult.result.items.size shouldBe 2
                    firstResult.result.nextToken shouldNotBe null

                    // Second page
                    val secondInput =
                        ListEmailMasksForOwnerInput(
                            limit = 2,
                            nextToken = firstResult.result.nextToken,
                        )
                    val secondResult = emailClient.listEmailMasksForOwner(secondInput)

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
