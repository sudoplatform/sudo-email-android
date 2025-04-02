/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.PublicKey

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
    sealed class DeviceKeyManagerException(message: String? = null, cause: Throwable? = null) :
        RuntimeException(message, cause) {
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
     * Returns the [PublicKey] with the identifier [keyId] if it exists.
     *
     * @param keyId [String] Identifier of the Public Key to retrieve.
     * @return The [PublicKey] that matches [keyId] if it exists, null if it does not.
     * @throws [DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException] if key operation fails.
     */
    @Throws(DeviceKeyManagerException::class)
    fun getPublicKeyWithId(keyId: String): PublicKey?

    /**
     * Generate a random symmetric key which is not persisted in the Android key store.
     *
     * @return The generated random symmetric key.
     * @throws [DeviceKeyManagerException.KeyGenerationException] if unable to generate the symmetric key.
     */
    @Throws(DeviceKeyManagerException::class)
    fun generateRandomSymmetricKey(): ByteArray

    /**
     * Generate a new symmetric key.
     *
     * @return The generated symmetric key identifier.
     * @throws [DeviceKeyManagerException.KeyGenerationException] if unable to generate the symmetric key.
     */
    @Throws(DeviceKeyManagerException::class)
    fun generateNewCurrentSymmetricKey(): String

    /**
     * Returns the symmetric key identifier that is currently being used by this service.
     * If no symmetric key has been previously generated, will return null and require the caller
     * to call [generateNewCurrentSymmetricKey] if a current symmetric key is required.
     *
     * @return The current symmetric key identifier in use or null.
     * @throws [DeviceKeyManagerException.KeyOperationFailedException] if key operation fails.
     */
    @Throws(DeviceKeyManagerException::class)
    fun getCurrentSymmetricKeyId(): String?

    /**
     * Returns the symmetric key used by this service.
     *
     * @param keyId [String] Identifier of the symmetric key to retrieve.
     * @return The symmetric key data in bytes or null if the key cannot be found.
     * @throws [DeviceKeyManagerException.KeyOperationFailedException] if key operation fails.
     */
    @Throws(DeviceKeyManagerException::class)
    fun getSymmetricKeyData(keyId: String): ByteArray?

    /**
     * Returns true if the key identifier returns a key, false otherwise.
     *
     * @return True if key exists, false otherwise
     * @throws [DeviceKeyManagerException.KeyOperationFailedException] if key operation fails.
     */
    @Throws(DeviceKeyManagerException::class)
    fun symmetricKeyExists(keyId: String): Boolean

    /**
     * Returns true if the key identifier returns a private key, false otherwise.
     *
     * @return True if private key exists, false otherwise
     * @throws [DeviceKeyManagerException.KeyOperationFailedException] if key operation fails.
     */
    @Throws(DeviceKeyManagerException::class)
    fun privateKeyExists(keyId: String): Boolean

    /**
     * Creates random data of size specified by [size] input.
     *
     * @param size [Int] The size (in bytes) of the random data to create.
     * @return The random data.
     */
    fun createRandomData(size: Int): ByteArray

    /**
     * Decrypt the [data] with the private key identified by [keyId] and [algorithm].
     *
     * @param data [ByteArray] Data to be decrypted.
     * @param keyId [String] Key to use to decrypt the [data].
     * @param algorithm [KeyManagerInterface.PublicKeyEncryptionAlgorithm] Algorithm to use to decrypt the [data].
     * @return The decrypted data.
     * @throws [DeviceKeyManagerException.DecryptionException] if the data cannot be decrypted
     */
    @Throws(DeviceKeyManagerException::class)
    fun decryptWithKeyPairId(
        data: ByteArray,
        keyId: String,
        algorithm: KeyManagerInterface.PublicKeyEncryptionAlgorithm,
    ): ByteArray

    /**
     * Decrypt the [data] with the symmetric key [key].
     *
     * @param key [ByteArray] Key to use to decrypt the [data].
     * @param data [ByteArray] Data to be decrypted.
     * @param initVector [ByteArray] The initialization vector.
     * @return The decrypted data.
     * @throws [DeviceKeyManagerException.DecryptionException] if the data cannot be decrypted
     */
    @Throws(DeviceKeyManagerException::class)
    fun decryptWithSymmetricKey(
        key: ByteArray,
        data: ByteArray,
        initVector: ByteArray? = null,
    ): ByteArray

    /**
     * Encrypt the [data] with the public key identified by [keyId].
     *
     * @param keyId [String] Key identifier belonging to the public key used to encrypt the [data].
     * @param data [ByteArray] Data to be encrypted.
     * @param algorithm [KeyManagerInterface.PublicKeyEncryptionAlgorithm] Algorithm to use to encrypt the [data].
     * @return The encrypted data.
     * @throws [DeviceKeyManagerException.EncryptionException] if the data cannot be encrypted.
     */
    @Throws(DeviceKeyManagerException::class)
    fun encryptWithKeyPairId(keyId: String, data: ByteArray, algorithm: KeyManagerInterface.PublicKeyEncryptionAlgorithm): ByteArray

    /**
     * Encrypt the [data] with the input public key.
     *
     * @param key [ByteArray] The public key used to encrypt the [data].
     * @param data [ByteArray] Data to be encrypted.
     * @param format [KeyManagerInterface.PublicKeyFormat] The format of the [key].
     * @param algorithm [KeyManagerInterface.PublicKeyEncryptionAlgorithm] Algorithm to use to encrypt the [data].
     * @return The encrypted data.
     * @throws [DeviceKeyManagerException.EncryptionException] if the data cannot be encrypted.
     */
    @Throws(DeviceKeyManagerException::class)
    fun encryptWithPublicKey(
        key: ByteArray,
        data: ByteArray,
        format: KeyManagerInterface.PublicKeyFormat,
        algorithm: KeyManagerInterface.PublicKeyEncryptionAlgorithm,
    ): ByteArray

    /**
     * Decrypt the [data] with the symmetric key [keyId].
     *
     * @param keyId [String] Key identifier belonging to the symmetric key used to decrypt the [data].
     * @param data [ByteArray] Data to be decrypted.
     * @return the decrypted data.
     * @throws [DeviceKeyManagerException.DecryptionException] if the data cannot be decrypted.
     */
    @Throws(DeviceKeyManagerException::class)
    fun decryptWithSymmetricKeyId(keyId: String, data: ByteArray): ByteArray

    /**
     * Encrypt the [data] with the symmetric key [keyId].
     *
     * @param keyId [String] Key identifier belonging to the symmetric key used to encrypt the [data].
     * @param data [ByteArray] Data to be encrypted.
     * @param initVector [ByteArray] The initialization vector. Must be 128 bit in size for AES-CBC and 96 for AES-GCM.
     * @return The encrypted data.
     * @throws [DeviceKeyManagerException.EncryptionException] if the data cannot be encrypted.
     */
    @Throws(DeviceKeyManagerException::class)
    fun encryptWithSymmetricKeyId(
        keyId: String,
        data: ByteArray,
        initVector: ByteArray? = null,
    ): ByteArray

    /**
     * Encrypt the [data] with the symmetric [key].
     *
     * @param key [ByteArray] The symmetric key used to encrypt the [data].
     * @param data [ByteArray] Data to be encrypted.
     * @param initVector [ByteArray] The initialization vector. Must be 128 bit in size for AES-CBC and 96 for AES-GCM.
     * @return The encrypted data.
     * @throws [DeviceKeyManagerException.EncryptionException] if the data cannot be encrypted.
     */
    @Throws(DeviceKeyManagerException::class)
    fun encryptWithSymmetricKey(
        key: ByteArray,
        data: ByteArray,
        initVector: ByteArray? = null,
    ): ByteArray

    /**
     * Import keys from a key archive
     *
     * @param archiveData [ByteArray] Key archive data to import the keys from.
     * @throws [DeviceKeyManagerException.SecureKeyArchiveException]
     */
    @Throws(DeviceKeyManagerException::class)
    fun importKeys(archiveData: ByteArray)

    /**
     * Export keys to a key archive.
     *
     * @return The key archive data.
     * @throws [DeviceKeyManagerException.SecureKeyArchiveException]
     */
    fun exportKeys(): ByteArray

    /**
     * Remove all the keys from the [DeviceKeyManager].
     */
    @Throws(DeviceKeyManagerException::class)
    fun removeAllKeys()
}
