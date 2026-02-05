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
import com.sudoplatform.sudoemail.types.ConfigurationData
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMask
import com.sudoplatform.sudoemail.types.EmailMaskRealAddressType
import com.sudoplatform.sudoemail.types.EmailMaskStatus
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
 * Test the operation of [SudoEmailClient.provisionEmailMask].
 */
@RunWith(AndroidJUnit4::class)
class ProvisionEmailMaskIntegrationTest : BaseIntegrationTest() {
    private val emailMaskList = mutableListOf<EmailMask>()
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

    // Shared test resources
    private lateinit var testSudo: Sudo
    private lateinit var ownershipProof: String
    private lateinit var testEmailAddress: EmailAddress
    private lateinit var maskDomains: List<String>
    private lateinit var config: ConfigurationData

    @Before
    fun setup() =
        runTest {
            sudoClient.reset()
            sudoClient.generateEncryptionKey()

            config = emailClient.getConfigurationData()
            // Check if email masks are enabled, skip all tests if not
            val enabled = config.emailMasksEnabled
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
    fun provisionEmailMaskShouldReturnEmailMask() =
        runTest {
            // Generate a mask address using shared test resources
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val result = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            with(result) {
                id shouldNotBe null
                owner shouldBe userClient.getSubject()
                owners.first().id shouldBe testSudo.id
                owners.first().issuer shouldBe "sudoplatform.sudoservice"
                this.maskAddress shouldBe maskAddress
                this.realAddress shouldBe testEmailAddress.emailAddress
                this.realAddressType shouldBe EmailMaskRealAddressType.INTERNAL
                this.status shouldBe EmailMaskStatus.ENABLED
                this.inboundReceived shouldBe 0
                this.inboundDelivered shouldBe 0
                this.outboundReceived shouldBe 0
                this.outboundDelivered shouldBe 0
                this.spamCount shouldBe 0
                this.virusCount shouldBe 0
                this.expiresAt shouldBe null
                version shouldBe 0
                createdAt.time shouldBeGreaterThan 0L
                updatedAt.time shouldBeGreaterThan 0L
                metadata shouldBe null
            }
            emailMaskList.add(result)
        }

    @Test
    fun provisionEmailMaskWithMetadataShouldReturnEmailMask() =
        runTest {
            // Generate a mask address using shared test resources
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val metadata =
                mapOf(
                    "purpose" to "testing",
                    "environment" to "integration",
                )

            val result = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof, metadata = metadata)
            with(result) {
                id shouldNotBe null
                owner shouldBe userClient.getSubject()
                owners.first().id shouldBe testSudo.id
                owners.first().issuer shouldBe "sudoplatform.sudoservice"
                this.maskAddress shouldBe maskAddress
                this.realAddress shouldBe testEmailAddress.emailAddress
                this.realAddressType shouldBe EmailMaskRealAddressType.INTERNAL
                this.status shouldBe EmailMaskStatus.ENABLED
                version shouldBe 0
                createdAt.time shouldBeGreaterThan 0L
                updatedAt.time shouldBeGreaterThan 0L
                this.metadata shouldBe metadata
            }
            emailMaskList.add(result)
        }

    @Test
    fun provisionEmailMaskWithExpirationShouldReturnEmailMask() =
        runTest {
            // Generate a mask address using shared test resources
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            // Set expiration to 30 days from now
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 30)
            val expiresAt = calendar.time

            val result = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof, expiresAt = expiresAt)
            with(result) {
                id shouldNotBe null
                owner shouldBe userClient.getSubject()
                owners.first().id shouldBe testSudo.id
                owners.first().issuer shouldBe "sudoplatform.sudoservice"
                this.maskAddress shouldBe maskAddress
                this.realAddress shouldBe testEmailAddress.emailAddress
                this.realAddressType shouldBe EmailMaskRealAddressType.INTERNAL
                this.status shouldBe EmailMaskStatus.ENABLED
                version shouldBe 0
                createdAt.time shouldBeGreaterThan 0L
                updatedAt.time shouldBeGreaterThan 0L
                this.expiresAt shouldNotBe null
                this.expiresAt!!.time shouldBeGreaterThan Date().time
                metadata shouldBe null
            }
            emailMaskList.add(result)
        }

    @Test
    fun provisionEmailMaskShouldHandleProvisioningWithExternalRealAddressAppropriately() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"
            val externalAddress = "example@sudoplatform.com"

            if (config.externalEmailMasksEnabled) {
                val result = provisionEmailMask(maskAddress, externalAddress, ownershipProof)
                with(result) {
                    id shouldNotBe null
                    owner shouldBe userClient.getSubject()
                    owners.first().id shouldBe testSudo.id
                    owners.first().issuer shouldBe "sudoplatform.sudoservice"
                    this.maskAddress shouldBe maskAddress
                    this.realAddress shouldBe externalAddress
                    this.realAddressType shouldBe EmailMaskRealAddressType.EXTERNAL
                    this.status shouldBe EmailMaskStatus.PENDING
                    version shouldBe 0
                    createdAt.time shouldBeGreaterThan 0L
                    updatedAt.time shouldBeGreaterThan 0L
                    metadata shouldBe null
                }
                emailMaskList.add(result)
            } else {
                shouldThrow<SudoEmailClient.EmailMaskException.InvalidArgumentException> {
                    provisionEmailMask(maskAddress, externalAddress, ownershipProof)
                }
            }
        }

    @Test
    fun provisionEmailMaskShouldThrowWithUnsupportedDomain() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@gmail.com"
            val realAddress = "test@example.com"

            shouldThrow<SudoEmailClient.EmailMaskException.InvalidEmailAddressException> {
                provisionEmailMask(maskAddress, realAddress, ownershipProof)
            }
        }

    @Test
    fun provisionEmailMaskShouldFailWithInvalidMaskAddress() =
        runTest {
            val invalidMaskAddress = "@" + maskDomains.first()
            val realAddress = "test@example.com"

            shouldThrow<SudoEmailClient.EmailMaskException.InvalidEmailAddressException> {
                provisionEmailMask(invalidMaskAddress, realAddress, ownershipProof)
            }
        }

    @Test
    fun provisionEmailMaskShouldFailWithInvalidRealAddress() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"
            val invalidRealAddress = "invalid-email"

            shouldThrow<SudoEmailClient.EmailMaskException.InvalidEmailAddressException> {
                provisionEmailMask(maskAddress, invalidRealAddress, ownershipProof)
            }
        }

    @Test
    fun provisionEmailMaskShouldFailWhenProvisioningSameMaskTwice() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            // Provision the email mask for the first time
            val result = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            result shouldNotBe null
            emailMaskList.add(result)

            // Attempt to provision the same mask address again - should fail
            shouldThrow<SudoEmailClient.EmailMaskException.EmailMaskAlreadyExistsException> {
                provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            }
        }

    @Test
    fun provisionEmailMaskShouldThrowWithExistingMaskAddressExceptionAfterDeprovisioning() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("mask")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            val result = provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            result shouldNotBe null

            val deprovisionedEmailMask =
                emailClient.deprovisionEmailMask(
                    com.sudoplatform.sudoemail.types.inputs
                        .DeprovisionEmailMaskInput(result.id),
                )
            deprovisionedEmailMask shouldNotBe null

            // Attempt to provision with an already deprovisioned mask address
            shouldThrow<SudoEmailClient.EmailMaskException.UnavailableEmailAddressException> {
                provisionEmailMask(maskAddress, testEmailAddress.emailAddress, ownershipProof)
            }
        }
}
