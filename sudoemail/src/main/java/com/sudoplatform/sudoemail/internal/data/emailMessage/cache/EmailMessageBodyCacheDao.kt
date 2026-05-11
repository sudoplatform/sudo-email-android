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
 * Room DAO for the email message body cache table.
 *
 * Note: Query strings use literal table/column names rather than [CacheTableConstants] references
 * because Room's annotation processor requires compile-time constant strings in @Query annotations
 * and does not support string interpolation or concatenation.
 */
@Dao
internal interface EmailMessageBodyCacheDao {
    @Query("SELECT * FROM email_message_body_cache WHERE message_id = :messageId")
    suspend fun getByMessageId(messageId: String): EmailMessageBodyCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EmailMessageBodyCacheEntity)

    @Query("DELETE FROM email_message_body_cache WHERE message_id = :messageId")
    suspend fun deleteByMessageId(messageId: String)

    @Query("DELETE FROM email_message_body_cache WHERE sudo_id = :sudoId")
    suspend fun deleteBySudoId(sudoId: String)

    @Query("DELETE FROM email_message_body_cache WHERE email_address_id = :emailAddressId")
    suspend fun deleteByEmailAddressId(emailAddressId: String)

    @Query("DELETE FROM email_message_body_cache")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM email_message_body_cache")
    suspend fun getTotalSizeBytes(): Long

    @Query("SELECT * FROM email_message_body_cache ORDER BY last_accessed_at ASC LIMIT :limit")
    suspend fun getLeastRecentlyAccessed(limit: Int): List<EmailMessageBodyCacheEntity>

    @Query("UPDATE email_message_body_cache SET last_accessed_at = :timestamp WHERE message_id = :messageId")
    suspend fun updateLastAccessedAt(
        messageId: String,
        timestamp: Long,
    )

    @Query("SELECT * FROM email_message_body_cache WHERE sudo_id = :sudoId")
    suspend fun getBySudoId(sudoId: String): List<EmailMessageBodyCacheEntity>

    @Query("SELECT * FROM email_message_body_cache WHERE email_address_id = :emailAddressId")
    suspend fun getByEmailAddressId(emailAddressId: String): List<EmailMessageBodyCacheEntity>
}
