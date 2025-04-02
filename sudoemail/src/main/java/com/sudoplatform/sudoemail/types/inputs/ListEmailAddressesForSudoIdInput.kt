/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import com.sudoplatform.sudoemail.SudoEmailClient

/**
 * Input object containing a Sudo identifier and other properties used to list email addresses for a Sudo.
 *
 * @property sudoId [String] The identifier of the sudo associated with the email addresses.
 * @property limit [Int] Number of email addresses to return. If omitted the limit defaults to 10.
 * @property nextToken [String] A token generated from previous calls to [SudoEmailClient.listEmailAddressesForSudoId].
 *  This is to allow for pagination. This value should be generated from a previous
 *  pagination call, otherwise it will throw an exception. The same arguments should be
 *  supplied to this method if using a previously generated [nextToken].
 */
data class ListEmailAddressesForSudoIdInput(
    val sudoId: String,
    val limit: Int? = SudoEmailClient.DEFAULT_EMAIL_ADDRESS_LIMIT,
    val nextToken: String? = null,
)
