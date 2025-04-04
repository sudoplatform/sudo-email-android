/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoentitlements.SudoEntitlementsClient
import com.sudoplatform.sudoentitlementsadmin.SudoEntitlementsAdminClient
import com.sudoplatform.sudoentitlementsadmin.types.Entitlement
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.TESTAuthenticationProvider
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.util.logging.Logger

/**
 * Test the operation of [DefaultDeviceKeyManager] on Android.
 */
@RunWith(AndroidJUnit4::class)
class ServiceKeyManagerTest {

    private val keyRingServiceName = "sudo-email"

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val userClient by lazy {
        SudoUserClient.builder(BaseIntegrationTest.context)
            .setNamespace("eml-client-test")
            .build()
    }

    private val sudoClient by lazy {
        val containerURI = Uri.fromFile(BaseIntegrationTest.context.cacheDir)
        SudoProfilesClient.builder(BaseIntegrationTest.context, userClient, containerURI)
            .build()
    }

    private val entitlementsClient by lazy {
        SudoEntitlementsClient.builder()
            .setContext(BaseIntegrationTest.context)
            .setSudoUserClient(userClient)
            .build()
    }

    private val entitlementsAdminClient by lazy {
        val adminApiKey = readArgument("ADMIN_API_KEY", "api.key")
        SudoEntitlementsAdminClient.builder(BaseIntegrationTest.context, adminApiKey).build()
    }

    private val keyManager by lazy {
        KeyManagerFactory(BaseIntegrationTest.context).createAndroidKeyManager("eml-client-test")
    }

    private val serviceKeyManager by lazy {
        DefaultServiceKeyManager(
            userClient = userClient,
            keyRingServiceName = keyRingServiceName,
            keyManager = keyManager,
        )
    }

    @Before
    fun init() {
        Timber.plant(Timber.DebugTree())

        Logger.getLogger("com.amazonaws").level = java.util.logging.Level.FINEST
        Logger.getLogger("org.apache.http").level = java.util.logging.Level.FINEST

        keyManager.removeAllKeys()
    }

    @After
    fun fini() = runTest {
        if (userClient.isRegistered()) {
            userClient.deregister()
        }
        userClient.reset()
        sudoClient.reset()

        Timber.uprootAll()
    }

    private fun readTextFile(fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use {
            it.readText().trim()
        }
    }

    private fun readArgument(argumentName: String, fallbackFileName: String?): String {
        val argumentValue = InstrumentationRegistry.getArguments().getString(argumentName)?.trim()
        if (argumentValue != null) {
            return argumentValue
        }
        if (fallbackFileName != null) {
            return readTextFile(fallbackFileName)
        }
        throw IllegalArgumentException("$argumentName property not found")
    }

    private suspend fun register() {
        userClient.isRegistered() shouldBe false

        val privateKey = readArgument("REGISTER_KEY", "register_key.private")
        val keyId = readArgument("REGISTER_KEY_ID", "register_key.id")

        val authProvider = TESTAuthenticationProvider(
            name = "eml-client-test",
            privateKey = privateKey,
            publicKey = null,
            keyManager = keyManager,
            keyId = keyId,
        )

        userClient.registerWithAuthenticationProvider(authProvider, "eml-client-test")
    }

    private suspend fun registerAndSignIn() {
        userClient.isRegistered() shouldBe false
        register()
        userClient.isRegistered() shouldBe true
        userClient.signInWithKey()
        userClient.isSignedIn() shouldBe true
    }

    private suspend fun registerSignInAndEntitle() {
        registerAndSignIn()
        val externalId = entitlementsClient.getExternalId()
        val entitlements = listOf(
            Entitlement("sudoplatform.sudo.max", "test", 3),
            Entitlement("sudoplatform.email.emailAddressUserEntitled", "test", 1),
            Entitlement("sudoplatform.email.emailStorageMaxPerUser", "test", 500000),
            Entitlement("sudoplatform.email.emailAddressMaxPerSudo", "test", 3),
            Entitlement("sudoplatform.email.emailStorageMaxPerEmailAddress", "test", 500000),
            Entitlement("sudoplatform.email.emailMessageSendUserEntitled", "test", 1),
            Entitlement("sudoplatform.email.emailMessageReceiveUserEntitled", "test", 1),
        )
        entitlementsAdminClient.applyEntitlementsToUser(externalId, entitlements)
        entitlementsClient.redeemEntitlements()
    }

