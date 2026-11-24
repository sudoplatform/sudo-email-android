/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress

import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.DeprovisionEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.SealedEmailAddressEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudologging.Logger
import java.util.concurrent.CancellationException

/**
 * Application business logic for deprovisioning an email address.
 *
 * With [allowCallerManagedKeys] set to `false`, once the service has deprovisioned the email
 * address its key pairs are removed from the device, except a key pair that another email address
 * or email alias still has registered with the service. Nothing is removed while [enforceSingletonKeyPair] is
 * set, and the singleton key pair is never removed.
 *
 * @property emailAddressService [EmailAddressService] Deprovisions the email address and lists
 *  the user's key registrations.
 * @property serviceKeyManager [ServiceKeyManager] Removes the deprovisioned email address's key
 *  pairs.
 * @property logger [Logger] The SDK logger.
 * @property enforceSingletonKeyPair [Boolean] Whether every email address and email alias shares
 *  one key pair.
 * @property allowCallerManagedKeys [Boolean] Whether the app manages its key pairs, so none are
 *  removed. When `false`, the deprovisioned email address's key pairs are removed once nothing
 *  else uses them.
 */
internal class DeprovisionEmailAddressUseCase(
    private val emailAddressService: EmailAddressService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
    private val enforceSingletonKeyPair: Boolean,
    private val allowCallerManagedKeys: Boolean,
) {
    /**
     * Deprovision the email address, then remove its key pairs that nothing else uses.
     *
     * @param id [String] Identifier of the email address to deprovision.
     * @return The deprovisioned [SealedEmailAddressEntity].
     * @throws DeprovisionedKeyPairRemovalException if the email address was deprovisioned but some
     *  of its key pairs could not be removed. Every key pair is attempted first.
     */
    suspend fun execute(id: String): SealedEmailAddressEntity {
        logger.debug(this::class.java.simpleName)
        val deprovisioned = emailAddressService.deprovision(DeprovisionEmailAddressRequest(emailAddressId = id))
        if (!allowCallerManagedKeys && !enforceSingletonKeyPair) {
            removeKeyPairsNoLongerInUse(deprovisioned)
        }
        return deprovisioned
    }

    /**
     * Any doubt keeps a key pair: if the singleton's identifier or the service's key
     * registrations can't be read, nothing is removed.
     */
    private suspend fun removeKeyPairsNoLongerInUse(deprovisioned: SealedEmailAddressEntity) {
        val candidateKeyIds =
            try {
                val singletonKeyId = if (deprovisioned.keyIds.isEmpty()) null else serviceKeyManager.getSingletonKeyId()
                deprovisioned.keyIds.filter { it != singletonKeyId }
            } catch (e: Exception) {
                logger.warning("Keeping the key pairs of deprovisioned email address ${deprovisioned.id}: $e")
                return
            }
        if (candidateKeyIds.isEmpty()) {
            return
        }

        val keyIdsInUse =
            try {
                emailAddressService
                    .listPublicKeyRegistrations()
                    // The deprovisioned email address's own registrations may still be listed
                    // until the service removes them.
                    .filter { it.keyRingId != deprovisioned.keyRingId }
                    .map { it.keyId }
                    .toSet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warning("Keeping the key pairs of deprovisioned email address ${deprovisioned.id}: $e")
                return
            }

        var firstError: Exception? = null
        val keyIdsNotRemoved = mutableListOf<String>()
        candidateKeyIds.filterNot { it in keyIdsInUse }.forEach { keyId ->
            try {
                serviceKeyManager.removeKeyPair(keyId)
            } catch (e: Exception) {
                logger.error(
                    "Couldn't remove key pair $keyId after deprovisioning email address ${deprovisioned.id}; " +
                        "it stays on this device: $e",
                )
                firstError = firstError ?: e
                keyIdsNotRemoved.add(keyId)
            }
        }
        firstError?.let { throw DeprovisionedKeyPairRemovalException(deprovisioned, keyIdsNotRemoved, it) }
    }
}

/**
 * The email address [deprovisioned] was deprovisioned, but this device could not remove the key
 * pairs [keyIds].
 *
 * @property deprovisioned [SealedEmailAddressEntity] The deprovisioned email address.
 * @property keyIds [List<String>] Identifiers of the key pairs that could not be removed.
 */
internal class DeprovisionedKeyPairRemovalException(
    val deprovisioned: SealedEmailAddressEntity,
    val keyIds: List<String>,
    cause: Throwable,
) : RuntimeException(cause)
