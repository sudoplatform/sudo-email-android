/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.notifications.EmailServiceNotification
import com.sudoplatform.sudoemail.notifications.SealedNotification

/**
 * Unpack and decrypt the sealed fields of an email notification [SealedNotification].
 */
internal object NotificationUnsealer {

    /**
     * Unseal and transform the email notification to [EmailServiceNotification].
     *
     * @param deviceKeyManager [DeviceKeyManager] The device key manager string the keys used to unseal.
     * @param sealedNotification [SealedNotification] The sealed email notifications.
     * @return The unsealed [EmailHeaderDetails].
     */
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
