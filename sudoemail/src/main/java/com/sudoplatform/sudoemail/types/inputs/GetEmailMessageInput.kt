/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing an email message identifier and cache policy used to retrieve
 * an email message.
 *
 * @property id [String] Identifier of the email message to be retrieved.
 */
data class GetEmailMessageInput(
    val id: String,
)
