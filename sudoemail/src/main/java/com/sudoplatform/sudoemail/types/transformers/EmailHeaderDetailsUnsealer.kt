/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.google.gson.Gson
import com.sudoplatform.sudoemail.keys.DeviceKeyManager

/**
 * Unpack and decrypt the sealed fields of an email message header [EmailHeaderDetails].
 */
internal object EmailHeaderDetailsUnsealer {

    /**
     * Unseal and transform the email message header details to [EmailHeaderDetails].
     *
     * @param sealedRFC822HeaderString [String] The sealed email message header details.
     * @param keyId [String] The identifier of the key used to unseal the payload.
     * @param algorithm [String] The algorithm used to unseal the payload.
     * @param deviceKeyManager [DeviceKeyManager] The device key manager string the keys used to unseal.
     * @return The unsealed [EmailHeaderDetails].
     */
    fun toEmailHeaderDetails(
        sealedRFC822HeaderString: String,
        keyId: String,
        algorithm: String,
        deviceKeyManager: DeviceKeyManager,
    ): EmailHeaderDetails {
        val keyInfo = KeyInfo(
            keyId,
            KeyType.PRIVATE_KEY,
            algorithm,
        )
        val unsealer = Unsealer(deviceKeyManager, keyInfo)

        val unsealedRfc822HeaderString =
            unsealer.unseal(sealedRFC822HeaderString)

        return Gson().fromJson(unsealedRfc822HeaderString, EmailHeaderDetails::class.java)
    }
}
