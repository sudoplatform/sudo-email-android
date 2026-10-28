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
import com.sudoplatform.sudoemail.types.EmailAlias
import com.sudoplatform.sudoemail.types.EmailAliasRealAddressType
import com.sudoplatform.sudoemail.types.EmailAliasStatus
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
 * Test the operation of [SudoEmailClient.provisionEmailAlias].
 */
@RunWith(AndroidJUnit4::class)
class ProvisionEmailAliasIntegrationTest : BaseIntegrationTest() {
    private val emailAliasList = mutableListOf<EmailAlias>()
    private val emailAddressList = mutableListOf<EmailAddress>()

    // Shared test resources
    private lateinit var testSudo: Sudo
    private lateinit var ownershipProof: String
    private lateinit var testEmailAddress: EmailAddress
    private lateinit var aliasDomains: List<String>
    private lateinit var config: ConfigurationData

    @Before
    fun setup() =
        runTest {
            config = emailClient.getConfigurationData()
            // Check if email masks are enabled, skip all tests if not
            val enabled = config.emailAliasesEnabled
            Assume.assumeTrue("Test suite skipped due to masks not being enabled.", enabled)

            testSudo = sharedSudo()
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
    fun provisionEmailAliasShouldReturnEmailAlias() =
        runTest {
            // Generate an alias address using shared test resources
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val result = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            with(result) {
                id shouldNotBe null
                owner shouldBe userClient.getSubject()
                owners.first().id shouldBe testSudo.id
                owners.first().issuer shouldBe "sudoplatform.sudoservice"
                this.aliasAddress shouldBe aliasAddress
                this.realAddress shouldBe testEmailAddress.emailAddress
                this.realAddressType shouldBe EmailAliasRealAddressType.INTERNAL
                this.status shouldBe EmailAliasStatus.ENABLED
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
            emailAliasList.add(result)
        }

    @Test
    fun provisionEmailAliasWithMetadataShouldReturnEmailAlias() =
        runTest {
            // Generate an alias address using shared test resources
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val metadata =
                mapOf(
                    "purpose" to "testing",
                    "environment" to "integration",
                )

            val result =
                provisionEmailAlias(
                    aliasAddress,
                    testEmailAddress.emailAddress,
                    ownershipProof,
                    metadata = metadata,
                )
            with(result) {
                id shouldNotBe null
                owner shouldBe userClient.getSubject()
                owners.first().id shouldBe testSudo.id
                owners.first().issuer shouldBe "sudoplatform.sudoservice"
                this.aliasAddress shouldBe aliasAddress
                this.realAddress shouldBe testEmailAddress.emailAddress
                this.realAddressType shouldBe EmailAliasRealAddressType.INTERNAL
                this.status shouldBe EmailAliasStatus.ENABLED
                version shouldBe 0
                createdAt.time shouldBeGreaterThan 0L
                updatedAt.time shouldBeGreaterThan 0L
                this.metadata shouldBe metadata
            }
            emailAliasList.add(result)
        }

    @Test
    fun provisionEmailAliasWithExpirationShouldReturnEmailAlias() =
        runTest {
            // Generate an alias address using shared test resources
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            // Set expiration to 30 days from now
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 30)
            val expiresAt = calendar.time

            val result =
                provisionEmailAlias(
                    aliasAddress,
                    testEmailAddress.emailAddress,
                    ownershipProof,
                    expiresAt = expiresAt,
                )
            with(result) {
                id shouldNotBe null
                owner shouldBe userClient.getSubject()
                owners.first().id shouldBe testSudo.id
                owners.first().issuer shouldBe "sudoplatform.sudoservice"
                this.aliasAddress shouldBe aliasAddress
                this.realAddress shouldBe testEmailAddress.emailAddress
                this.realAddressType shouldBe EmailAliasRealAddressType.INTERNAL
                this.status shouldBe EmailAliasStatus.ENABLED
                version shouldBe 0
                createdAt.time shouldBeGreaterThan 0L
                updatedAt.time shouldBeGreaterThan 0L
                this.expiresAt shouldNotBe null
                this.expiresAt!!.time shouldBeGreaterThan Date().time
                metadata shouldBe null
            }
            emailAliasList.add(result)
        }

    @Test
    fun provisionEmailAliasShouldHandleProvisioningWithExternalRealAddressAppropriately() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"
            val externalAddress = "example@sudoplatform.com"

            if (config.externalEmailAliasesEnabled) {
                val result = provisionEmailAlias(aliasAddress, externalAddress, ownershipProof)
                with(result) {
                    id shouldNotBe null
                    owner shouldBe userClient.getSubject()
                    owners.first().id shouldBe testSudo.id
                    owners.first().issuer shouldBe "sudoplatform.sudoservice"
                    this.aliasAddress shouldBe aliasAddress
                    this.realAddress shouldBe externalAddress
                    this.realAddressType shouldBe EmailAliasRealAddressType.EXTERNAL
                    this.status shouldBe EmailAliasStatus.PENDING
                    version shouldBe 0
                    createdAt.time shouldBeGreaterThan 0L
                    updatedAt.time shouldBeGreaterThan 0L
                    metadata shouldBe null
                }
                emailAliasList.add(result)
            } else {
                shouldThrow<SudoEmailClient.EmailAliasException.InvalidArgumentException> {
                    provisionEmailAlias(aliasAddress, externalAddress, ownershipProof)
                }
            }
        }

    @Test
    fun provisionEmailAliasShouldThrowWithUnsupportedDomain() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@gmail.com"

            shouldThrow<SudoEmailClient.EmailAliasException.InvalidEmailAddressException> {
                provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            }
        }

    @Test
    fun provisionEmailAliasShouldFailWithInvalidAliasAddress() =
        runTest {
            val invalidAliasAddress = "@" + aliasDomains.first()

            shouldThrow<SudoEmailClient.EmailAliasException.InvalidEmailAddressException> {
                provisionEmailAlias(invalidAliasAddress, testEmailAddress.emailAddress, ownershipProof)
            }
        }

    @Test
    fun provisionEmailAliasShouldFailWithInvalidRealAddress() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"
            val invalidRealAddress = "invalid-email"

            shouldThrow<SudoEmailClient.EmailAliasException.InvalidEmailAddressException> {
                provisionEmailAlias(aliasAddress, invalidRealAddress, ownershipProof)
            }
        }

    @Test
    fun provisionEmailAliasShouldFailWhenProvisioningSameAliasTwice() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            // Provision the email alias for the first time
            val result = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            result shouldNotBe null
            emailAliasList.add(result)

            // Attempt to provision the same alias address again - should fail
            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasAlreadyExistsException> {
                provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            }
        }

    @Test
    fun provisionEmailAliasShouldThrowWithExistingAliasAddressExceptionAfterDeprovisioning() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val result = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            result shouldNotBe null

            val deprovisionedEmailAlias =
                emailClient.deprovisionEmailAlias(
                    com.sudoplatform.sudoemail.types.inputs
                        .DeprovisionEmailAliasInput(result.id),
                )
            deprovisionedEmailAlias shouldNotBe null

            // Attempt to provision with an already deprovisioned alias address
            shouldThrow<SudoEmailClient.EmailAliasException.UnavailableEmailAddressException> {
                provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            }
        }
}
