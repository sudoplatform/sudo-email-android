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
import com.sudoplatform.sudoemail.types.EmailAlias
import com.sudoplatform.sudoemail.types.EmailAliasStatus
import com.sudoplatform.sudoemail.types.inputs.DisableEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.EnableEmailAliasInput
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
 * Test the operation of [SudoEmailClient.disableEmailAlias].
 */
@RunWith(AndroidJUnit4::class)
class DisableEnableEmailAliasIntegrationTest : BaseIntegrationTest() {
    private val emailAliasList = mutableListOf<EmailAlias>()
    private val emailAddressList = mutableListOf<EmailAddress>()

    // Shared test resources
    private lateinit var ownershipProof: String
    private lateinit var testEmailAddress: EmailAddress
    private lateinit var aliasDomains: List<String>

    @Before
    fun setup() =
        runTest {
            // Check if email aliases are enabled, skip all tests if not
            val enabled = emailClient.getConfigurationData().emailAliasesEnabled
            Assume.assumeTrue("Test suite skipped due to aliases not being enabled.", enabled)

            ownershipProof = sharedOwnershipProof()
            aliasDomains = getAliasDomains(emailClient)

            // Create email address for testing
            testEmailAddress = provisionEmailAddress(emailClient, ownershipProof)
            emailAddressList.add(testEmailAddress)
        }

    @After
    fun teardown() =
        runTest {
            emailAliasList.map {
                emailClient.deprovisionEmailAlias(
                    com.sudoplatform.sudoemail.types.inputs
                        .DeprovisionEmailAliasInput(it.id),
                )
            }
            emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        }

    @Test
    fun disableEnableEmailAliasShouldReturnCorrectStatusEmailAlias() =
        runTest {
            // Generate a alias address and provision it
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            // Verify the alias is initially enabled
            provisionedAlias.status shouldBe EmailAliasStatus.ENABLED

            // Disable the email alias
            val disableInput = DisableEmailAliasInput(provisionedAlias.id)
            val disableResult = emailClient.disableEmailAlias(disableInput)

            with(disableResult) {
                id shouldBe provisionedAlias.id
                owner shouldBe provisionedAlias.owner
                owners shouldBe provisionedAlias.owners
                identityId shouldBe provisionedAlias.identityId
                aliasAddress shouldBe provisionedAlias.aliasAddress
                realAddress shouldBe provisionedAlias.realAddress
                realAddressType shouldBe provisionedAlias.realAddressType
                status shouldBe EmailAliasStatus.DISABLED
                inboundReceived shouldBe provisionedAlias.inboundReceived
                inboundDelivered shouldBe provisionedAlias.inboundDelivered
                outboundReceived shouldBe provisionedAlias.outboundReceived
                outboundDelivered shouldBe provisionedAlias.outboundDelivered
                spamCount shouldBe provisionedAlias.spamCount
                virusCount shouldBe provisionedAlias.virusCount
                expiresAt shouldBe provisionedAlias.expiresAt
                createdAt shouldBe provisionedAlias.createdAt
                updatedAt.time shouldBeGreaterThan provisionedAlias.updatedAt.time
                version shouldBe provisionedAlias.version + 1
                metadata shouldBe provisionedAlias.metadata
            }
            // Now enable the email alias
            val enableInput = EnableEmailAliasInput(provisionedAlias.id)
            val enableResult = emailClient.enableEmailAlias(enableInput)

            with(enableResult) {
                id shouldBe provisionedAlias.id
                owner shouldBe provisionedAlias.owner
                owners shouldBe provisionedAlias.owners
                identityId shouldBe provisionedAlias.identityId
                aliasAddress shouldBe provisionedAlias.aliasAddress
                realAddress shouldBe provisionedAlias.realAddress
                realAddressType shouldBe provisionedAlias.realAddressType
                status shouldBe EmailAliasStatus.ENABLED
                inboundReceived shouldBe provisionedAlias.inboundReceived
                inboundDelivered shouldBe provisionedAlias.inboundDelivered
                outboundReceived shouldBe provisionedAlias.outboundReceived
                outboundDelivered shouldBe provisionedAlias.outboundDelivered
                spamCount shouldBe provisionedAlias.spamCount
                virusCount shouldBe provisionedAlias.virusCount
                expiresAt shouldBe provisionedAlias.expiresAt
                createdAt shouldBe provisionedAlias.createdAt
                updatedAt.time shouldBeGreaterThan disableResult.updatedAt.time
                version shouldBe disableResult.version + 1
                metadata shouldBe provisionedAlias.metadata
            }
        }

