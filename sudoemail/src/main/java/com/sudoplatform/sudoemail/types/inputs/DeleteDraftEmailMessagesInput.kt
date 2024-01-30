/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing an email address identifier and a list of draft email message ids
 * to be deleted.
 *
 * @property ids [List<String>] The ids of the draft email messages to be deleted.
 * @property emailAddressId [String] The id of the email address to which the drafts belong.
 */
data class DeleteDraftEmailMessagesInput(
    val ids: List<String>,
    val emailAddressId: String,
)
