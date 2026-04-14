/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
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
 * @property emailMaskId [String?] The identifier of the email mask associated with the draft email messages, if any. In order to
 *  delete a draft email message that is associated with an email mask, the `emailMaskId` must be provided and match the email mask
 *  associated with the draft email message. If the draft email message is not associated with an email mask, this property should be omitted.]
 */
data class DeleteDraftEmailMessagesInput(
    val ids: List<String>,
    val emailAddressId: String,
    val emailMaskId: String? = null,
)
