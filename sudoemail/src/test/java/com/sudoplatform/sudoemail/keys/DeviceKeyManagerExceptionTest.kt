/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldThrow
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test the operation of [DefaultDeviceKeyManager] under exceptional conditions using mocks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeviceKeyManagerExceptionTest : BaseTests() {

    private val keyRingServiceName = "sudo-email"

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "mockSubject"
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val serviceKeyManager by before {
        DefaultServiceKeyManager(
            userClient = mockUserClient,
            keyRingServiceName = keyRingServiceName,
            keyManager = mockKeyManager,
            logger = mockLogger,
        )
    }

    @Test
    fun generateKeyPairShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { generateKeyPair(anyString(), anyBoolean()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException> {
            serviceKeyManager.generateKeyPair()
        }
    }

    @Test
    fun getKeyPairWithIdShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { getPublicKeyData(anyString()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException> {
            serviceKeyManager.getKeyPairWithId("42")
        }
    }

    @Test
    fun getKeyPairWithIdShouldThrowIfKeyManagerThrows2() {
        mockKeyManager.stub {
            on { getPublicKeyData(anyString()) } doReturn ByteArray(42)
            on { getPrivateKeyData(anyString()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException> {
            serviceKeyManager.getKeyPairWithId("42")
        }
    }

    @Test
    fun getPublicKeyWithIdShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { getPublicKey(anyString()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException> {
            serviceKeyManager.getPublicKeyWithId("notAnId")
        }
    }

    @Test
    fun getCurrentSymmetricKeyIdShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { getPassword(anyString()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException> {
            serviceKeyManager.getCurrentSymmetricKeyId()
        }
    }

    @Test
    fun generateRandomSymmetricKeyShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { generateSymmetricKey(anyString()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException> {
            serviceKeyManager.generateRandomSymmetricKey()
        }
    }

    @Test
    fun generateNewCurrentSymmetricKeyShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { deletePassword(anyString()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException> {
            serviceKeyManager.generateNewCurrentSymmetricKey()
        }
    }

    @Test
    fun symmetricKeyExistsShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { getSymmetricKeyData(anyString()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException> {
            serviceKeyManager.symmetricKeyExists("42")
        }
    }

    @Test
    fun privateKeyExistsShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { getPrivateKeyData(anyString()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException> {
            serviceKeyManager.privateKeyExists("42")
        }
    }

    @Test
    fun getSymmetricKeyDataShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { getSymmetricKeyData(anyString()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException> {
            serviceKeyManager.getSymmetricKeyData("42")
        }
    }

    @Test
    fun decryptWithKeyPairIdShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { decryptWithPrivateKey(anyString(), any(), any()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.DecryptionException> {
            serviceKeyManager.decryptWithKeyPairId(
                ByteArray(42),
                "42",
                KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
            )
        }
    }

    @Test
    fun encryptWithKeyPairIdShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { encryptWithPublicKey(anyString(), any(), any()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.EncryptionException> {
            serviceKeyManager.encryptWithKeyPairId(
                "42",
                ByteArray(42),
                KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
            )
        }
    }

    @Test
    fun encryptWithPublicKeyShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { encryptWithPublicKey(any<ByteArray>(), any<ByteArray>(), any()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.EncryptionException> {
            serviceKeyManager.encryptWithPublicKey(
                ByteArray(42),
                ByteArray(42),
                KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
            )
        }
    }

    @Test
    fun decryptWithSymmetricKeyShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.DecryptionException> {
            serviceKeyManager.decryptWithSymmetricKey(
                ByteArray(42),
                ByteArray(42),
            )
        }
    }

    @Test
    fun decryptWithSymmetricKeyWithIVShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>(), any<ByteArray>()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.DecryptionException> {
            serviceKeyManager.decryptWithSymmetricKey(
                ByteArray(42),
                ByteArray(42),
                ByteArray(42),
            )
        }
    }

    @Test
    fun decryptWithSymmetricKeyIdShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { decryptWithSymmetricKey(anyString(), any<ByteArray>()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.DecryptionException> {
            serviceKeyManager.decryptWithSymmetricKeyId(
                "42",
                ByteArray(42),
            )
        }
    }

    @Test
    fun encryptWithSymmetricKeyIdShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { encryptWithSymmetricKey(anyString(), any()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.EncryptionException> {
            serviceKeyManager.encryptWithSymmetricKeyId(
                "42",
                ByteArray(42),
            )
        }
    }

    @Test
    fun encryptWithSymmetricKeyIdWithIVShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { encryptWithSymmetricKey(anyString(), any<ByteArray>(), any<ByteArray>()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.EncryptionException> {
            serviceKeyManager.encryptWithSymmetricKeyId(
                "42",
                ByteArray(42),
                ByteArray(42),
            )
        }
    }

    @Test
    fun encryptWithSymmetricKeyShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { encryptWithSymmetricKey(any<ByteArray>(), any()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.EncryptionException> {
            serviceKeyManager.encryptWithSymmetricKey(
                ByteArray(42),
                ByteArray(42),
            )
        }
    }

    @Test
    fun encryptWithSymmetricKeyWithIVShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { encryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>(), any<ByteArray>()) } doThrow KeyManagerException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.EncryptionException> {
            serviceKeyManager.encryptWithSymmetricKey(
                ByteArray(42),
                ByteArray(42),
                ByteArray(42),
            )
        }
    }

    @Test
    fun removeAllKeysShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { removeAllKeys() } doThrow RuntimeException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.UnknownException> {
            serviceKeyManager.removeAllKeys()
        }
    }

    @Test
    fun getKeyRingIdShouldThrowIfUserClientThrows() {
        mockUserClient.stub {
            on { getSubject() } doThrow RuntimeException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException> {
            serviceKeyManager.getKeyRingId()
        }
    }

    @Test
    fun exportKeysShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { exportKeys() } doThrow RuntimeException("mock")
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException> {
            serviceKeyManager.exportKeys()
        }
    }

    @Test
    fun importKeysShouldThrowIfKeyManagerThrows() {
        mockKeyManager.stub {
            on { addPassword(any(), anyString(), anyBoolean()) } doThrow RuntimeException("mock")
            on { addSymmetricKey(any(), anyString(), anyBoolean()) } doThrow RuntimeException("mock")
        }
        val dummyKeyArchive = "dummy key archive".toByteArray()
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException> {
            serviceKeyManager.importKeys(dummyKeyArchive)
        }
    }
}
