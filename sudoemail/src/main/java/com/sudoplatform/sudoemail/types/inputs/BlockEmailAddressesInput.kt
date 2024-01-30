/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing information required to block email addresses
 *
 * @property owner [String] The identifier for the user blocking the addresses
 * @property addresses [List<String>] The list of addresses to block
 */
data class BlockEmailAddressesInput(
    val owner: String,
    val addresses: List<String>,
)
