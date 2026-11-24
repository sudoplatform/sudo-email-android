/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.internal.data.emailAddress.GraphQLEmailAddressService
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.inputs.DeprovisionEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.ProvisionEmailAliasInput
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.After
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.UUID

/**
 * Test which key pair an email address or email alias is provisioned with, and when that key pair
 * is removed from the device, with and without [SudoEmailClient.Builder.setEnforceSingletonKeyPair]
 * and [SudoEmailClient.Builder.setAllowCallerManagedKeys], against a deployed email service.
 */
@RunWith(AndroidJUnit4::class)
class ProvisioningKeyIntegrationTest : BaseIntegrationTest() {
    /** Records the key pairs the SDK generates, so a test can find the one it made. */
    private class RecordingKeyManager(
        private val keyManager: KeyManagerInterface,
    ) : KeyManagerInterface by keyManager {
        val generatedKeyIds: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun generateKeyPair(
            name: String,
            isExportable: Boolean,
        ) {
            keyManager.generateKeyPair(name, isExportable)
            generatedKeyIds.add(name)
        }
    }

    private val keyManager =
        RecordingKeyManager(
            KeyManagerFactory(context).createAndroidKeyManager("eml-provisioning-key-test-${UUID.randomUUID()}"),
        )

    private val clients = mutableListOf<SudoEmailClient>()
    private val emailAddressIds = mutableListOf<String>()
    private val emailAliasIds = mutableListOf<String>()

    /** Reads the user's key registrations straight from the service. */
    private val keyRegistrations by lazy {
        GraphQLEmailAddressService(
            ApiClient(ApiClientManager.getClient(context, userClient, "emService"), logger),
            logger,
        )
    }

    /** A client over this test's key store, which every client the test builds shares. */
    private fun client(
        enforceSingletonKeyPair: Boolean,
        allowCallerManagedKeys: Boolean = true,
    ): SudoEmailClient =
        SudoEmailClient
            .builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setLogger(logger)
            .setKeyManager(keyManager)
            .setEnforceSingletonKeyPair(enforceSingletonKeyPair)
            .setAllowCallerManagedKeys(allowCallerManagedKeys)
            .build()
            .also { clients.add(it) }

    @After
    fun teardown() =
        runBlocking<Unit> {
            emailAliasIds.forEach { runCatching { emailClient.deprovisionEmailAlias(DeprovisionEmailAliasInput(it)) } }
            emailAddressIds.forEach { runCatching { emailClient.deprovisionEmailAddress(it) } }
            clients.forEach { runCatching { it.close() } }
            runCatching { keyManager.removeAllKeys() }
        }

    private fun hasKeyPair(keyId: String): Boolean = keyManager.getPrivateKeyData(keyId) != null

    private suspend fun provision(
        client: SudoEmailClient,
        keyId: String? = null,
    ): EmailAddress = provisionEmailAddress(client, sharedOwnershipProof(), keyId = keyId).also { emailAddressIds.add(it.id) }

    private suspend fun deprovision(
        client: SudoEmailClient,
        emailAddress: EmailAddress,
    ) {
        client.deprovisionEmailAddress(emailAddress.id)
        emailAddressIds.remove(emailAddress.id)
    }

