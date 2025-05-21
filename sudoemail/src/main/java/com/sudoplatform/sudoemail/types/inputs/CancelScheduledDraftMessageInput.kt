/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing the parameters needed to cancel a scheduled draft message
 *
 * @property id [String] The id of the scheduled draft message to cancel
 * @property emailAddressId [String] The id of the email address that owns the scheduled draft
 */
data class CancelScheduledDraftMessageInput(
    val id: String,
    val emailAddressId: String,
)
