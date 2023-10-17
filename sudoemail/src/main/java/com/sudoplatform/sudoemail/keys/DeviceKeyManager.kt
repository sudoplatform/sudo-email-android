/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import com.sudoplatform.sudokeymanager.KeyManagerInterface

/**
 * Responsible for managing the local storage and lifecycle of key pairs associated with the email service.
 */
internal interface DeviceKeyManager {

    /**
     * Defines the exceptions for the [DeviceKeyManager] methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class DeviceKeyManagerException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
        class UserIdNotFoundException(message: String? = null, cause: Throwable? = null) :
            DeviceKeyManagerException(message = message, cause = cause)
        class KeyGenerationException(message: String? = null, cause: Throwable? = null) :
            DeviceKeyManagerException(message = message, cause = cause)
        class KeyOperationFailedException(message: String? = null, cause: Throwable? = null) :
            DeviceKeyManagerException(message = message, cause = cause)
        class DecryptionException(message: String? = null, cause: Throwable? = null) :
            DeviceKeyManagerException(message = message, cause = cause)
        class EncryptionException(message: String? = null, cause: Throwable? = null) :
            DeviceKeyManagerException(message = message, cause = cause)
        class SecureKeyArchiveException(message: String? = null, cause: Throwable? = null) :
            DeviceKeyManagerException(message = message, cause = cause)
        class UnknownException(message: String? = null, cause: Throwable? = null) :
            DeviceKeyManagerException(message = message, cause = cause)
    }

    /**
     * Returns the key ring id associated with the owner's service.
     *
     * @return The identifier of the key ring associated with the owner's service.
     * @throws [DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException] if the user Id cannot be found.
     */
    @Throws(DeviceKeyManagerException::class)
    fun getKeyRingId(): String

    /**
     * Returns the [KeyPair] with the identifier [id] if it exists.
     *
     * @param id [String] Identifier of the [KeyPair] to retrieve.
     * @return The [KeyPair] with the identifier [id] if it exists, null if it does not.
     */
    @Throws(DeviceKeyManagerException::class)
    fun getKeyPairWithId(id: String): KeyPair?

    /**
     * Returns a new [KeyPair].
     *
     * @return The generated [KeyPair].
     * @throws [DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException] if unable to generate the [KeyPair].
     */
    @Throws(DeviceKeyManagerException::class)
    fun generateKeyPair(): KeyPair

    /**
     * Generate a new symmetric key.
     *
     * @return The generated symmetric key identifier.
     * @throws [DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException] if unable to generate the symmetric key.
     */
    @Throws(DeviceKeyManagerException::class)
    fun generateNewCurrentSymmetricKey(): String

    /**
     * Returns the symmetric key identifier that is currently being used by this service.
     * If no symmetric key has been previously generated, will return null and require the caller
     * to call [generateNewCurrentSymmetricKey] if a current symmetric key is required.
     *
     * @return The current symmetric key identifier in use or null.
     * @throws [DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException] if key operation fails.
     */
    @Throws(DeviceKeyManagerException::class)
    fun getCurrentSymmetricKeyId(): String?

    /**
     * Decrypt the [data] with the private key [keyId] and [algorithm].
     *
     * @param data [ByteArray] Data to be decrypted.
     * @param keyId [String] Key to use to decrypt the [data].
     * @param algorithm [KeyManagerInterface.PublicKeyEncryptionAlgorithm] Algorithm to use to decrypt the [data].
     * @return The decrypted data.
     * @throws [DeviceKeyManager.DeviceKeyManagerException.DecryptionException] if the data cannot be decrypted
     */
    @Throws(DeviceKeyManagerException::class)
    fun decryptWithPrivateKey(data: ByteArray, keyId: String, algorithm: KeyManagerInterface.PublicKeyEncryptionAlgorithm): ByteArray

    /**
     * Decrypt the [data] with the symmetric key [key].
     *
     * @param key [ByteArray] Key to use to decrypt the [data].
     * @param data [ByteArray] Data to be decrypted.
     * @return The decrypted data.
     * @throws [DeviceKeyManager.DeviceKeyManagerException.DecryptionException] if the data cannot be decrypted
     */
    @Throws(DeviceKeyManagerException::class)
    fun decryptWithSymmetricKey(key: ByteArray, data: ByteArray): ByteArray

    /**
     * Decrypt the [data] with the symmetric key [keyId].
     *
     * @param keyId [String] Key identifier belonging to the symmetric key used to decrypt the [data].
     * @param data [ByteArray] Data to be decrypted.
     * @return the decrypted data.
     * @throws [DeviceKeyManager.DeviceKeyManagerException.DecryptionException] if the data cannot be decrypted.
     */
    @Throws(DeviceKeyManagerException::class)
    fun decryptWithSymmetricKeyId(keyId: String, data: ByteArray): ByteArray

    /**
     * Encrypt the [data] with the symmetric key [keyId].
     *
     * @param keyId [String] Key identifier belonging to the symmetric key used to encrypt the [data].
     * @param data [ByteArray] Data to be encrypted.
     * @return The encrypted data.
     * @throws [DeviceKeyManager.DeviceKeyManagerException.EncryptionException] if the data cannot be encrypted.
     */
    @Throws(DeviceKeyManagerException::class)
    fun encryptWithSymmetricKeyId(keyId: String, data: ByteArray): ByteArray

    /**
     * Import keys from a key archive.
     *
     * @param archiveData [ByteArray] Key archive data to import the keys from.
     * @throws [DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException]
     */
    @Throws(DeviceKeyManagerException::class)
    fun importKeys(archiveData: ByteArray)

    /**
     * Export keys to a key archive.
     *
     * @return The key archive data.
     * @throws [DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException]
     */
    fun exportKeys(): ByteArray

    /**
     * Remove all the keys from the [DeviceKeyManager].
     */
    @Throws(DeviceKeyManagerException::class)
    fun removeAllKeys()
}
