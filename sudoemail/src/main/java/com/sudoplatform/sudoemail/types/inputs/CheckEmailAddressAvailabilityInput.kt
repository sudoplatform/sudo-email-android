/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing a list of local parts and domains used to check for email
 * address availability.
 *
 * @property localParts [List<String>] The local parts of the email address to search for
 *  addresses that match this in part or whole.
 * @property domains [List<String>] Optional list of email domains in which to search for an
 *  available address. If left null, will use the default domains list recognized by the service.
 */
data class CheckEmailAddressAvailabilityInput(
    val localParts: List<String>,
    val domains: List<String>?
)
