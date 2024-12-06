/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing information required to update a custom email folder.
 *
 * @property emailAddressId [String] The identifier of the email address associated with
 *  email folder
 * @property emailFolderId [String] The identifier of the email folder
 * @property customFolderName [String] The new name to assign to the custom email folder
 */
data class UpdateCustomEmailFolderInput(
    val emailAddressId: String,
    val emailFolderId: String,
    val customFolderName: String? = null,
)
