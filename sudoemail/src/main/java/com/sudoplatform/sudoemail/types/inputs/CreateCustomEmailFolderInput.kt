/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing the properties needed to create a custom email folder.
 *
 * @property emailAddressId [String] The identifier of the email address associated with the email folder.
 * @property customFolderName [String] The name of the custom folder
 */
data class CreateCustomEmailFolderInput(
    val emailAddressId: String,
    val customFolderName: String,
)
