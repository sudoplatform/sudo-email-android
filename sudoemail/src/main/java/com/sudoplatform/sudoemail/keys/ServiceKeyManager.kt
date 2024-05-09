/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
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
}
