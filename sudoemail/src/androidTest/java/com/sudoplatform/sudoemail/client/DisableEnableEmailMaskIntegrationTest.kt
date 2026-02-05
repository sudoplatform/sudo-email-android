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
import com.sudoplatform.sudoemail.types.EmailMaskStatus
import com.sudoplatform.sudoemail.types.inputs.DisableEmailMaskInput
import com.sudoplatform.sudoemail.types.inputs.EnableEmailMaskInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.disableEmailMask].
 */
@RunWith(AndroidJUnit4::class)
class DisableEnableEmailMaskIntegrationTest : BaseIntegrationTest() {
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

            // Create email address for testing
            testEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddressList.add(testEmailAddress)
        }

    @After
    fun teardown() =
        runTest {
            emailMaskList.map {
                emailClient.deprovisionEmailMask(
                    com.sudoplatform.sudoemail.types.inputs
                        .DeprovisionEmailMaskInput(it.id),
                )
            }
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
            sudoList.map { sudoClient.deleteSudo(it) }
            sudoClient.reset()
        }

    @Test
    fun disableEnableEmailMaskShouldReturnCorrectStatusEmailMask() =
        runTest {
            // Generate a mask address and provision it
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            // Verify the mask is initially enabled
            provisionedMask.status shouldBe EmailMaskStatus.ENABLED

            // Disable the email mask
            val disableInput = DisableEmailMaskInput(provisionedMask.id)
            val disableResult = emailClient.disableEmailMask(disableInput)

            with(disableResult) {
                id shouldBe provisionedMask.id
                owner shouldBe provisionedMask.owner
                owners shouldBe provisionedMask.owners
                identityId shouldBe provisionedMask.identityId
                maskAddress shouldBe provisionedMask.maskAddress
                realAddress shouldBe provisionedMask.realAddress
                realAddressType shouldBe provisionedMask.realAddressType
                status shouldBe EmailMaskStatus.DISABLED
                inboundReceived shouldBe provisionedMask.inboundReceived
                inboundDelivered shouldBe provisionedMask.inboundDelivered
                outboundReceived shouldBe provisionedMask.outboundReceived
                outboundDelivered shouldBe provisionedMask.outboundDelivered
                spamCount shouldBe provisionedMask.spamCount
                virusCount shouldBe provisionedMask.virusCount
                expiresAt shouldBe provisionedMask.expiresAt
                createdAt shouldBe provisionedMask.createdAt
                updatedAt.time shouldBeGreaterThan provisionedMask.updatedAt.time
                version shouldBe provisionedMask.version + 1
                metadata shouldBe provisionedMask.metadata
            }
            // Now enable the email mask
            val enableInput = EnableEmailMaskInput(provisionedMask.id)
            val enableResult = emailClient.enableEmailMask(enableInput)

            with(enableResult) {
                id shouldBe provisionedMask.id
                owner shouldBe provisionedMask.owner
                owners shouldBe provisionedMask.owners
                identityId shouldBe provisionedMask.identityId
                maskAddress shouldBe provisionedMask.maskAddress
                realAddress shouldBe provisionedMask.realAddress
                realAddressType shouldBe provisionedMask.realAddressType
                status shouldBe EmailMaskStatus.ENABLED
                inboundReceived shouldBe provisionedMask.inboundReceived
                inboundDelivered shouldBe provisionedMask.inboundDelivered
                outboundReceived shouldBe provisionedMask.outboundReceived
                outboundDelivered shouldBe provisionedMask.outboundDelivered
                spamCount shouldBe provisionedMask.spamCount
                virusCount shouldBe provisionedMask.virusCount
                expiresAt shouldBe provisionedMask.expiresAt
                createdAt shouldBe provisionedMask.createdAt
                updatedAt.time shouldBeGreaterThan disableResult.updatedAt.time
                version shouldBe disableResult.version + 1
                metadata shouldBe provisionedMask.metadata
            }
        }

    @Test
    fun disableEnableEmailMaskShouldWorkWithMaskWithMetadata() =
        runTest {
            // Generate a mask address with metadata and provision it
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"
            val metadata =
                mapOf(
                    "purpose" to "testing",
                    "environment" to "integration",
                )

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof, metadata = metadata)
            emailMaskList.add(provisionedMask)

            // Disable the email mask
            val disableInput = DisableEmailMaskInput(provisionedMask.id)
            val disableResult = emailClient.disableEmailMask(disableInput)

            with(disableResult) {
                id shouldBe provisionedMask.id
                status shouldBe EmailMaskStatus.DISABLED
                version shouldBe provisionedMask.version + 1
                updatedAt.time shouldBeGreaterThan provisionedMask.updatedAt.time
                this.metadata shouldBe metadata
            }
            // Enable the email mask
            val enableInput = EnableEmailMaskInput(provisionedMask.id)
            val enableResult = emailClient.enableEmailMask(enableInput)

            with(enableResult) {
                id shouldBe provisionedMask.id
                status shouldBe EmailMaskStatus.ENABLED
                version shouldBe disableResult.version + 1
                updatedAt.time shouldBeGreaterThan disableResult.updatedAt.time
                this.metadata shouldBe metadata
            }
        }

    @Test
    fun disableEmailMaskShouldFailWithNonExistentMaskId() =
        runTest {
            val nonExistentId = "non-existent-mask-id"
            val disableInput = DisableEmailMaskInput(nonExistentId)

            shouldThrow<SudoEmailClient.EmailMaskException.EmailMaskNotFoundException> {
                emailClient.disableEmailMask(disableInput)
            }
        }

    @Test
    fun disableEmailMaskShouldWorkOnAlreadyDisabledMask() =
        runTest {
            // Generate a mask address and provision it
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            // Disable the email mask for the first time
            val disableInput = DisableEmailMaskInput(provisionedMask.id)
            val firstDisableResult = emailClient.disableEmailMask(disableInput)
            firstDisableResult.status shouldBe EmailMaskStatus.DISABLED

            // Disable the email mask again - should succeed and return the already disabled mask
            val secondDisableResult = emailClient.disableEmailMask(disableInput)
            with(secondDisableResult) {
                id shouldBe provisionedMask.id
                status shouldBe EmailMaskStatus.DISABLED
                version shouldBeGreaterThanOrEqual firstDisableResult.version
                updatedAt shouldBe firstDisableResult.updatedAt
            }
        }

    @Test
    fun enableEmailMaskShouldFailWithNonExistentMaskId() =
        runTest {
            val nonExistentId = "non-existent-mask-id"
            val enableInput = EnableEmailMaskInput(nonExistentId)

            shouldThrow<SudoEmailClient.EmailMaskException.EmailMaskNotFoundException> {
                emailClient.enableEmailMask(enableInput)
            }
        }

    @Test
    fun enableEmailMaskShouldWorkOnAlreadyEnabledMask() =
        runTest {
            // Generate a mask address and provision it (starts enabled)
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            // Verify the mask is initially enabled
            provisionedMask.status shouldBe EmailMaskStatus.ENABLED

            // Enable the email mask again - should succeed and return the already enabled mask
            val enableInput = EnableEmailMaskInput(provisionedMask.id)
            val enableResult = emailClient.enableEmailMask(enableInput)
            with(enableResult) {
                id shouldBe provisionedMask.id
                status shouldBe EmailMaskStatus.ENABLED
                // Version might stay the same or increment depending on implementation
                version shouldBe provisionedMask.version
                updatedAt shouldBe provisionedMask.updatedAt
            }
        }

    @Test
    fun disableAndEnableEmailMaskCycleShouldWorkCorrectly() =
        runTest {
            // Generate a mask address and provision it
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            val originalVersion = provisionedMask.version

            // Disable the mask
            val disableInput = DisableEmailMaskInput(provisionedMask.id)
            val disabledMask = emailClient.disableEmailMask(disableInput)
            disabledMask.status shouldBe EmailMaskStatus.DISABLED
            disabledMask.version shouldBe originalVersion + 1

            // Enable the mask
            val enableInput = EnableEmailMaskInput(provisionedMask.id)
            val enabledMask = emailClient.enableEmailMask(enableInput)
            enabledMask.status shouldBe EmailMaskStatus.ENABLED
            enabledMask.version shouldBe originalVersion + 2

            // Disable again
            val secondDisabledMask = emailClient.disableEmailMask(disableInput)
            secondDisabledMask.status shouldBe EmailMaskStatus.DISABLED
            secondDisabledMask.version shouldBe originalVersion + 3

            // Enable again
            val secondEnabledMask = emailClient.enableEmailMask(enableInput)
            secondEnabledMask.status shouldBe EmailMaskStatus.ENABLED
            secondEnabledMask.version shouldBe originalVersion + 4

            // Verify all core properties remain consistent throughout the cycle
            with(secondEnabledMask) {
                id shouldBe provisionedMask.id
                owner shouldBe provisionedMask.owner
                owners shouldBe provisionedMask.owners
                identityId shouldBe provisionedMask.identityId
                maskAddress shouldBe provisionedMask.maskAddress
                realAddress shouldBe provisionedMask.realAddress
                realAddressType shouldBe provisionedMask.realAddressType
                createdAt shouldBe provisionedMask.createdAt
                metadata shouldBe provisionedMask.metadata
            }
        }
}
