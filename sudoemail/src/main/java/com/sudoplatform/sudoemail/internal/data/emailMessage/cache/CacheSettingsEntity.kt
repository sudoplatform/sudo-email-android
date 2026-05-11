/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a persisted cache setting (key/value pair).
 *
 * Currently defined keys:
 * - [CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT] — Maximum total cache size in bytes (0 = disabled).
 */
@Entity(tableName = CacheTableConstants.SETTINGS_TABLE_NAME)
internal data class CacheSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = CacheTableConstants.KEY)
    val key: String,
    @ColumnInfo(name = CacheTableConstants.VALUE)
    val value: String,
)
