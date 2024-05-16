/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.notifications

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Base class of Email Service notifications.
 *
 * @property type [String] Type of notification. Concrete sub-classes will
 *  specify as their SerialName the value of this property that identifies
 *  the specific notification type.
 * @property owner [String] Subject identifier of user to whom the notification
 *  is addressed.
 */
@Serializable
sealed class EmailServiceNotification {

    companion object {
        /**
         * JSON object to use for deserialisation of the notification.
         */
        private val JSON = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }

        /**
         * Decode an unsealed stringified notification to the appropriate
         * concrete EmailServiceNotification sub-class.
         */
        fun decodeFromString(unsealedNotificationString: String): EmailServiceNotification {
            return JSON.decodeFromString(unsealedNotificationString)
        }
    }

    abstract val type: String
    abstract val owner: String
}
