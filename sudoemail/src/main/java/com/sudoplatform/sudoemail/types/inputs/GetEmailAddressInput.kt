/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import com.sudoplatform.sudoemail.types.CachePolicy

/**
 * Input object containing an email address identifier and cache policy used to retrieve
 * an email address.
 *
 * @property id [String] Identifier of the email address to be retrieved.
 * @property cachePolicy [CachePolicy] Determines how the data will be fetched. When using [CachePolicy.CACHE_ONLY],
 * be aware that this will only return cached results of identical API calls.
 */
data class GetEmailAddressInput(
    val id: String,
    val cachePolicy: CachePolicy = CachePolicy.REMOTE_ONLY,
)
