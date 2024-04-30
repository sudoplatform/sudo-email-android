/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import java.util.Date

/**
 * A result type for an email message send operation.
 *
 * @property id [String] The unique identifier for the message.
 * @property createdAt [Date] The datetime that the message was created.
 */
data class SendEmailMessageResult(
    val id: String,
    val createdAt: Date,
)
