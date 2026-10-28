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
import com.sudoplatform.sudoemail.types.inputs.DisableEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailAliasInput
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
import java.util.Calendar
import java.util.Date

/**
 * Test the operation of [SudoEmailClient.updateEmailAlias].
 */
@RunWith(AndroidJUnit4::class)
class UpdateEmailAliasIntegrationTest : BaseIntegrationTest() {
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
    fun updateEmailAliasShouldUpdateMetadataSuccessfully() =
        runTest {
            // Generate an alias address and provision it with initial metadata using shared test resources
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val initialMetadata =
                mapOf(
                    "purpose" to "initial",
                    "environment" to "test",
                )

            val provisionedAlias =
                provisionEmailAlias(
                    aliasAddress,
                    testEmailAddress.emailAddress,
                    ownershipProof,
                    metadata = initialMetadata,
                )
            emailAliasList.add(provisionedAlias)

            // Update the metadata
            val updatedMetadata =
                mapOf(
                    "purpose" to "updated",
                    "environment" to "integration",
                    "version" to "2.0",
                )

            val updateInput =
                UpdateEmailAliasInput(
                    emailAliasId = provisionedAlias.id,
                    metadata = updatedMetadata,
                )
            val result = emailClient.updateEmailAlias(updateInput)

            with(result) {
                id shouldBe provisionedAlias.id
                owner shouldBe provisionedAlias.owner
                owners shouldBe provisionedAlias.owners
                identityId shouldBe provisionedAlias.identityId
                aliasAddress shouldBe provisionedAlias.aliasAddress
                realAddress shouldBe provisionedAlias.realAddress
                realAddressType shouldBe provisionedAlias.realAddressType
                status shouldBe provisionedAlias.status
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
                metadata shouldBe updatedMetadata
            }
        }

    @Test
    fun updateEmailAliasShouldUpdateExpirationSuccessfully() =
        runTest {
            // Generate an alias address and provision it without expiration using shared test resources
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            // Verify no initial expiration
            provisionedAlias.expiresAt shouldBe null

            // Update with an expiration date
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 30)
            val newExpirationDate = calendar.time

            val updateInput =
                UpdateEmailAliasInput(
                    emailAliasId = provisionedAlias.id,
                    expiresAt = newExpirationDate,
                )
            val result = emailClient.updateEmailAlias(updateInput)

