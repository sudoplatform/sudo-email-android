/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import com.sudoplatform.sudoemail.types.CachePolicy

/**
 * Input object containing a list of email addresses used to retrieve public info for email addresses.
 *
 * @property emailAddresses [List<String>] A list of email address strings in format 'local-part@domain'.
 * @property cachePolicy [CachePolicy] Determines how the data will be fetched. When using [CachePolicy.CACHE_ONLY],
 *  be aware that this will only return cached results of identical API calls.
 */
data class LookupEmailAddressesPublicInfoInput(
    val emailAddresses: List<String>,
    val cachePolicy: CachePolicy = CachePolicy.REMOTE_ONLY,
)
