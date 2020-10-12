/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import com.sudoplatform.sudokeymanager.KeyManagerInterface

/**
 * Responsible for managing the local storage and lifecycle of key pairs associated with the email service.
 *
 * @since 2020-08-05
 */
internal interface DeviceKeyManager {

    /**
     * Defines the exceptions for the [DeviceKeyManager] methods.
     *
     * @property message Accompanying message for the exception.
     * @property cause The cause for the exception.
     */
    sealed class DeviceKeyManagerException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
        class UserIdNotFoundException(message: String? = null, cause: Throwable? = null) :
            DeviceKeyManagerException(message = message, cause = cause)
        class KeyGenerationException(message: String? = null, cause: Throwable? = null) :
            DeviceKeyManagerException(message = message, cause = cause)
        class KeyNotFoundException(message: String? = null, cause: Throwable? = null) :
            DeviceKeyManagerException(message = message, cause = cause)
        class DecryptionException(message: String? = null, cause: Throwable? = null) :
            DeviceKeyManagerException(message = message, cause = cause)
        class UnknownException(message: String? = null, cause: Throwable? = null) :
            DeviceKeyManagerException(message = message, cause = cause)
    }

    /**
     * Returns the key ring id associated with the owner's service.
     *
     * @return the identifier of the key ring associated with the owner's service
     * @throws [DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException] if the user Id cannot be found.
     */
    @Throws(DeviceKeyManagerException::class)
    fun getKeyRingId(): String

    /**
     * Returns the key pair that is currently being used by this service.
     * If no key pair has been previously generated, will return null and require the caller
     * to call [generateNewCurrentKeyPair] if a current key pair is required.
     *
     * @return the current key pair in use or null.
     */
    @Throws(DeviceKeyManagerException::class)
    fun getCurrentKeyPair(): KeyPair?

    /**
     * Returns the [KeyPair] with the identifier [id] if it exists.
     *
     * @return the [KeyPair] with the identifier [id] if it exists, null if it does not.
     */
    @Throws(DeviceKeyManagerException::class)
    fun getKeyPairWithId(id: String): KeyPair?

    /**
     * Generate a new [KeyPair] and make it the current [KeyPair].
     *
     * @return the generated [KeyPair]
     * @throws [DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException] if unable to generate the [KeyPair]
     */
    @Throws(DeviceKeyManagerException::class)
    fun generateNewCurrentKeyPair(): KeyPair

    /**
     * Decrypt the [data] with the private key [keyId] and [algorithm]
     *
     * @param data Data to be decrypted
     * @param keyId Key to use to decrypt the [data]
     * @param algorithm Algorithm to use to decrypt the [data]
     * @return the decrypted data
     * @throws [DeviceKeyManager.DeviceKeyManagerException.KeyNotFoundException] if the key with identifier [keyId] cannot be found
     * @throws [DeviceKeyManager.DeviceKeyManagerException.DecryptionException] if the data cannot be decrypted
     */
    @Throws(DeviceKeyManagerException::class)
    fun decryptWithPrivateKey(data: ByteArray, keyId: String, algorithm: KeyManagerInterface.PublicKeyEncryptionAlgorithm): ByteArray

    /**
     * Decrypt the [data] with the symmetric key [key]
     *
     * @param key Key to use to decrypt the [data]
     * @param data Data to be decrypted
     * @return the decrypted data
     * @throws [DeviceKeyManager.DeviceKeyManagerException.DecryptionException] if the data cannot be decrypted
     */
    @Throws(DeviceKeyManagerException::class)
    fun decryptWithSymmetricKey(key: ByteArray, data: ByteArray): ByteArray

    /**
     * Remove all the keys from the [DeviceKeyManager]
     */
    @Throws(DeviceKeyManagerException::class)
    fun removeAllKeys()
}
