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
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailMaskInput
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
import java.util.Calendar
import java.util.Date

/**
 * Test the operation of [SudoEmailClient.updateEmailMask].
 */
@RunWith(AndroidJUnit4::class)
class UpdateEmailMaskIntegrationTest : BaseIntegrationTest() {
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
    fun updateEmailMaskShouldUpdateMetadataSuccessfully() =
        runTest {
            // Generate a mask address and provision it with initial metadata using shared test resources
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val initialMetadata =
                mapOf(
                    "purpose" to "initial",
                    "environment" to "test",
                )

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof, metadata = initialMetadata)
            emailMaskList.add(provisionedMask)

            // Update the metadata
            val updatedMetadata =
                mapOf(
                    "purpose" to "updated",
                    "environment" to "integration",
                    "version" to "2.0",
                )

            val updateInput =
                UpdateEmailMaskInput(
                    emailMaskId = provisionedMask.id,
                    metadata = updatedMetadata,
                )
            val result = emailClient.updateEmailMask(updateInput)

            with(result) {
                id shouldBe provisionedMask.id
                owner shouldBe provisionedMask.owner
                owners shouldBe provisionedMask.owners
                identityId shouldBe provisionedMask.identityId
                maskAddress shouldBe provisionedMask.maskAddress
                realAddress shouldBe provisionedMask.realAddress
                realAddressType shouldBe provisionedMask.realAddressType
                status shouldBe provisionedMask.status
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
                metadata shouldBe updatedMetadata
            }
        }

    @Test
    fun updateEmailMaskShouldUpdateExpirationSuccessfully() =
        runTest {
            // Generate a mask address and provision it without expiration using shared test resources
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            // Verify no initial expiration
            provisionedMask.expiresAt shouldBe null

            // Update with an expiration date
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 30)
            val newExpirationDate = calendar.time

            val updateInput =
                UpdateEmailMaskInput(
                    emailMaskId = provisionedMask.id,
                    expiresAt = newExpirationDate,
                )
            val result = emailClient.updateEmailMask(updateInput)

            with(result) {
                id shouldBe provisionedMask.id
                owner shouldBe provisionedMask.owner
                owners shouldBe provisionedMask.owners
                identityId shouldBe provisionedMask.identityId
                maskAddress shouldBe provisionedMask.maskAddress
                realAddress shouldBe provisionedMask.realAddress
                realAddressType shouldBe provisionedMask.realAddressType
                status shouldBe provisionedMask.status
                createdAt shouldBe provisionedMask.createdAt
                updatedAt.time shouldBeGreaterThan provisionedMask.updatedAt.time
                version shouldBe provisionedMask.version + 1
                expiresAt shouldNotBe null
                expiresAt!!.time / 1000 shouldBe newExpirationDate.time / 1000
                metadata shouldBe provisionedMask.metadata
            }
        }

    @Test
    fun updateEmailMaskShouldUpdateBothMetadataAndExpiration() =
        runTest {
            // Generate a mask address and provision it with initial metadata using shared test resources
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val initialMetadata = mapOf("initial" to "data")

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof, metadata = initialMetadata)
            emailMaskList.add(provisionedMask)

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
                UpdateEmailMaskInput(
                    emailMaskId = provisionedMask.id,
                    metadata = updatedMetadata,
                    expiresAt = newExpirationDate,
                )
            val result = emailClient.updateEmailMask(updateInput)

            with(result) {
                id shouldBe provisionedMask.id
                version shouldBe provisionedMask.version + 1
                updatedAt.time shouldBeGreaterThan provisionedMask.updatedAt.time
                metadata shouldBe updatedMetadata
                expiresAt shouldNotBe null
                (expiresAt!!.time / 1000) shouldBe (newExpirationDate.time / 1000)
                // All other properties should remain unchanged
                owner shouldBe provisionedMask.owner
                maskAddress shouldBe provisionedMask.maskAddress
                realAddress shouldBe provisionedMask.realAddress
                status shouldBe provisionedMask.status
                createdAt shouldBe provisionedMask.createdAt
            }
        }

    @Test
    fun updateEmailMaskShouldRemoveMetadataWithEmptyMap() =
        runTest {
            // Generate a mask address and provision it with metadata using shared test resources
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val initialMetadata =
                mapOf(
                    "key1" to "value1",
                    "key2" to "value2",
                    "key3" to "value3",
                )

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof, metadata = initialMetadata)
            emailMaskList.add(provisionedMask)

            // Verify initial metadata exists
            provisionedMask.metadata shouldBe initialMetadata

            // Update with empty metadata map to remove all metadata
            val updateInput =
                UpdateEmailMaskInput(
                    emailMaskId = provisionedMask.id,
                    metadata = emptyMap(),
                )
            val result = emailClient.updateEmailMask(updateInput)

            with(result) {
                id shouldBe provisionedMask.id
                version shouldBe provisionedMask.version + 1
                updatedAt.time shouldBeGreaterThan provisionedMask.updatedAt.time
                metadata shouldBe null
                // All other properties should remain unchanged
                owner shouldBe provisionedMask.owner
                maskAddress shouldBe provisionedMask.maskAddress
                realAddress shouldBe provisionedMask.realAddress
                status shouldBe provisionedMask.status
                expiresAt shouldBe provisionedMask.expiresAt
                createdAt shouldBe provisionedMask.createdAt
            }
        }

    @Test
    fun updateEmailMaskShouldRemoveExpirationWithZeroDate() =
        runTest {
            // Generate a mask address and provision it with an expiration date using shared test resources
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 15)
            val initialExpirationDate = calendar.time

            val provisionedMask =
                provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof, expiresAt = initialExpirationDate)
            emailMaskList.add(provisionedMask)

            // Verify initial expiration exists
            provisionedMask.expiresAt shouldNotBe null

            // Update with zero date to remove expiration
            val zeroDate = Date(0)
            val updateInput =
                UpdateEmailMaskInput(
                    emailMaskId = provisionedMask.id,
                    expiresAt = zeroDate,
                )
            val result = emailClient.updateEmailMask(updateInput)

            with(result) {
                id shouldBe provisionedMask.id
                version shouldBe provisionedMask.version + 1
                updatedAt.time shouldBeGreaterThan provisionedMask.updatedAt.time
                expiresAt shouldBe null
                // All other properties should remain unchanged
                owner shouldBe provisionedMask.owner
                maskAddress shouldBe provisionedMask.maskAddress
                realAddress shouldBe provisionedMask.realAddress
                status shouldBe provisionedMask.status
                metadata shouldBe provisionedMask.metadata
                createdAt shouldBe provisionedMask.createdAt
            }
        }

    @Test
    fun updateEmailMaskShouldFailWithNonExistentMaskId() =
        runTest {
            val nonExistentId = "non-existent-mask-id"
            val updateInput =
                UpdateEmailMaskInput(
                    emailMaskId = nonExistentId,
                    metadata = mapOf("test" to "data"),
                )

            shouldThrow<SudoEmailClient.EmailMaskException.EmailMaskNotFoundException> {
                emailClient.updateEmailMask(updateInput)
            }
        }

    @Test
    fun updateEmailMaskShouldWorkWithDisabledMask() =
        runTest {
            // Generate a mask address and provision it using shared test resources
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            // Disable the mask first
            val disabledMask =
                emailClient.disableEmailMask(
                    DisableEmailMaskInput(provisionedMask.id),
                )
            disabledMask.status shouldBe EmailMaskStatus.DISABLED

            // Update the disabled mask
            val updatedMetadata = mapOf("status" to "disabled-but-updated")
            val updateInput =
                UpdateEmailMaskInput(
                    emailMaskId = provisionedMask.id,
                    metadata = updatedMetadata,
                )
            val result = emailClient.updateEmailMask(updateInput)

            with(result) {
                id shouldBe provisionedMask.id
                status shouldBe EmailMaskStatus.DISABLED // Status should remain disabled
                version shouldBe disabledMask.version + 1
                updatedAt.time shouldBeGreaterThan disabledMask.updatedAt.time
                metadata shouldBe updatedMetadata
            }
        }

    @Test
    fun updateEmailMaskShouldHandleMultipleUpdates() =
        runTest {
            // Generate a mask address and provision it using shared test resources
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val provisionedMask = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            emailMaskList.add(provisionedMask)

            val originalVersion = provisionedMask.version

            // First update - add metadata
            val firstUpdateInput =
                UpdateEmailMaskInput(
                    emailMaskId = provisionedMask.id,
                    metadata = mapOf("update" to "first"),
                )
            val firstResult = emailClient.updateEmailMask(firstUpdateInput)
            firstResult.version shouldBe originalVersion + 1
            firstResult.metadata shouldBe mapOf("update" to "first")

            // Second update - update metadata
            val secondUpdateInput =
                UpdateEmailMaskInput(
                    emailMaskId = provisionedMask.id,
                    metadata = mapOf("update" to "second", "additional" to "data"),
                )
            val secondResult = emailClient.updateEmailMask(secondUpdateInput)
            secondResult.version shouldBe originalVersion + 2
            secondResult.metadata shouldBe mapOf("update" to "second", "additional" to "data")

            // Third update - add expiration
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 45)
            val expirationDate = calendar.time

            val thirdUpdateInput =
                UpdateEmailMaskInput(
                    emailMaskId = provisionedMask.id,
                    expiresAt = expirationDate,
                )
            val thirdResult = emailClient.updateEmailMask(thirdUpdateInput)
            thirdResult.version shouldBe originalVersion + 3
            thirdResult.expiresAt shouldNotBe null
            thirdResult.expiresAt!!.time / 1000 shouldBe expirationDate.time / 1000
            thirdResult.metadata shouldBe mapOf("update" to "second", "additional" to "data") // Metadata should be preserved

            // Verify all core properties remain consistent throughout updates
            with(thirdResult) {
                id shouldBe provisionedMask.id
                owner shouldBe provisionedMask.owner
                owners shouldBe provisionedMask.owners
                identityId shouldBe provisionedMask.identityId
                maskAddress shouldBe provisionedMask.maskAddress
                realAddress shouldBe provisionedMask.realAddress
                realAddressType shouldBe provisionedMask.realAddressType
                status shouldBe provisionedMask.status
                createdAt shouldBe provisionedMask.createdAt
            }
        }

    @Test
    fun updateEmailMaskShouldWorkWithNullInputs() =
        runTest {
            // Generate a mask address and provision it with initial data using shared test resources
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 10)
            val initialExpiration = calendar.time

            val initialMetadata = mapOf("initial" to "data")

            val provisionedMask =
                provisionEmailMask(
                    maskAddress,
                    testEmailAddress.emailAddress,
                    ownershipProof,
                    metadata = initialMetadata,
                    expiresAt = initialExpiration,
                )
            emailMaskList.add(provisionedMask)

            // Update with null values (should not change existing values)
            val updateInput =
                UpdateEmailMaskInput(
                    emailMaskId = provisionedMask.id,
                    metadata = null,
                    expiresAt = null,
                )
            val result = emailClient.updateEmailMask(updateInput)

            with(result) {
                id shouldBe provisionedMask.id
                version shouldBe provisionedMask.version
                updatedAt.time shouldBe provisionedMask.updatedAt.time
                // Null inputs should preserve existing values
                metadata shouldBe provisionedMask.metadata
                expiresAt shouldBe provisionedMask.expiresAt
            }
        }
}
