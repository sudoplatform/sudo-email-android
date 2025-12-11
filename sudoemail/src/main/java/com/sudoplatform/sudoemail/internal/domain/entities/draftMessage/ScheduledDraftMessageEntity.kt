/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.draftMessage

import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import java.util.Date

/**
 * The state of a scheduled draft message.
 */
enum class ScheduledDraftMessageStateEntity {
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
 * Core entity representation of a draft message that has been scheduled to be sent in the future by
 * the Sudo Platform Email SDK
 *
 * @property id [String] The id of the draft message that has been scheduled.
 * @property emailAddressId [String] The id of the email address associated with the draft message.
 * @property owner [String] Identifier of the user that owns the email message.
 * @property owners [List<OwnerEntity>] List of identifiers of the user/sudo associated with this email message.
 * @property sendAt [Date] Timestamp of when to send the message.
 * @property state [ScheduledDraftMessageStateEntity] The current state of the scheduled message.
 * @property createdAt [Date] When the scheduled message was created.
 * @property updatedAt [Date] When the scheduled message was last updated.
 */
internal data class ScheduledDraftMessageEntity(
    val id: String,
    val emailAddressId: String,
    val owner: String,
    val owners: List<OwnerEntity>,
    val sendAt: Date,
    val state: ScheduledDraftMessageStateEntity,
    val createdAt: Date,
    val updatedAt: Date,
)

/**
 * Base interface for scheduled draft message state filter inputs.
 */
internal sealed interface ScheduledDraftMessageStateFilterInputEntity

/**
 * Filter for messages with a state equal to the specified value.
 *
 * @property equal The [ScheduledDraftMessageStateEntity] value to match.
 */
internal data class EqualStateFilterEntity(
    val equal: ScheduledDraftMessageStateEntity,
) : ScheduledDraftMessageStateFilterInputEntity

/**
 * Filter for messages with a state matching one of the specified values.
 *
 * @property oneOf The [List] of [ScheduledDraftMessageStateEntity] values to match.
 */
internal data class OneOfStateFilterEntity(
    val oneOf: List<ScheduledDraftMessageStateEntity>,
) : ScheduledDraftMessageStateFilterInputEntity

/**
 * Filter for messages with a state not equal to the specified value.
 *
 * @property notEqual The [ScheduledDraftMessageStateEntity] value to exclude.
 */
internal data class NotEqualStateFilterEntity(
    val notEqual: ScheduledDraftMessageStateEntity,
) : ScheduledDraftMessageStateFilterInputEntity

/**
 * Filter for messages with a state not matching any of the specified values.
 *
 * @property notOneOf The [List] of [ScheduledDraftMessageStateEntity] values to exclude.
 */
internal data class NotOneOfStateFilterEntity(
    val notOneOf: List<ScheduledDraftMessageStateEntity>,
) : ScheduledDraftMessageStateFilterInputEntity

/**
 * Filter input for scheduled draft messages.
 *
 * @property state [ScheduledDraftMessageStateFilterInputEntity] Optional state filter to apply.
 */
internal data class ScheduledDraftMessageFilterInputEntity(
    val state: ScheduledDraftMessageStateFilterInputEntity? = null,
)
