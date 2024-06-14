/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudokeymanager.AndroidSQLiteStore
import com.sudoplatform.sudokeymanager.KeyManager
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

/**
 * Test the operation of [DefaultDeviceKeyManager] using mocks under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ServiceKeyManagerRoboTest : BaseTests() {

    private val keyRingServiceName = "sudo-email"

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "mockSubject"
        }
    }

    private val keyManager by before {
        KeyManager(AndroidSQLiteStore(context))
    }

    private val serviceKeyManager by before {
        DefaultServiceKeyManager(
            userClient = mockUserClient,
            keyRingServiceName = keyRingServiceName,
            keyManager = keyManager,
            logger = mockLogger,
        )
    }

    @Before
    fun init() {
        Timber.plant(Timber.DebugTree())
        keyManager.removeAllKeys()
    }

    @After
    fun fini() = runTest {
        Timber.uprootAll()
    }

    @Test
    fun shouldThrowIfNotRegistered() {
        mockUserClient.stub {
            on { getSubject() } doReturn null
        }

        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException> {
            serviceKeyManager.getKeyRingId()
        }
    }

    @Test
    fun shouldNotThrowIfRegistered() {
        serviceKeyManager.getKeyRingId() shouldNotBe null
    }

    @Test
    fun shouldBeAbleToPerformOperationsAfterSignIn() = runTest {
        serviceKeyManager.getKeyPairWithId("bogusValue") shouldBe null

        val keyPair = serviceKeyManager.generateKeyPair()
        with(keyPair) {
            this shouldNotBe null
            keyRingId shouldStartWith keyRingServiceName
            keyId.isBlank() shouldBe false
            publicKey shouldNotBe null
            publicKey.size shouldBeGreaterThan 0
            privateKey shouldNotBe null
            privateKey.size shouldBeGreaterThan 0
        }

        val newKeyPair = serviceKeyManager.generateKeyPair()
        newKeyPair shouldNotBe null
        newKeyPair shouldNotBe keyPair

        val fetchedKeyPair = serviceKeyManager.getKeyPairWithId(newKeyPair.keyId)
        fetchedKeyPair shouldNotBe null
        fetchedKeyPair shouldNotBe keyPair
        fetchedKeyPair shouldBe newKeyPair

        val fetchedPublicKey = serviceKeyManager.getPublicKeyWithId(keyPair.keyId)
        fetchedPublicKey shouldNotBe null

        serviceKeyManager.getKeyRingId() shouldStartWith keyRingServiceName

        val privateKeyExists = serviceKeyManager.privateKeyExists(newKeyPair.keyId)
        privateKeyExists shouldBe true

        val clearData = "hello world".toByteArray()
        var secretData = serviceKeyManager.encryptWithKeyPairId(
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
            KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
        )
        val decryptedData2 = serviceKeyManager.decryptWithKeyPairId(
            secretData2,
            newKeyPair.keyId,
            KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
        )
        decryptedData2 shouldBe clearData2

        keyManager.generateSymmetricKey("symmetricKey")
        val symmetricKey = serviceKeyManager.getSymmetricKeyData("symmetricKey")
        symmetricKey shouldNotBe null
        secretData = serviceKeyManager.encryptWithSymmetricKeyId("symmetricKey", clearData)

        decryptedData = serviceKeyManager.decryptWithSymmetricKey(symmetricKey!!, secretData)
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
    }

    @Test
    fun shouldBeAbleToGenerateSymmetricKeyId() = runTest {
        serviceKeyManager.getCurrentSymmetricKeyId() shouldBe null

        val symmetricKey = serviceKeyManager.generateNewCurrentSymmetricKey()
        symmetricKey.isBlank() shouldBe false

        val symmetricKeyId = serviceKeyManager.getCurrentSymmetricKeyId()
        symmetricKeyId shouldNotBe null
        symmetricKeyId?.isBlank() shouldBe false
    }
}