    private suspend fun provisionRejectedAddress(client: SudoEmailClient) {
        shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
            provisionEmailAddress(client, sharedOwnershipProof(), address = "${generateSafeLocalPart()}@gmail.com")
        }
    }

    /**
     * The service removes a deprovisioned email address's key registrations after the
     * deprovision returns, so wait until [keyId] is registered [count] times.
     */
    private fun awaitRegistrations(
        keyId: String,
        count: Int,
    ) {
        Awaitility
            .await("key $keyId to be registered $count times")
            .atMost(Duration.ONE_MINUTE)
            .pollInterval(Duration.TWO_SECONDS)
            .until {
                runBlocking { keyRegistrations.listPublicKeyRegistrations().count { it.keyId == keyId } == count }
            }
    }

    @Test
    fun perAddressKeyShouldBeKeptOnDeprovisionByDefault() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = false)

            val emailAddress = provision(client)

            val keyId = keyManager.generatedKeyIds.single()
            deprovision(client, emailAddress)

            hasKeyPair(keyId) shouldBe true
        }

    @Test
    fun perAddressKeyShouldBeRemovedOnDeprovision() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = false, allowCallerManagedKeys = false)

            val emailAddress = provision(client)

            val keyId = keyManager.generatedKeyIds.single()
            hasKeyPair(keyId) shouldBe true

            deprovision(client, emailAddress)

            hasKeyPair(keyId) shouldBe false
        }

    @Test
    fun generatedKeyShouldBeRemovedWhenTheServiceRejectsProvisioning() =
        runBlocking {
            provisionRejectedAddress(client(enforceSingletonKeyPair = false))

            hasKeyPair(keyManager.generatedKeyIds.single()) shouldBe false
        }

    @Test
    fun suppliedKeyShouldBeKeptUntilTheLastEmailAddressUsingItIsDeprovisioned() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = false, allowCallerManagedKeys = false)
            val first = provision(client)
            val keyId = keyManager.generatedKeyIds.single()
            // A client that manages its own keys won't take a keyId, so share the key through a
            // default client over the same key store.
            val second = provision(client(enforceSingletonKeyPair = false), keyId = keyId)
            keyManager.generatedKeyIds.size shouldBe 1

            deprovision(client, first)

            hasKeyPair(keyId) shouldBe true

            awaitRegistrations(keyId, count = 1)
            deprovision(client, second)

            hasKeyPair(keyId) shouldBe false
        }

    @Test
    fun keySharedWithAnEmailAliasShouldBeKeptOnDeprovisioning() =
        runBlocking {
            Assume.assumeTrue("Email aliases are not enabled.", emailClient.getConfigurationData().emailAliasesEnabled)
            val client = client(enforceSingletonKeyPair = false, allowCallerManagedKeys = false)
            val emailAddress = provision(client)
            val keyId = keyManager.generatedKeyIds.single()
            val alias =
                client(enforceSingletonKeyPair = false).provisionEmailAlias(
                    ProvisionEmailAliasInput(
                        aliasAddress = "${generateSafeLocalPart("alias")}@${getAliasDomains(client).first()}",
                        realAddress = emailAddress.emailAddress,
                        ownershipProofToken = sharedOwnershipProof(),
                        keyId = keyId,
                    ),
                )
            emailAliasIds.add(alias.id)

            deprovision(client, emailAddress)

            hasKeyPair(keyId) shouldBe true

            client.deprovisionEmailAlias(DeprovisionEmailAliasInput(alias.id))
            emailAliasIds.remove(alias.id)

            hasKeyPair(keyId) shouldBe true
        }

    @Test
    fun suppliedKeyShouldBeRejectedWithoutCallerManagedKeys() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = false, allowCallerManagedKeys = false)
            val keyId = UUID.randomUUID().toString()

            shouldThrow<SudoEmailClient.EmailAddressException.InvalidArgumentException> {
                provisionEmailAddress(client, sharedOwnershipProof(), keyId = keyId)
            }
            shouldThrow<SudoEmailClient.EmailAliasException.InvalidArgumentException> {
                client.provisionEmailAlias(
                    ProvisionEmailAliasInput(
                        aliasAddress = "${generateSafeLocalPart("alias")}@example.com",
                        realAddress = "${generateSafeLocalPart()}@example.com",
                        ownershipProofToken = sharedOwnershipProof(),
                        keyId = keyId,
                    ),
                )
            }

            keyManager.generatedKeyIds shouldBe emptyList<String>()
        }

    @Test
    fun createSingletonKeysIfAbsentShouldCreateTheKeyOnceAndReportWhetherItDid() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = true)

            val first = client.createSingletonKeysIfAbsent()
            val second = client.createSingletonKeysIfAbsent()

            first.keyPair.created shouldBe true
            second.keyPair.created shouldBe false
            second.keyPair.keyId shouldBe first.keyPair.keyId
            keyManager.generatedKeyIds.toList() shouldBe listOf(first.keyPair.keyId)
        }

    @Test
    fun createSingletonKeysIfAbsentShouldBeRejectedWithoutTheSingletonKeyMode() =
        runBlocking {
            shouldThrow<SudoEmailClient.EmailCryptographicKeysException.InvalidConfigurationException> {
                client(enforceSingletonKeyPair = false).createSingletonKeysIfAbsent()
            }

            keyManager.generatedKeyIds shouldBe emptyList<String>()
        }

    @Test
    fun singletonKeyModeShouldNotProvisionOrCreateAKeyBeforeTheKeyIsCreated() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = true)

            shouldThrow<SudoEmailClient.EmailAddressException.SingletonKeyPairNotFoundException> {
                provisionEmailAddress(client, sharedOwnershipProof())
            }
            shouldThrow<SudoEmailClient.EmailAliasException.SingletonKeyPairNotFoundException> {
                client.provisionEmailAlias(
                    ProvisionEmailAliasInput(
                        aliasAddress = "${generateSafeLocalPart("alias")}@example.com",
                        realAddress = "${generateSafeLocalPart()}@example.com",
                        ownershipProofToken = sharedOwnershipProof(),
                    ),
                )
            }

            keyManager.generatedKeyIds shouldBe emptyList<String>()
        }

    @Test
    fun singletonKeyShouldBeSharedByEveryEmailAddressAndSurviveDeprovision() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = true)
            val singletonKeyId = client.createSingletonKeysIfAbsent().keyPair.keyId

            val first = provision(client)
            val second = provision(client)

            keyManager.generatedKeyIds.toList() shouldBe listOf(singletonKeyId)

            // Deprovisioning through a client that doesn't enforce the singleton must still
            // leave the key the other email address is sealed to.
            deprovision(client(enforceSingletonKeyPair = false, allowCallerManagedKeys = false), first)
            deprovision(client, second)

            hasKeyPair(singletonKeyId) shouldBe true
        }

    @Test
    fun singletonKeyShouldSurviveARejectedProvision() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = true)
            val singletonKeyId = client.createSingletonKeysIfAbsent().keyPair.keyId

            provisionRejectedAddress(client)

            hasKeyPair(singletonKeyId) shouldBe true
            keyManager.generatedKeyIds.toList() shouldBe listOf(singletonKeyId)
        }

    @Test
    fun suppliedKeyShouldBeUsedEvenWithTheSingletonEnforced() =
        runBlocking {
            val singletonClient = client(enforceSingletonKeyPair = true)
            val singletonKeyId = singletonClient.createSingletonKeysIfAbsent().keyPair.keyId
            provision(client(enforceSingletonKeyPair = false))
            val suppliedKeyId = keyManager.generatedKeyIds.last()

            provision(singletonClient, keyId = suppliedKeyId)

            keyManager.generatedKeyIds.toList() shouldBe listOf(singletonKeyId, suppliedKeyId)
            awaitRegistrations(suppliedKeyId, count = 2)
        }

    @Test
    fun singletonKeyShouldBeCarriedByExportAndImport() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = true)
            val singletonKeyId = client.createSingletonKeysIfAbsent().keyPair.keyId
            val archive = client.exportKeys()

            client.reset()
            hasKeyPair(singletonKeyId) shouldBe false
            client.importKeys(archive)

            with(client.createSingletonKeysIfAbsent().keyPair) {
                created shouldBe false
                keyId shouldBe singletonKeyId
            }
        }
}
