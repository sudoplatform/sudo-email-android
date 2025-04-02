/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing a list of email addresses used to retrieve public info for email addresses.
 *
 * @property emailAddresses [List<String>] A list of email address strings in format 'local-part@domain'.
 */
data class LookupEmailAddressesPublicInfoInput(
    val emailAddresses: List<String>,
)
