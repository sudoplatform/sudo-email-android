/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

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
