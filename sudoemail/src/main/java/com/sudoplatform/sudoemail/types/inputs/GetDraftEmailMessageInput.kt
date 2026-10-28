/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing a draft email message id and email address id to retrieve
 * a draft email message
 *
 * @property id [String] The id to the draft email message
 * @property emailAddressId [String] The id of the email address the draft is for
 * @property emailAliasId [String?] Optional email alias ID associated with the draft.
 */
data class GetDraftEmailMessageInput(
    val id: String,
    val emailAddressId: String,
    val emailAliasId: String? = null,
)
