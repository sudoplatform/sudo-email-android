/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.notifications

import kotlinx.serialization.Serializable

/**
 * Base class of Email Service notifications that pertain to a specific
 * email address.
 *
 * @property type [String] ]Type of notification.
 * @property owner [String] Subject ID of user to whom the notification is addressed.
 * @property emailAddressId [String] ID of email address to which this notification pertains.
 */
@Serializable
sealed class EmailAddressNotification : EmailServiceNotification() {
    abstract override val type: String
    abstract override val owner: String
    abstract val emailAddressId: String
}
