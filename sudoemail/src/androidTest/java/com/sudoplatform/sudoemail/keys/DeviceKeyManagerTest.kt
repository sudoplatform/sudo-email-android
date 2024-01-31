/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
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
import kotlinx.coroutines.runBlocking
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
class DeviceKeyManagerTest {

    private val keyRingServiceName = "sudo-email"

    val context: Context = ApplicationProvider.getApplicationContext<Context>()

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

    private val deviceKeyManager by lazy {
        DefaultDeviceKeyManager(
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
    fun fini() = runBlocking {
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
            deviceKeyManager.getKeyRingId()
        }
    }

    @Test
    fun shouldBeAbleToPerformOperationsAfterSignIn() = runBlocking {
        registerSignInAndEntitle()

        deviceKeyManager.getKeyPairWithId("bogusValue") shouldBe null

        val keyPair = deviceKeyManager.generateKeyPair()
        with(keyPair) {
            this shouldNotBe null
            keyRingId shouldStartWith keyRingServiceName
            keyId.isBlank() shouldBe false
            publicKey shouldNotBe null
            publicKey?.size?.shouldBeGreaterThan(0)
            privateKey shouldNotBe null
            privateKey?.size?. shouldBeGreaterThan(0)
        }

        val newKeyPair = deviceKeyManager.generateKeyPair()
        newKeyPair shouldNotBe null
        newKeyPair shouldNotBe keyPair

        val fetchedKeyPair = deviceKeyManager.getKeyPairWithId(newKeyPair!!.keyId)
        fetchedKeyPair shouldNotBe null
        fetchedKeyPair shouldNotBe keyPair
        fetchedKeyPair shouldBe newKeyPair

        deviceKeyManager.getKeyRingId() shouldStartWith keyRingServiceName

        val clearData = "hello world".toByteArray()
        var secretData = keyManager.encryptWithPublicKey(
            newKeyPair.keyId,
            clearData,
            KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
        )
        var decryptedData = deviceKeyManager.decryptWithPrivateKey(
            secretData,
            newKeyPair.keyId,
            KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
        )
        decryptedData shouldBe clearData

        keyManager.deleteSymmetricKey("symmetricKey")
        keyManager.generateSymmetricKey("symmetricKey")
        val symmetricKey = keyManager.getSymmetricKeyData("symmetricKey")
        val symmetricSecretData = keyManager.encryptWithSymmetricKey("symmetricKey", clearData)

        decryptedData = deviceKeyManager.decryptWithSymmetricKey(symmetricKey!!, symmetricSecretData)
        decryptedData shouldBe clearData

        // exportKeys and importKeys
        val exportedKeys = deviceKeyManager.exportKeys()
        exportedKeys shouldNotBe null
        deviceKeyManager.removeAllKeys()
        shouldThrow<com.sudoplatform.sudokeymanager.KeyNotFoundException> {
            keyManager.encryptWithPublicKey(
                newKeyPair.keyId,
                clearData,
                KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
            )
        }
        deviceKeyManager.importKeys(exportedKeys)
        decryptedData = deviceKeyManager.decryptWithPrivateKey(
            secretData,
            newKeyPair.keyId,
            KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
        )
        decryptedData shouldBe clearData
    }
}
