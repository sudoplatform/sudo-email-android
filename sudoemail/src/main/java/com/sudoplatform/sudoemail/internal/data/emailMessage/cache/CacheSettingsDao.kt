/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room DAO for the cache settings table.
 *
 * Note: Query strings use literal table/column names rather than [CacheTableConstants] references
 * because Room's annotation processor requires compile-time constant strings in @Query annotations
 * and does not support string interpolation or concatenation.
 */
@Dao
internal interface CacheSettingsDao {
    @Query("SELECT * FROM email_message_cache_settings WHERE `key` = :key")
    suspend fun get(key: String): CacheSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: CacheSettingsEntity)
}