    @Test
    fun shouldThrowIfNotRegistered() {
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException> {
            serviceKeyManager.getKeyRingId()
        }
    }

    @Test
    fun shouldBeAbleToPerformOperationsAfterSignIn() = runTest {
        registerSignInAndEntitle()

        serviceKeyManager.getKeyPairWithId("bogusValue") shouldBe null

        val keyPair = serviceKeyManager.generateKeyPair()
        with(keyPair) {
            this shouldNotBe null
            keyRingId shouldStartWith keyRingServiceName
            keyId.isBlank() shouldBe false
            publicKey shouldNotBe null
            publicKey.size.shouldBeGreaterThan(0)
            privateKey shouldNotBe null
            privateKey.size.shouldBeGreaterThan(0)
        }

        val newKeyPair = serviceKeyManager.generateKeyPair()
        newKeyPair shouldNotBe null
        newKeyPair shouldNotBe keyPair

        val fetchedKeyPair = serviceKeyManager.getKeyPairWithId(newKeyPair.keyId)
        fetchedKeyPair shouldNotBe null
        fetchedKeyPair shouldNotBe keyPair
        fetchedKeyPair shouldBe newKeyPair

        serviceKeyManager.getKeyRingId() shouldStartWith keyRingServiceName

        val privateKeyExists = serviceKeyManager.privateKeyExists(newKeyPair.keyId)
        privateKeyExists shouldBe true

        val clearData = "hello world".toByteArray()
        val secretData = serviceKeyManager.encryptWithKeyPairId(
            newKeyPair.keyId,
            clearData,
            KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
        )
        var decryptedData = serviceKeyManager.decryptWithKeyPairId(
            secretData,
            newKeyPair.keyId,
            KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
        )
        decryptedData shouldBe clearData

        val clearData2 = "hello world2".toByteArray()
        val secretData2 = serviceKeyManager.encryptWithPublicKey(
            newKeyPair.publicKey,
            clearData2,
            KeyManagerInterface.PublicKeyFormat.RSA_PUBLIC_KEY,
            KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
        )
        val decryptedData2 = serviceKeyManager.decryptWithKeyPairId(
            secretData2,
            newKeyPair.keyId,
            KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
        )
        decryptedData2 shouldBe clearData2

        keyManager.deleteSymmetricKey("symmetricKey")
        keyManager.generateSymmetricKey("symmetricKey")
        val symmetricKey = serviceKeyManager.getSymmetricKeyData("symmetricKey")
        val symmetricSecretData = serviceKeyManager.encryptWithSymmetricKeyId("symmetricKey", clearData)

        decryptedData = serviceKeyManager.decryptWithSymmetricKey(symmetricKey!!, symmetricSecretData)
        decryptedData shouldBe clearData

        // Encrypt/decrypt with IV
        val randomData = serviceKeyManager.createRandomData(16)
        randomData shouldNotBe null

        val symmetricKeyExists = serviceKeyManager.symmetricKeyExists("symmetricKey")
        symmetricKeyExists shouldBe true

        val clearData3 = "hello world3".toByteArray()
        val secretData3 = serviceKeyManager.encryptWithSymmetricKeyId("symmetricKey", clearData3, randomData)
        val decryptedData3 = serviceKeyManager.decryptWithSymmetricKey(symmetricKey, secretData3, randomData)
        decryptedData3 shouldBe clearData3

        // exportKeys and importKeys
        val exportedKeys = serviceKeyManager.exportKeys()
        exportedKeys shouldNotBe null
        serviceKeyManager.removeAllKeys()
        shouldThrow<com.sudoplatform.sudokeymanager.KeyNotFoundException> {
            keyManager.encryptWithPublicKey(
                newKeyPair.keyId,
                clearData,
                KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
            )
        }
        serviceKeyManager.importKeys(exportedKeys)
        decryptedData = serviceKeyManager.decryptWithKeyPairId(
            secretData,
            newKeyPair.keyId,
            KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
        )
        decryptedData shouldBe clearData
    }
}
