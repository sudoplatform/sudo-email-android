/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
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
import java.util.UUID

/**
 * Test the operation of [SudoEmailClient.getEmailAliasById].
 */
@RunWith(AndroidJUnit4::class)
class GetEmailAliasByIdIntegrationTest : BaseIntegrationTest() {
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
            // Check if email aliases are enabled, skip all tests if not
            val enabled = config.emailAliasesEnabled
            Assume.assumeTrue("Test suite skipped due to aliases not being enabled.", enabled)

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
    fun getEmailAliasByIdShouldReturnProvisionedEmailAlias() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val provisioned = provisionEmailAlias(aliasAddress, testEmailAddress.emailAddress, ownershipProof)
            emailAliasList.add(provisioned)

            val result = emailClient.getEmailAliasById(provisioned.id)

            with(result) {
                id shouldBe provisioned.id
                owner shouldBe userClient.getSubject()
                owners.first().id shouldBe testSudo.id
                owners.first().issuer shouldBe "sudoplatform.sudoservice"
                this.aliasAddress shouldBe aliasAddress
                this.realAddress shouldBe testEmailAddress.emailAddress
                this.realAddressType shouldBe EmailAliasRealAddressType.INTERNAL
                this.status shouldBe EmailAliasStatus.ENABLED
                createdAt.time shouldBeGreaterThan 0L
                updatedAt.time shouldBeGreaterThan 0L
            }
        }

    @Test
    fun getEmailAliasByIdShouldReturnAliasWithMetadata() =
        runTest {
            val aliasLocalPart = generateSafeLocalPart("alias")
            val aliasAddress = "$aliasLocalPart@${aliasDomains.first()}"

            val metadata =
                mapOf(
                    "purpose" to "testing",
                    "environment" to "integration",
                )

            val provisioned =
                provisionEmailAlias(
                    aliasAddress,
                    testEmailAddress.emailAddress,
                    ownershipProof,
                    metadata = metadata,
                )
            emailAliasList.add(provisioned)

            val result = emailClient.getEmailAliasById(provisioned.id)

            with(result) {
                id shouldBe provisioned.id
                this.aliasAddress shouldBe aliasAddress
                this.metadata shouldBe metadata
            }
        }

    @Test
    fun getEmailAliasByIdShouldThrowWhenAliasNotFound() =
        runTest {
            val nonExistentId = UUID.randomUUID().toString()

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasNotFoundException> {
                emailClient.getEmailAliasById(nonExistentId)
            }
        }
}
