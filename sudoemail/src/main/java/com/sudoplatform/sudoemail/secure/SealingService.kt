/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.secure

import com.sudoplatform.sudoemail.keys.DeviceKeyManager

/**
 * Responsible for performing sealing operations on data in the email service.
 */
internal interface SealingService {

    /**
     * Seals the [payload] with the key [keyId].
     *
     * @param keyId [String] Identifier of the key used to seal the data.
     * @param payload [ByteArray] The payload of the message to seal.
     * @return The sealed data as a [ByteArray].
     */
    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    fun sealString(keyId: String, payload: ByteArray): ByteArray

    /**
     * Unseals the [payload] with the key [keyId].
     *
     * @param keyId [String] Identifier of the key used to unseal the data.
     * @param payload [ByteArray] The payload of the message to unseal.
     * @return The unsealed data as a [ByteArray].
     */
    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    fun unsealString(keyId: String, payload: ByteArray): ByteArray
}
