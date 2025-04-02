/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing an email message identifier and email address identifier used to retrieve
 * the email message data.
 *
 * @property id [String] Identifier of the email message body data to be retrieved.
 * @property emailAddressId [String] Identifier of the email address associated with the email message.
 */
data class GetEmailMessageWithBodyInput(
    val id: String,
    val emailAddressId: String,
)
