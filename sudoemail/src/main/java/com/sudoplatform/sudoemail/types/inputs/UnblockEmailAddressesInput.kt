/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing information required to unblock email addresses
 *
 * @property owner [String] The identifier for the user unblocking the addresses
 * @property addresses [List<String>] The list of addresses to unblock
 */
data class UnblockEmailAddressesInput(
    val owner: String,
    val addresses: List<String>,
)
