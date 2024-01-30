/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing information required to update an email address.
 *
 * @property id [String] The identifier of the email address to update.
 * @property alias [String] An optional user defined alias name for the the email address.
 */
data class UpdateEmailAddressMetadataInput(
    val id: String,
    val alias: String? = null,
)
