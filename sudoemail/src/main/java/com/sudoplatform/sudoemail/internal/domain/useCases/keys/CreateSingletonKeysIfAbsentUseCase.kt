/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.keys

import com.sudoplatform.sudoemail.SudoEmailClient.EmailCryptographicKeysException
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.keys.SingletonKeyPairResult
import com.sudoplatform.sudologging.Logger

/**
 * Application business logic for creating the singleton key pair if this device does not have
 * it.
 *
 * @property serviceKeyManager [ServiceKeyManager] Reads and creates the singleton key pair.
 * @property enforceSingletonKeyPair [Boolean] Whether every email address and email alias shares
 *  one key pair.
 * @property logger [Logger] The SDK logger.
 */
internal class CreateSingletonKeysIfAbsentUseCase(
    private val serviceKeyManager: ServiceKeyManager,
    private val enforceSingletonKeyPair: Boolean,
    private val logger: Logger,
) {
    /**
     * Create the singleton key pair if this device does not have it.
     *
     * @return The singleton key pair, and whether it had to be created.
     * @throws EmailCryptographicKeysException.InvalidConfigurationException if the client does not
     *  enforce a singleton key pair, since each email address and email alias then gets its own
     *  key pair when it is provisioned.
     */
    fun execute(): SingletonKeyPairResult {
        logger.debug(this::class.java.simpleName)
        if (!enforceSingletonKeyPair) {
            throw EmailCryptographicKeysException.InvalidConfigurationException(
                StringConstants.SINGLETON_KEY_PAIR_NOT_ENFORCED_MSG,
            )
        }
        return serviceKeyManager.createSingletonKeyPairIfAbsent()
    }
}
