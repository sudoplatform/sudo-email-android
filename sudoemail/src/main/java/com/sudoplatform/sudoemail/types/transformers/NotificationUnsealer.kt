/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.notifications.EmailServiceNotification
import com.sudoplatform.sudoemail.notifications.SealedNotification

internal object NotificationUnsealer {
    fun toNotification(
        deviceKeyManager: DeviceKeyManager,
        sealedNotification: SealedNotification,
    ): EmailServiceNotification {
        val keyInfo = KeyInfo(
            sealedNotification.keyId,
            KeyType.PRIVATE_KEY,
            sealedNotification.algorithm,
        )
        val unsealer = Unsealer(deviceKeyManager, keyInfo)

        val unsealedNotificationString =
            unsealer.unseal(sealedNotification.sealed)

        return EmailServiceNotification.decodeFromString(unsealedNotificationString)
    }
}
