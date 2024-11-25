/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing the properties needed to delete a custom email folder.
 *
 * @property emailFolderId [String] The identifier of the email folder to delete.
 * @property emailAddressId [String] The identifier of the email address associated with the folder.
 */
data class DeleteCustomEmailFolderInput(
    val emailFolderId: String,
    val emailAddressId: String,
)
