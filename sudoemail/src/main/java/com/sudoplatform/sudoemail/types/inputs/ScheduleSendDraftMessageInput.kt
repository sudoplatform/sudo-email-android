/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import java.util.Date

/**
 * Input object containing the parameters needed to schedule send a draft message
 *
 * @property id [String] The id of the draft message to schedule
 * @property emailAddressId [String] The id of the email address to send the message from
 * @property sendAt [Date] Timestamp of when to send the message.
 */
data class ScheduleSendDraftMessageInput(
    val id: String,
    val emailAddressId: String,
    val sendAt: Date,
)
