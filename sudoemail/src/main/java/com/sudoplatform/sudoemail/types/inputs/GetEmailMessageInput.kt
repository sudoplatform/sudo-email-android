/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import com.sudoplatform.sudoemail.types.CachePolicy

/**
 * Input object containing an email message identifier and cache policy used to retrieve
 * an email message.
 *
 * @property id [String] Identifier of the email message to be retrieved.
 * @property cachePolicy [CachePolicy] Determines how the data will be fetched. When using [CachePolicy.CACHE_ONLY],
 * be aware that this will only return cached results of identical API calls.
 */
data class GetEmailMessageInput(
    val id: String,
    val cachePolicy: CachePolicy = CachePolicy.REMOTE_ONLY,
)
