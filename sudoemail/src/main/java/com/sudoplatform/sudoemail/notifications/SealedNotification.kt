/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.notifications

import kotlinx.serialization.Serializable

/**
 * Sealed Email Service Notification.
 *
 * Unseal with
 * [com.sudoplatform.sudoemail.types.transformers.NotificationUnsealer]
 * to obtain an [EmailServiceNotification]
 *
 * @property keyId [String] ID of key used to seal the payload
 * @property algorithm [String] Algorithm used to seal the payload
 * @property sealed [String] The sealed stringified JSON payload
 */
@Serializable
internal data class SealedNotification(
    val keyId: String,
    val algorithm: String,
    val sealed: String,
)
