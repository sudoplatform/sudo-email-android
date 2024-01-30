/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import androidx.annotation.VisibleForTesting
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudokeymanager.SecureKeyArchive
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import java.util.UUID

/**
 * Responsible for Managing the lifecycle of key pairs associated with the email service.
 */
internal class DefaultDeviceKeyManager(
    private val keyRingServiceName: String,
    private val userClient: SudoUserClient,
    private val keyManager: KeyManagerInterface,
    private val logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO)),
) : DeviceKeyManager {

    companion object {
        @VisibleForTesting
        private const val SECRET_KEY_ID_NAME = "eml-secret-key"
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException::class)
    override fun getKeyRingId(): String {
        try {
            val userId = userClient.getSubject()
                ?: throw DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException("UserId not found")
            return "$keyRingServiceName.$userId"
        } catch (e: Exception) {
            throw DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException("UserId could not be accessed", e)
        }
    }

    override fun getKeyPairWithId(id: String): KeyPair? {
        try {
            val publicKey = keyManager.getPublicKeyData(id)
                ?: return null
            val privateKey = keyManager.getPrivateKeyData(id)
                ?: return null
            return KeyPair(
                keyId = id,
                keyRingId = getKeyRingId(),
                publicKey = publicKey,
                privateKey = privateKey,
            )
        } catch (e: KeyManagerException) {
            throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException("KeyManager exception", e)
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun generateKeyPair(): KeyPair {
        val keyId = UUID.randomUUID().toString()
        try {
            // Generate the key pair
            keyManager.generateKeyPair(keyId, true)

            val publicKey = keyManager.getPublicKeyData(keyId)
            val privateKey = keyManager.getPrivateKeyData(keyId)
            return KeyPair(
                keyId = keyId,
                keyRingId = getKeyRingId(),
                publicKey = publicKey,
                privateKey = privateKey,
            )
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException("Failed to generate key pair", e)
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun generateNewCurrentSymmetricKey(): String {
        val keyId = UUID.randomUUID().toString()
        try {
            // Replace the old current key identifier with a new one
            keyManager.deletePassword(SECRET_KEY_ID_NAME)
            keyManager.addPassword(keyId.toByteArray(), SECRET_KEY_ID_NAME)

            // Generate the key pair for the new symmetric key
            keyManager.generateSymmetricKey(keyId)
            return keyId
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException("Failed to generate symmetric key", e)
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun getCurrentSymmetricKeyId(): String? {
        try {
            val symmetricKeyIdBits = keyManager.getPassword(SECRET_KEY_ID_NAME) ?: return null
            val symmetricKeyId = symmetricKeyIdBits.toString(Charsets.UTF_8)
            keyManager.getSymmetricKeyData(symmetricKeyId) ?: return null
            return symmetricKeyId
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException("KeyManager exception", e)
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun decryptWithPrivateKey(
        data: ByteArray,
        keyId: String,
        algorithm: KeyManagerInterface.PublicKeyEncryptionAlgorithm,
    ): ByteArray {
        try {
            return keyManager.decryptWithPrivateKey(keyId, data, algorithm)
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.DecryptionException("Failed to decrypt", e)
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun decryptWithSymmetricKey(key: ByteArray, data: ByteArray): ByteArray {
        try {
            return keyManager.decryptWithSymmetricKey(key, data)
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.DecryptionException("Failed to decrypt", e)
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun decryptWithSymmetricKeyId(keyId: String, data: ByteArray): ByteArray {
        try {
            return keyManager.decryptWithSymmetricKey(keyId, data)
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.DecryptionException("Failed to decrypt", e)
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun encryptWithSymmetricKeyId(keyId: String, data: ByteArray): ByteArray {
        try {
            return keyManager.encryptWithSymmetricKey(keyId, data)
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.EncryptionException("Failed to encrypt", e)
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun importKeys(archiveData: ByteArray) {
        try {
            keyManager.removeAllKeys()
            val archive = SecureKeyArchive.getInstanceV3(archiveData, keyManager)
            archive.unarchive()
            archive.saveKeys()
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException("Failed to import keys", e)
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun exportKeys(): ByteArray {
        val archive = SecureKeyArchive.getInstanceV3(keyManager)
        try {
            archive.loadKeys()
            return archive.archive()
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException("Failed to export keys", e)
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun removeAllKeys() {
        try {
            return keyManager.removeAllKeys()
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.UnknownException("Failed to remove all keys", e)
        }
    }
}
