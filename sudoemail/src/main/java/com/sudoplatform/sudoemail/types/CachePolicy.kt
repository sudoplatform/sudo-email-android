/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.apollographql.apollo.fetcher.ResponseFetcher

/**
 * Enumeration outlining options for how data will be fetched.
 */
enum class CachePolicy {
    /**
     * Returns data from the local cache only.
     */
    CACHE_ONLY,

    /**
     * Returns data from the backend only and ignores any cached entries.
     */
    REMOTE_ONLY,
}

internal fun CachePolicy.toResponseFetcher(): ResponseFetcher {
    return when (this) {
        CachePolicy.CACHE_ONLY -> {
            AppSyncResponseFetchers.CACHE_ONLY
        }
        CachePolicy.REMOTE_ONLY -> {
            AppSyncResponseFetchers.NETWORK_ONLY
        }
    }
}
