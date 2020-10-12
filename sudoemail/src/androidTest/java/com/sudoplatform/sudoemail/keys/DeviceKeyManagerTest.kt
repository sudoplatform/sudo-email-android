/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/**
 * Test the operation of [DefaultDeviceKeyManager] on Android.
 *
 * @since 2020-08-05
 */
@RunWith(AndroidJUnit4::class)
class DeviceKeyManagerTest : BaseIntegrationTest() {

    private val keyRingServiceName = "sudo-email"

    private val deviceKeyManager by lazy {
        DefaultDeviceKeyManager(
            context = context,
            userClient = userClient,
            keyRingServiceName = keyRingServiceName,
            keyManager = keyManager
        )
    }

    private val keyPairsToDelete = mutableListOf<KeyPair>()
    private var savedCurrentKeyId: ByteArray? = null

    @Before
    fun init() {
        Timber.plant(Timber.DebugTree())
        savedCurrentKeyId = keyManager.getPassword(DefaultDeviceKeyManager.CURRENT_KEY_ID_NAME)
        keyManager.deletePassword(DefaultDeviceKeyManager.CURRENT_KEY_ID_NAME)
    }

    @After
    fun fini() = runBlocking {
        keyPairsToDelete.forEach {
            try {
                keyManager.deleteKeyPair(it.keyId)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
        savedCurrentKeyId?.let {
            keyManager.deletePassword(DefaultDeviceKeyManager.CURRENT_KEY_ID_NAME)
            keyManager.addPassword(savedCurrentKeyId, DefaultDeviceKeyManager.CURRENT_KEY_ID_NAME)
            savedCurrentKeyId = null
        }
        deleteAllSudos()
        Timber.uprootAll()
    }

    @Test
    fun shouldBeAbleToPerformOperationsAfterSignIn() = runBlocking {

        signInAndRegister()

        deviceKeyManager.getCurrentKeyPair() shouldBe null
        deviceKeyManager.getKeyPairWithId("bogusValue") shouldBe null

        val keyPair = deviceKeyManager.generateNewCurrentKeyPair()
        with(keyPair) {
            this shouldNotBe null
            keyPairsToDelete.add(keyPair)
            keyRingId shouldStartWith keyRingServiceName
            keyId.isBlank() shouldBe false
            publicKey shouldNotBe null
            publicKey.size shouldBeGreaterThan 0
            privateKey shouldNotBe null
            privateKey.size shouldBeGreaterThan 0
        }

        val currentKeyPair = deviceKeyManager.getCurrentKeyPair()
        currentKeyPair shouldNotBe null
        currentKeyPair shouldBe keyPair

        val fetchedKeyPair = deviceKeyManager.getKeyPairWithId(currentKeyPair!!.keyId)
        fetchedKeyPair shouldNotBe null
        fetchedKeyPair shouldBe keyPair
        fetchedKeyPair shouldBe currentKeyPair

        deviceKeyManager.getKeyRingId() shouldStartWith keyRingServiceName

        val clearData = "hello world".toByteArray()
        var secretData = keyManager.encryptWithPublicKey(
            currentKeyPair.keyId,
            clearData,
            KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1
        )
        var decryptedData = deviceKeyManager.decryptWithPrivateKey(
            secretData,
            currentKeyPair.keyId,
            KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1
        )
        decryptedData shouldBe clearData

        keyManager.deleteSymmetricKey("symmetricKey")
        keyManager.generateSymmetricKey("symmetricKey")
        val symmetricKey = keyManager.getSymmetricKeyData("symmetricKey")
        secretData = keyManager.encryptWithSymmetricKey("symmetricKey", clearData)

        decryptedData = deviceKeyManager.decryptWithSymmetricKey(symmetricKey, secretData)
        decryptedData shouldBe clearData
    }
}