    @Test
    fun disableEnableEmailAliasShouldWorkWithAliasWithMetadata() =
        runTest {
            // Generate a alias address with metadata and provision it
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"
            val metadata =
                mapOf(
                    "purpose" to "testing",
                    "environment" to "integration",
                )

            val provisionedAlias =
                provisionEmailAlias(
                    aliasAddress,
                    testEmailAddress.emailAddress,
                    ownershipProof,
                    metadata = metadata,
                )
            emailAliasList.add(provisionedAlias)

            // Disable the email alias
            val disableInput = DisableEmailAliasInput(provisionedAlias.id)
            val disableResult = emailClient.disableEmailAlias(disableInput)

            with(disableResult) {
                id shouldBe provisionedAlias.id
                status shouldBe EmailAliasStatus.DISABLED
                version shouldBe provisionedAlias.version + 1
                updatedAt.time shouldBeGreaterThan provisionedAlias.updatedAt.time
                this.metadata shouldBe metadata
            }
            // Enable the email alias
            val enableInput = EnableEmailAliasInput(provisionedAlias.id)
            val enableResult = emailClient.enableEmailAlias(enableInput)

            with(enableResult) {
                id shouldBe provisionedAlias.id
                status shouldBe EmailAliasStatus.ENABLED
                version shouldBe disableResult.version + 1
                updatedAt.time shouldBeGreaterThan disableResult.updatedAt.time
                this.metadata shouldBe metadata
            }
        }

    @Test
    fun disableEmailAliasShouldFailWithNonExistentAliasId() =
        runTest {
            val nonExistentId = "non-existent-alias-id"
            val disableInput = DisableEmailAliasInput(nonExistentId)

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasNotFoundException> {
                emailClient.disableEmailAlias(disableInput)
            }
        }

    @Test
    fun disableEmailAliasShouldWorkOnAlreadyDisabledAlias() =
        runTest {
            // Generate a alias address and provision it
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            // Disable the email alias for the first time
            val disableInput = DisableEmailAliasInput(provisionedAlias.id)
            val firstDisableResult = emailClient.disableEmailAlias(disableInput)
            firstDisableResult.status shouldBe EmailAliasStatus.DISABLED

            // Disable the email alias again - should succeed and return the already disabled alias
            val secondDisableResult = emailClient.disableEmailAlias(disableInput)
            with(secondDisableResult) {
                id shouldBe provisionedAlias.id
                status shouldBe EmailAliasStatus.DISABLED
                version shouldBeGreaterThanOrEqual firstDisableResult.version
                updatedAt shouldBe firstDisableResult.updatedAt
            }
        }

    @Test
    fun enableEmailAliasShouldFailWithNonExistentAliasId() =
        runTest {
            val nonExistentId = "non-existent-alias-id"
            val enableInput = EnableEmailAliasInput(nonExistentId)

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasNotFoundException> {
                emailClient.enableEmailAlias(enableInput)
            }
        }

    @Test
    fun enableEmailAliasShouldWorkOnAlreadyEnabledAlias() =
        runTest {
            // Generate a alias address and provision it (starts enabled)
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            // Verify the alias is initially enabled
            provisionedAlias.status shouldBe EmailAliasStatus.ENABLED

            // Enable the email alias again - should succeed and return the already enabled alias
            val enableInput = EnableEmailAliasInput(provisionedAlias.id)
            val enableResult = emailClient.enableEmailAlias(enableInput)
            with(enableResult) {
                id shouldBe provisionedAlias.id
                status shouldBe EmailAliasStatus.ENABLED
                // Version might stay the same or increment depending on implementation
                version shouldBe provisionedAlias.version
                updatedAt shouldBe provisionedAlias.updatedAt
            }
        }

    @Test
    fun disableAndEnableEmailAliasCycleShouldWorkCorrectly() =
        runTest {
            // Generate a alias address and provision it
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            val originalVersion = provisionedAlias.version

            // Disable the alias
            val disableInput = DisableEmailAliasInput(provisionedAlias.id)
            val disabledAlias = emailClient.disableEmailAlias(disableInput)
            disabledAlias.status shouldBe EmailAliasStatus.DISABLED
            disabledAlias.version shouldBe originalVersion + 1

            // Enable the alias
            val enableInput = EnableEmailAliasInput(provisionedAlias.id)
            val enabledAlias = emailClient.enableEmailAlias(enableInput)
            enabledAlias.status shouldBe EmailAliasStatus.ENABLED
            enabledAlias.version shouldBe originalVersion + 2

            // Disable again
            val secondDisabledAlias = emailClient.disableEmailAlias(disableInput)
            secondDisabledAlias.status shouldBe EmailAliasStatus.DISABLED
            secondDisabledAlias.version shouldBe originalVersion + 3

            // Enable again
            val secondEnabledAlias = emailClient.enableEmailAlias(enableInput)
            secondEnabledAlias.status shouldBe EmailAliasStatus.ENABLED
            secondEnabledAlias.version shouldBe originalVersion + 4

            // Verify all core properties remain consistent throughout the cycle
            with(secondEnabledAlias) {
                id shouldBe provisionedAlias.id
                owner shouldBe provisionedAlias.owner
                owners shouldBe provisionedAlias.owners
                identityId shouldBe provisionedAlias.identityId
                aliasAddress shouldBe provisionedAlias.aliasAddress
                realAddress shouldBe provisionedAlias.realAddress
                realAddressType shouldBe provisionedAlias.realAddressType
                createdAt shouldBe provisionedAlias.createdAt
                metadata shouldBe provisionedAlias.metadata
            }
        }
}
