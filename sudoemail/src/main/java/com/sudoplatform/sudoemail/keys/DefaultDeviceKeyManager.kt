/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import androidx.annotation.VisibleForTesting
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudokeymanager.KeyType
import com.sudoplatform.sudokeymanager.SecureKeyArchive
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.PublicKey
import java.util.UUID

/**
 * Responsible for Managing the lifecycle of key pairs associated with the email service.
 */
internal open class DefaultDeviceKeyManager(
    private val keyManager: KeyManagerInterface,
    private val logger: Logger = Logger(
        LogConstants.SUDOLOG_TAG,
        AndroidUtilsLogDriver(LogLevel.INFO),
    ),
) : DeviceKeyManager {

    companion object {
        @VisibleForTesting
        private const val SECRET_KEY_ID_NAME = "eml-secret-key"
    }

    override fun getPublicKeyWithId(keyId: String): PublicKey? {
        try {
            val publicKey = keyManager.getPublicKey(keyId) ?: return null
            return PublicKey(keyId, publicKey.encoded)
        } catch (e: KeyManagerException) {
            throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException(
                "KeyManager exception",
                e,
            )
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun generateRandomSymmetricKey(): ByteArray {
        val keyId = UUID.randomUUID().toString()
        try {
            keyManager.generateSymmetricKey(keyId)
            val symmetricKey = keyManager.getSymmetricKeyData(keyId)
                ?: throw DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException(
                    "Failed to generate symmetric key",
                )
            keyManager.deleteSymmetricKey(keyId)
            return symmetricKey
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException(
                "Failed to generate symmetric key",
                e,
            )
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
            throw DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException(
                "Failed to generate symmetric key",
                e,
            )
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
            throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException(
                "KeyManager exception",
                e,
            )
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun symmetricKeyExists(keyId: String): Boolean {
        try {
            val data = keyManager.getSymmetricKeyData(keyId)
            return data != null
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException(
                "KeyManager exception",
                e,
            )
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun privateKeyExists(keyId: String): Boolean {
        try {
            val data = keyManager.getPrivateKeyData(keyId)
            return data != null
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException(
                "KeyManager exception",
                e,
            )
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun getSymmetricKeyData(keyId: String): ByteArray? {
        try {
            return keyManager.getSymmetricKeyData(keyId)
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException("KeyManager exception", e)
        }
    }

    override fun createRandomData(size: Int): ByteArray {
        return keyManager.createRandomData(size)
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun decryptWithKeyPairId(
        data: ByteArray,
        keyId: String,
        algorithm: KeyManagerInterface.PublicKeyEncryptionAlgorithm,
    ): ByteArray {
        try {
            return keyManager.decryptWithPrivateKey(keyId, data, algorithm)
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.DecryptionException(
                "Failed to decrypt",
                e,
            )
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun encryptWithKeyPairId(
        keyId: String,
        data: ByteArray,
        algorithm: KeyManagerInterface.PublicKeyEncryptionAlgorithm,
    ): ByteArray {
        try {
            return keyManager.encryptWithPublicKey(keyId, data, algorithm)
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.EncryptionException("Failed to encrypt", e)
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun encryptWithPublicKey(
        key: ByteArray,
        data: ByteArray,
        format: KeyManagerInterface.PublicKeyFormat,
        algorithm: KeyManagerInterface.PublicKeyEncryptionAlgorithm,
    ): ByteArray {
        try {
            return keyManager.encryptWithPublicKey(key, data, format, algorithm)
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.EncryptionException("Failed to encrypt", e)
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun decryptWithSymmetricKey(key: ByteArray, data: ByteArray, initVector: ByteArray?): ByteArray {
        return try {
            if (initVector != null) {
                keyManager.decryptWithSymmetricKey(key, data, initVector)
            } else {
                keyManager.decryptWithSymmetricKey(key, data)
            }
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.DecryptionException(
                "Failed to decrypt",
                e,
            )
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun decryptWithSymmetricKeyId(keyId: String, data: ByteArray): ByteArray {
        try {
            return keyManager.decryptWithSymmetricKey(keyId, data)
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.DecryptionException(
                "Failed to decrypt",
                e,
            )
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun encryptWithSymmetricKeyId(
        keyId: String,
        data: ByteArray,
        initVector: ByteArray?,
    ): ByteArray {
        return try {
            if (initVector != null) {
                keyManager.encryptWithSymmetricKey(keyId, data, initVector)
            } else {
                keyManager.encryptWithSymmetricKey(keyId, data)
            }
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.EncryptionException("Failed to encrypt", e)
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun encryptWithSymmetricKey(
        key: ByteArray,
        data: ByteArray,
        initVector: ByteArray?,
    ): ByteArray {
        return try {
            if (initVector != null) {
                keyManager.encryptWithSymmetricKey(key, data, initVector)
            } else {
                keyManager.encryptWithSymmetricKey(key, data)
            }
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
            throw DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException(
                "Failed to import keys",
                e,
            )
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun exportKeys(): ByteArray {
        val archive = SecureKeyArchive.getInstanceV3(keyManager)
        try {
            val excludedKeyTypes: MutableSet<KeyType> = HashSet()
            excludedKeyTypes.add(KeyType.PUBLIC_KEY)
            archive.excludedKeyTypes = excludedKeyTypes
            archive.loadKeys()
            return archive.archive()
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException(
                "Failed to export keys",
                e,
            )
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun removeAllKeys() {
        try {
            return keyManager.removeAllKeys()
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.UnknownException(
                "Failed to remove all keys",
                e,
            )
        }
    }
}
