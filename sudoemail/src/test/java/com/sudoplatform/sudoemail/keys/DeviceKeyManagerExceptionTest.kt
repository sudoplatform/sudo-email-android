/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import android.content.Context
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldThrow
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test the operation of [DefaultDeviceKeyManager] under exceptional conditions using mocks.
 *
 * @since 2020-08-05
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeviceKeyManagerExceptionTest : BaseTests() {

    private val keyRingServiceName = "sudo-email"

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "mockSubject"
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val deviceKeyManager by before {
        DefaultDeviceKeyManager(
            context = mockContext,
            userClient = mockUserClient,
            keyRingServiceName = keyRingServiceName,
            keyManager = mockKeyManager,
            logger = mockLogger
        )
    }

    @Test
    fun shouldThrowIfKeyManagerThrowsWhenExecutingGetCurrentKeyPair() {
        mockKeyManager.stub {
            on { getPassword(anyString()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyNotFoundException> {
            deviceKeyManager.getCurrentKeyPair()
        }
    }

    @Test
    fun shouldThrowIfKeyManagerThrowsWhenExecutingGetKeyPairWithId() {
        mockKeyManager.stub {
            on { getPublicKeyData(anyString()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyNotFoundException> {
            deviceKeyManager.getKeyPairWithId("42")
        }
    }

    @Test
    fun shouldThrowIfKeyManagerThrowsWhenExecutingGetKeyPairWithId2() {
        mockKeyManager.stub {
            on { getPublicKeyData(anyString()) } doReturn ByteArray(42)
            on { getPrivateKeyData(anyString()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyNotFoundException> {
            deviceKeyManager.getKeyPairWithId("42")
        }
    }

    @Test
    fun shouldThrowIfKeyManagerThrowsWhenExecutingGenerateNewCurrentKeyPair() {
        mockKeyManager.stub {
            on { getPassword(anyString()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException> {
            deviceKeyManager.generateNewCurrentKeyPair()
        }
    }

    @Test
    fun shouldThrowIfKeyManagerThrowsWhenExecutingDecryptWithPrivateKey() {
        mockKeyManager.stub {
            on { decryptWithPrivateKey(anyString(), any(), any()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.DecryptionException> {
            deviceKeyManager.decryptWithPrivateKey(
                ByteArray(42),
                "42",
                KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1
            )
        }
    }

    @Test
    fun shouldThrowIfKeyManagerThrowsDecryptWithSymmetricKey() {
        mockKeyManager.stub {
            on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.DecryptionException> {
            deviceKeyManager.decryptWithSymmetricKey(
                ByteArray(42),
                ByteArray(42)
            )
        }
    }

    @Test
    fun shouldThrowIfKeyManagerThrowsWhenExecutingRemoveAllKeys() {
        mockKeyManager.stub {
            on { removeAllKeys() } doThrow RuntimeException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.UnknownException> {
            deviceKeyManager.removeAllKeys()
        }
    }

    @Test
    fun shouldThrowIfKeyManagerThrowsWhenExecutingGetKeyRingId() {
        mockUserClient.stub {
            on { getSubject() } doThrow RuntimeException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException> {
            deviceKeyManager.getKeyRingId()
        }
    }
}
