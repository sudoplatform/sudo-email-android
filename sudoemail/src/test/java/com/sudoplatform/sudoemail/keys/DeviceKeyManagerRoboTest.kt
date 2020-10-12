/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

/**
 * Test the operation of [DefaultDeviceKeyManager] using mocks under Robolectric.
 *
 * @since 2020-08-05
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeviceKeyManagerRoboTest : BaseTests() {

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

    private val deviceKeyManager by before {
        DefaultDeviceKeyManager(
            context = context,
            userClient = mockUserClient,
            keyRingServiceName = keyRingServiceName,
            keyManager = keyManager,
            logger = mockLogger
        )
    }

    @Before
    fun init() {
        Timber.plant(Timber.DebugTree())
        keyManager.removeAllKeys()
    }

    @After
    fun fini() = runBlocking {
        Timber.uprootAll()
    }

    @Test
    fun shouldThrowIfNotRegistered() {
        // given
        mockUserClient.stub {
            on { getSubject() } doReturn null
        }

        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException> {
            deviceKeyManager.getKeyRingId()
        }
    }

    @Test
    fun shouldNotThrowIfRegistered() {
        deviceKeyManager.getKeyRingId() shouldNotBe null
    }

    @Test
    fun shouldBeAbleToPerformOperationsAfterSignIn() = runBlocking {

        deviceKeyManager.getCurrentKeyPair() shouldBe null
        deviceKeyManager.getKeyPairWithId("bogusValue") shouldBe null

        val keyPair = deviceKeyManager.generateNewCurrentKeyPair()
        with(keyPair) {
            this shouldNotBe null
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

        keyManager.generateSymmetricKey("symmetricKey")
        val symmetricKey = keyManager.getSymmetricKeyData("symmetricKey")
        secretData = keyManager.encryptWithSymmetricKey("symmetricKey", clearData)

        decryptedData = deviceKeyManager.decryptWithSymmetricKey(symmetricKey, secretData)
        decryptedData shouldBe clearData
    }
}
