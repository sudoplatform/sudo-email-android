/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

import java.util.Date

/**
 * Core entity representation of the result of an email message send operation.
 *
 * @property id [String] The unique identifier for the message.
 * @property createdAt [java.util.Date] The datetime that the message was created.
 */
internal data class SendEmailMessageResultEntity(
    val id: String,
    val createdAt: Date,
)
