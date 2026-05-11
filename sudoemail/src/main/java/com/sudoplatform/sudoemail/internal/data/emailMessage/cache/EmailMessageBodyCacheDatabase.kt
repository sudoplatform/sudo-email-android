/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage.cache

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for the email message body cache.
 *
 * Contains two tables:
 * - [EmailMessageBodyCacheEntity] — cached sealed message blobs
 * - [CacheSettingsEntity] — persisted key/value settings (e.g. cache size limit)
 */
@Database(
    entities = [EmailMessageBodyCacheEntity::class, CacheSettingsEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class EmailMessageBodyCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): EmailMessageBodyCacheDao

    abstract fun settingsDao(): CacheSettingsDao
}
