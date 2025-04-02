/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing an email address identifier used to retrieve
 * an email address.
 *
 * @property id [String] Identifier of the email address to be retrieved.
 */
data class GetEmailAddressInput(
    val id: String,
)
