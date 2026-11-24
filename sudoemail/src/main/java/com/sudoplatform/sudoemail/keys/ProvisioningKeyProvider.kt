/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudologging.Logger

/**
 * The key pair an email address or email alias is provisioned with.
 *
 * @property keyPair [KeyPair] The key pair whose public key is registered with the service.
 * @property isGeneratedForProvisioning [Boolean] Whether the key pair was generated for this
 *  provisioning, so belongs to it alone and may be removed if the provisioning is rejected.
 *  `false` for a key pair the caller supplied and for the singleton key pair.
 */
internal data class ProvisioningKey(
    val keyPair: KeyPair,
    val isGeneratedForProvisioning: Boolean,
)

/**
 * Chooses the key pair an email address or email alias is provisioned with, following the
 * client's key configuration.
 *
 * @property serviceKeyManager [ServiceKeyManager] Reads, generates and removes key pairs.
 * @property logger [Logger] The SDK logger.
 * @property enforceSingletonKeyPair [Boolean] Whether every email address and email alias shares
 *  one key pair.
 * @property allowCallerManagedKeys [Boolean] Whether the caller may supply the key pair to
 *  provision with.
 * @property invalidArgumentException Creates the exception thrown when a key pair is supplied but
 *  [allowCallerManagedKeys] is not set.
 * @property singletonKeyPairNotFoundException Creates the exception thrown when the enforced
 *  singleton key pair is not on this device.
 */
internal class ProvisioningKeyProvider(
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
    private val enforceSingletonKeyPair: Boolean,
    private val allowCallerManagedKeys: Boolean,
    private val invalidArgumentException: (String) -> Throwable,
    private val singletonKeyPairNotFoundException: (String) -> Throwable,
) {
    companion object {
        /** A provider that throws [SudoEmailClient.EmailAddressException]s. */
        fun forEmailAddress(
            serviceKeyManager: ServiceKeyManager,
            logger: Logger,
            enforceSingletonKeyPair: Boolean,
            allowCallerManagedKeys: Boolean,
        ) = ProvisioningKeyProvider(
            serviceKeyManager = serviceKeyManager,
            logger = logger,
            enforceSingletonKeyPair = enforceSingletonKeyPair,
            allowCallerManagedKeys = allowCallerManagedKeys,
            invalidArgumentException = { SudoEmailClient.EmailAddressException.InvalidArgumentException(it) },
            singletonKeyPairNotFoundException = {
                SudoEmailClient.EmailAddressException.SingletonKeyPairNotFoundException(it)
            },
        )

        /** A provider that throws [SudoEmailClient.EmailAliasException]s. */
        fun forEmailAlias(
            serviceKeyManager: ServiceKeyManager,
            logger: Logger,
            enforceSingletonKeyPair: Boolean,
            allowCallerManagedKeys: Boolean,
        ) = ProvisioningKeyProvider(
            serviceKeyManager = serviceKeyManager,
            logger = logger,
            enforceSingletonKeyPair = enforceSingletonKeyPair,
            allowCallerManagedKeys = allowCallerManagedKeys,
            invalidArgumentException = { SudoEmailClient.EmailAliasException.InvalidArgumentException(it) },
            singletonKeyPairNotFoundException = {
                SudoEmailClient.EmailAliasException.SingletonKeyPairNotFoundException(it)
            },
        )
    }

    /**
     * The key pair to provision with: the one the caller supplied, if [allowCallerManagedKeys] is
     * set, even with a singleton key pair enforced; otherwise the singleton key pair when
     * [enforceSingletonKeyPair] is set, which is never created here; otherwise a new key pair.
     *
     * @param keyId [String] Identifier of the key pair the caller supplied, if any.
     * @return The [ProvisioningKey] to provision with.
     * @throws Throwable from [invalidArgumentException] if [keyId] is supplied but
     *  [allowCallerManagedKeys] is not set.
     * @throws KeyNotFoundException if the supplied key pair is not on this device.
     * @throws Throwable from [singletonKeyPairNotFoundException] if the enforced singleton key pair
     *  is not on this device.
     */
    fun key(keyId: String?): ProvisioningKey =
        when {
            keyId != null -> {
                if (!allowCallerManagedKeys) {
                    throw invalidArgumentException(StringConstants.CALLER_MANAGED_KEYS_NOT_ALLOWED_MSG)
                }
                val keyPair =
                    serviceKeyManager.getKeyPairWithId(keyId)
                        ?: throw KeyNotFoundException(StringConstants.PUBLIC_KEY_NOT_FOUND_ERROR_MSG)
                ProvisioningKey(keyPair, isGeneratedForProvisioning = false)
            }
            enforceSingletonKeyPair -> {
                val keyPair =
                    serviceKeyManager.getSingletonKeyPair()
                        ?: throw singletonKeyPairNotFoundException(StringConstants.SINGLETON_KEY_PAIR_NOT_FOUND_MSG)
                ProvisioningKey(keyPair, isGeneratedForProvisioning = false)
            }
            else -> ProvisioningKey(serviceKeyManager.generateKeyPair(), isGeneratedForProvisioning = true)
        }

    /**
     * Removes a key pair generated for a provisioning the service definitely rejected. Does nothing
     * for a key pair the caller supplied or for the singleton key pair. A failure is logged, never
     * thrown, so it can't replace the provisioning error.
     *
     * @param key [ProvisioningKey] The key the provisioning used.
     */
    fun discardRejectedKey(key: ProvisioningKey) {
        if (!key.isGeneratedForProvisioning) return
        val keyId = key.keyPair.keyId
        try {
            serviceKeyManager.removeKeyPair(keyId)
        } catch (e: Exception) {
            logger.warning("Couldn't remove key pair $keyId after a rejected provision; it stays on this device: $e")
        }
    }
}
