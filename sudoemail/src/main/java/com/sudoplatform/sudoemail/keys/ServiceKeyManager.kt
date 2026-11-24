/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import com.sudoplatform.sudoemail.keys.DeviceKeyManager.DeviceKeyManagerException

internal interface ServiceKeyManager : DeviceKeyManager {
    /**
     * Returns the key ring id associated with the owner's service.
     *
     * @return The identifier of the key ring associated with the owner's service.
     * @throws [DeviceKeyManagerException.UserIdNotFoundException] if the user Id cannot be found.
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
     * @throws [DeviceKeyManagerException.KeyGenerationException] if unable to generate the [KeyPair].
     */
    @Throws(DeviceKeyManagerException::class)
    fun generateKeyPair(): KeyPair

    /**
     * Returns the single [KeyPair] shared by every email address and email alias provisioned
     * while the client enforces a singleton key pair. Never creates it; see
     * [createSingletonKeyPairIfAbsent].
     *
     * @return The singleton [KeyPair], or `null` if none has been recorded or its private key is
     *  not on this device.
     * @throws [DeviceKeyManagerException.KeyOperationFailedException] if the key store holds the
     *  singleton's private key but not its public key, or the key pair cannot be read.
     */
    @Throws(DeviceKeyManagerException::class)
    fun getSingletonKeyPair(): KeyPair?

    /**
     * Returns the singleton [KeyPair], creating it if this device does not have it.
     *
     * The singleton key pair's identifier is recorded in the key store, so it is carried by
     * [exportKeys] and [importKeys] and cleared by [removeAllKeys]. If an identifier is recorded
     * but its private key is missing, a new singleton key pair replaces it.
     *
     * @return The singleton [KeyPair], and whether it had to be created.
     * @throws [DeviceKeyManagerException.KeyOperationFailedException] if the key store holds the
     *  singleton's private key but not its public key, or the key pair cannot be read or
     *  recorded.
     * @throws [DeviceKeyManagerException.KeyGenerationException] if the key pair cannot be
     *  generated.
     */
    @Throws(DeviceKeyManagerException::class)
    fun createSingletonKeyPairIfAbsent(): SingletonKeyPairResult

    /**
     * Returns the identifier of the singleton key pair, or `null` if none has been recorded. The
     * key pair itself may not be on this device.
     *
     * @throws [DeviceKeyManagerException.KeyOperationFailedException] if the identifier cannot be
     *  read.
     */
    @Throws(DeviceKeyManagerException::class)
    fun getSingletonKeyId(): String?

    /**
     * Removes the key pair with the identifier [keyId], unless it is the singleton key pair,
     * which other email addresses and email aliases may still be sealed to.
     *
     * Removing a key pair that does not exist succeeds.
     *
     * @param keyId [String] Identifier of the key pair to remove.
     * @throws [DeviceKeyManagerException.KeyOperationFailedException] if the removal fails.
     */
    @Throws(DeviceKeyManagerException::class)
    fun removeKeyPair(keyId: String)
}

/**
 * The singleton key pair, and whether [ServiceKeyManager.createSingletonKeyPairIfAbsent] had to
 * create it.
 */
internal data class SingletonKeyPairResult(
    val keyPair: KeyPair,
    val created: Boolean,
)