            with(result) {
                id shouldBe provisionedAlias.id
                owner shouldBe provisionedAlias.owner
                owners shouldBe provisionedAlias.owners
                identityId shouldBe provisionedAlias.identityId
                aliasAddress shouldBe provisionedAlias.aliasAddress
                realAddress shouldBe provisionedAlias.realAddress
                realAddressType shouldBe provisionedAlias.realAddressType
                status shouldBe provisionedAlias.status
                createdAt shouldBe provisionedAlias.createdAt
                updatedAt.time shouldBeGreaterThan provisionedAlias.updatedAt.time
                version shouldBe provisionedAlias.version + 1
                expiresAt shouldNotBe null
                expiresAt!!.time / 1000 shouldBe newExpirationDate.time / 1000
                metadata shouldBe provisionedAlias.metadata
            }
        }

    @Test
    fun updateEmailAliasShouldUpdateBothMetadataAndExpiration() =
        runTest {
            // Generate an alias address and provision it with initial metadata using shared test resources
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val initialMetadata = mapOf("initial" to "data")

            val provisionedAlias =
                provisionEmailAlias(
                    aliasAddress,
                    testEmailAddress.emailAddress,
                    ownershipProof,
                    metadata = initialMetadata,
                )
            emailAliasList.add(provisionedAlias)

            // Update both metadata and expiration
            val updatedMetadata =
                mapOf(
                    "purpose" to "comprehensive-test",
                    "updated" to "true",
                )

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 60)
            val newExpirationDate = calendar.time

            val updateInput =
                UpdateEmailAliasInput(
                    emailAliasId = provisionedAlias.id,
                    metadata = updatedMetadata,
                    expiresAt = newExpirationDate,
                )
            val result = emailClient.updateEmailAlias(updateInput)

            with(result) {
                id shouldBe provisionedAlias.id
                version shouldBe provisionedAlias.version + 1
                updatedAt.time shouldBeGreaterThan provisionedAlias.updatedAt.time
                metadata shouldBe updatedMetadata
                expiresAt shouldNotBe null
                (expiresAt!!.time / 1000) shouldBe (newExpirationDate.time / 1000)
                // All other properties should remain unchanged
                owner shouldBe provisionedAlias.owner
                aliasAddress shouldBe provisionedAlias.aliasAddress
                realAddress shouldBe provisionedAlias.realAddress
                status shouldBe provisionedAlias.status
                createdAt shouldBe provisionedAlias.createdAt
            }
        }

    @Test
    fun updateEmailAliasShouldRemoveMetadataWithEmptyMap() =
        runTest {
            // Generate an alias address and provision it with metadata using shared test resources
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val initialMetadata =
                mapOf(
                    "key1" to "value1",
                    "key2" to "value2",
                    "key3" to "value3",
                )

            val provisionedAlias =
                provisionEmailAlias(
                    aliasAddress,
                    testEmailAddress.emailAddress,
                    ownershipProof,
                    metadata = initialMetadata,
                )
            emailAliasList.add(provisionedAlias)

            // Verify initial metadata exists
            provisionedAlias.metadata shouldBe initialMetadata

            // Update with empty metadata map to remove all metadata
            val updateInput =
                UpdateEmailAliasInput(
                    emailAliasId = provisionedAlias.id,
                    metadata = emptyMap(),
                )
            val result = emailClient.updateEmailAlias(updateInput)

            with(result) {
                id shouldBe provisionedAlias.id
                version shouldBe provisionedAlias.version + 1
                updatedAt.time shouldBeGreaterThan provisionedAlias.updatedAt.time
                metadata shouldBe null
                // All other properties should remain unchanged
                owner shouldBe provisionedAlias.owner
                aliasAddress shouldBe provisionedAlias.aliasAddress
                realAddress shouldBe provisionedAlias.realAddress
                status shouldBe provisionedAlias.status
                expiresAt shouldBe provisionedAlias.expiresAt
                createdAt shouldBe provisionedAlias.createdAt
            }
        }

    @Test
    fun updateEmailAliasShouldRemoveExpirationWithZeroDate() =
        runTest {
            // Generate an alias address and provision it with an expiration date using shared test resources
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 15)
            val initialExpirationDate = calendar.time

            val provisionedAlias =
                provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof, expiresAt = initialExpirationDate)
            emailAliasList.add(provisionedAlias)

            // Verify initial expiration exists
            provisionedAlias.expiresAt shouldNotBe null

            // Update with zero date to remove expiration
            val zeroDate = Date(0)
            val updateInput =
                UpdateEmailAliasInput(
                    emailAliasId = provisionedAlias.id,
                    expiresAt = zeroDate,
                )
            val result = emailClient.updateEmailAlias(updateInput)

            with(result) {
                id shouldBe provisionedAlias.id
                version shouldBe provisionedAlias.version + 1
                updatedAt.time shouldBeGreaterThan provisionedAlias.updatedAt.time
                expiresAt shouldBe null
                // All other properties should remain unchanged
                owner shouldBe provisionedAlias.owner
                aliasAddress shouldBe provisionedAlias.aliasAddress
                realAddress shouldBe provisionedAlias.realAddress
                status shouldBe provisionedAlias.status
                metadata shouldBe provisionedAlias.metadata
                createdAt shouldBe provisionedAlias.createdAt
            }
        }

    @Test
    fun updateEmailAliasShouldFailWithNonExistentAliasId() =
        runTest {
            val nonExistentId = "non-existent-alias-id"
            val updateInput =
                UpdateEmailAliasInput(
                    emailAliasId = nonExistentId,
                    metadata = mapOf("test" to "data"),
                )

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasNotFoundException> {
                emailClient.updateEmailAlias(updateInput)
            }
        }

    @Test
    fun updateEmailAliasShouldWorkWithDisabledAlias() =
        runTest {
            // Generate an alias address and provision it using shared test resources
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            // Disable the alias first
            val disabledAlias =
                emailClient.disableEmailAlias(
                    DisableEmailAliasInput(provisionedAlias.id),
                )
            disabledAlias.status shouldBe EmailAliasStatus.DISABLED

            // Update the disabled alias
            val updatedMetadata = mapOf("status" to "disabled-but-updated")
            val updateInput =
                UpdateEmailAliasInput(
                    emailAliasId = provisionedAlias.id,
                    metadata = updatedMetadata,
                )
            val result = emailClient.updateEmailAlias(updateInput)

            with(result) {
                id shouldBe provisionedAlias.id
                status shouldBe EmailAliasStatus.DISABLED // Status should remain disabled
                version shouldBe disabledAlias.version + 1
                updatedAt.time shouldBeGreaterThan disabledAlias.updatedAt.time
                metadata shouldBe updatedMetadata
            }
        }

    @Test
    fun updateEmailAliasShouldHandleMultipleUpdates() =
        runTest {
            // Generate an alias address and provision it using shared test resources
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)

            val originalVersion = provisionedAlias.version

            // First update - add metadata
            val firstUpdateInput =
                UpdateEmailAliasInput(
                    emailAliasId = provisionedAlias.id,
                    metadata = mapOf("update" to "first"),
                )
            val firstResult = emailClient.updateEmailAlias(firstUpdateInput)
            firstResult.version shouldBe originalVersion + 1
            firstResult.metadata shouldBe mapOf("update" to "first")

            // Second update - update metadata
            val secondUpdateInput =
                UpdateEmailAliasInput(
                    emailAliasId = provisionedAlias.id,
                    metadata = mapOf("update" to "second", "additional" to "data"),
                )
            val secondResult = emailClient.updateEmailAlias(secondUpdateInput)
            secondResult.version shouldBe originalVersion + 2
            secondResult.metadata shouldBe mapOf("update" to "second", "additional" to "data")

            // Third update - add expiration
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 45)
            val expirationDate = calendar.time

            val thirdUpdateInput =
                UpdateEmailAliasInput(
                    emailAliasId = provisionedAlias.id,
                    expiresAt = expirationDate,
                )
            val thirdResult = emailClient.updateEmailAlias(thirdUpdateInput)
            thirdResult.version shouldBe originalVersion + 3
            thirdResult.expiresAt shouldNotBe null
            thirdResult.expiresAt!!.time / 1000 shouldBe expirationDate.time / 1000
            thirdResult.metadata shouldBe mapOf("update" to "second", "additional" to "data") // Metadata should be preserved

            // Verify all core properties remain consistent throughout updates
            with(thirdResult) {
                id shouldBe provisionedAlias.id
                owner shouldBe provisionedAlias.owner
                owners shouldBe provisionedAlias.owners
                identityId shouldBe provisionedAlias.identityId
                aliasAddress shouldBe provisionedAlias.aliasAddress
                realAddress shouldBe provisionedAlias.realAddress
                realAddressType shouldBe provisionedAlias.realAddressType
                status shouldBe provisionedAlias.status
                createdAt shouldBe provisionedAlias.createdAt
            }
        }

    @Test
    fun updateEmailAliasShouldWorkWithNullInputs() =
        runTest {
            // Generate an alias address and provision it with initial data using shared test resources
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 10)
            val initialExpiration = calendar.time

            val initialMetadata = mapOf("initial" to "data")

            val provisionedAlias =
                provisionEmailAlias(
                    aliasAddress,
                    testEmailAddress.emailAddress,
                    ownershipProof,
                    metadata = initialMetadata,
                    expiresAt = initialExpiration,
                )
            emailAliasList.add(provisionedAlias)

            // Update with null values (should not change existing values)
            val updateInput =
                UpdateEmailAliasInput(
                    emailAliasId = provisionedAlias.id,
                    metadata = null,
                    expiresAt = null,
                )
            val result = emailClient.updateEmailAlias(updateInput)

            with(result) {
                id shouldBe provisionedAlias.id
                version shouldBe provisionedAlias.version
                updatedAt.time shouldBe provisionedAlias.updatedAt.time
                // Null inputs should preserve existing values
                metadata shouldBe provisionedAlias.metadata
                expiresAt shouldBe provisionedAlias.expiresAt
            }
        }

    @Test
    fun updateEmailAliasShouldThrowWhenAliasIsNotExternal() =
        runTest {
            Assume.assumeTrue(
                "Test skipped due to external masks not being enabled.",
                emailClient.getConfigurationData().externalEmailAliasesEnabled,
            )

            // Provision an internal alias (real address is a provisioned Sudo email address)
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisionedAlias)
            provisionedAlias.realAddressType shouldBe EmailAliasRealAddressType.INTERNAL

            val updateInput =
                UpdateEmailAliasInput(
                    emailAliasId = provisionedAlias.id,
                    realAddress = "new@example.com",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.InvalidArgumentException> {
                emailClient.updateEmailAlias(updateInput)
            }
        }

    @Test
    fun updateEmailAliasShouldThrowWhenNewAddressIsInvalid() =
        runTest {
            Assume.assumeTrue(
                "Test skipped due to external masks not being enabled.",
                emailClient.getConfigurationData().externalEmailAliasesEnabled,
            )

            // Provision an external alias (real address is an external email address)
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, "test@sudoplatform.com", ownershipProof)
            emailAliasList.add(provisionedAlias)
            provisionedAlias.realAddressType shouldBe EmailAliasRealAddressType.EXTERNAL

            val updateInput =
                UpdateEmailAliasInput(
                    emailAliasId = provisionedAlias.id,
                    realAddress = "not-a-valid-email",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.InvalidEmailAddressException> {
                emailClient.updateEmailAlias(updateInput)
            }
        }

    @Test
    fun updateEmailAliasShouldThrowWhenNewAddressIsInternal() =
        runTest {
            Assume.assumeTrue(
                "Test skipped due to external masks not being enabled.",
                emailClient.getConfigurationData().externalEmailAliasesEnabled,
            )

            // Provision an external alias, then attempt to set the real address to an internal (Sudo) address
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, "test@sudoplatform.com", ownershipProof)
            emailAliasList.add(provisionedAlias)
            provisionedAlias.realAddressType shouldBe EmailAliasRealAddressType.EXTERNAL

            val updateInput =
                UpdateEmailAliasInput(
                    emailAliasId = provisionedAlias.id,
                    realAddress = testEmailAddress.emailAddress,
                )

            shouldThrow<SudoEmailClient.EmailAliasException.InvalidArgumentException> {
                emailClient.updateEmailAlias(updateInput)
            }
        }

    @Test
    fun updateEmailAliasShouldUpdateRealAddressSuccessfully() =
        runTest {
            Assume.assumeTrue(
                "Test skipped due to external masks not being enabled.",
                emailClient.getConfigurationData().externalEmailAliasesEnabled,
            )

            val newAddress = "new@example.com"

            // Provision an external alias (real address is an external email address)
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisionedAlias = provisionEmailAlias(aliasAddress, "test@sudoplatform.com", ownershipProof)
            emailAliasList.add(provisionedAlias)
            provisionedAlias.realAddressType shouldBe EmailAliasRealAddressType.EXTERNAL

            val updateInput =
                UpdateEmailAliasInput(
                    emailAliasId = provisionedAlias.id,
                    realAddress = newAddress,
                )
            val result = emailClient.updateEmailAlias(updateInput)

            with(result) {
                id shouldBe provisionedAlias.id
                realAddress shouldBe newAddress
                version shouldBe provisionedAlias.version + 1
                updatedAt.time shouldBeGreaterThan provisionedAlias.updatedAt.time
                aliasAddress shouldBe provisionedAlias.aliasAddress
            }
        }
}
