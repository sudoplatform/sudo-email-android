/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing information required to update an email address.
 *
 * @property id [String] The identifier of the email address to update.
 * @property displayName [String] An optional user defined display name for the the email address.
 *  Pass the empty string to clear this property.
 */
data class UpdateEmailAddressMetadataInput(
    val id: String,
    val displayName: String? = null,
)
