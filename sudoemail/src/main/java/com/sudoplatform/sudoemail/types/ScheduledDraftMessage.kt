/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import java.util.Date

enum class ScheduledDraftMessageState {
    /** Scheduled to be sent. */
    SCHEDULED,

    /** Has failed to be sent. */
    FAILED,

    /** Has successfully been sent. */
    SENT,

    /** Has been cancelled. */
    CANCELLED,

    /** API Evolution - if this occurs, it may mean you need to update the library. */
    UNKNOWN,
}

/**
 * Representation of a draft message that has been scheduled to be sent in the future by
 * the Sudo Platform Email SDK
 *
 * @property id [String] The id of the draft message that has been scheduled.
 * @property emailAddressId [String] The id of the email address associated with the draft message.
 * @property owner [String] Identifier of the user that owns the email message.
 * @property owners [List<Owner>] List of identifiers of the user/sudo associated with this email message.
 * @property sendAt [Date] Timestamp of when to send the message.
 * @property state [ScheduledDraftMessageState] The current state of the scheduled message.
 * @property createdAt [Date] When the scheduled message was created.
 * @property updatedAt [Date] When the scheduled message was last updated.
 */
data class ScheduledDraftMessage(
    val id: String,
    val emailAddressId: String,
    val owner: String,
    val owners: List<Owner>,
    val sendAt: Date,
    val state: ScheduledDraftMessageState,
    val createdAt: Date,
    val updatedAt: Date,
)
