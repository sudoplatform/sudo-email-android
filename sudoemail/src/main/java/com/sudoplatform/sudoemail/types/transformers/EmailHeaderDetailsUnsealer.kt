/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.google.gson.Gson
import com.sudoplatform.sudoemail.keys.DeviceKeyManager

internal object EmailHeaderDetailsUnsealer {
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
