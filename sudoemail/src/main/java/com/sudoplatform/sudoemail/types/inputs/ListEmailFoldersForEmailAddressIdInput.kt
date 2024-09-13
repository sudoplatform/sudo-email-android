/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import com.sudoplatform.sudoemail.SudoEmailClient

/**
 * Input object containing an email address identifier and other properties used to list
 * email folders for an email address.
 *
 * @property emailAddressId [String] The identifier of the email address associated with the email folders.
 * @property limit [Int] Number of email folders to return. If omitted the limit defaults to 10.
 * @property nextToken [String] A token generated from previous calls to [SudoEmailClient.listEmailFoldersForEmailAddressId].
 *  This is to allow for pagination. This value should be generated from a previous
 *  pagination call, otherwise it will throw an exception. The same arguments should be
 *  supplied to this method if using a previously generated [nextToken].
 */
data class ListEmailFoldersForEmailAddressIdInput(
    val emailAddressId: String,
    val limit: Int? = SudoEmailClient.DEFAULT_EMAIL_FOLDER_LIMIT,
    val nextToken: String? = null,
)
