/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing the properties needed to delete messages for an email folder.
 *
 * @property emailFolderId [String] The identifier of the folder to delete messages from.
 * @property emailAddressId [String] The identifier of the email address that owns the folder.
 * @property hardDelete [Boolean] (optional) If true (default), messages will be completely deleted. If false, messages will be moved to TRASH, unless the folder itself is TRASH.
 */
data class DeleteMessagesForFolderIdInput(
    val emailFolderId: String,
    val emailAddressId: String,
    val hardDelete: Boolean? = null,
)
