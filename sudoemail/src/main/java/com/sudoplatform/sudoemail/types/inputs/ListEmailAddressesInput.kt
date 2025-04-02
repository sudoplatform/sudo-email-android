/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import com.sudoplatform.sudoemail.SudoEmailClient

/**
 * Input object containing properties used to list provisioned email addresses.
 *
 * @property limit [Int] Number of email addresses to return. If omitted the limit defaults to 10.
 * @property nextToken [String] A token generated from previous calls to [SudoEmailClient.listEmailAddresses].
 *  This is to allow for pagination. This value should be generated from a previous
 *  pagination call, otherwise it will throw an exception. The same arguments should be
 *  supplied to this method if using a previously generated [nextToken].
 */
data class ListEmailAddressesInput(
    val limit: Int? = SudoEmailClient.DEFAULT_EMAIL_ADDRESS_LIMIT,
    val nextToken: String? = null,
)
