/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a cached sealed email message body.
 *
 * Exactly one of [content] or [fsPath] is non-null for any given row:
 * - [content] is set for blobs ≤ 1 MB (stored inline).
 * - [fsPath] is set for blobs > 1 MB (stored on the filesystem).
 */
@Entity(
    tableName = CacheTableConstants.CACHE_TABLE_NAME,
    indices = [
        Index(value = [CacheTableConstants.LAST_ACCESSED_AT]),
        Index(value = [CacheTableConstants.SUDO_ID]),
        Index(value = [CacheTableConstants.EMAIL_ADDRESS_ID]),
    ],
)
internal data class EmailMessageBodyCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = CacheTableConstants.MESSAGE_ID)
    val messageId: String,
    @ColumnInfo(name = CacheTableConstants.SUDO_ID)
    val sudoId: String?,
    @ColumnInfo(name = CacheTableConstants.EMAIL_ADDRESS_ID)
    val emailAddressId: String,
    @ColumnInfo(name = CacheTableConstants.CONTENT, typeAffinity = ColumnInfo.BLOB)
    val content: ByteArray?,
    @ColumnInfo(name = CacheTableConstants.FS_PATH)
    val fsPath: String?,
    @ColumnInfo(name = CacheTableConstants.CONTENT_ENCODING)
    val contentEncoding: String?,
    @ColumnInfo(name = CacheTableConstants.SIZE_BYTES)
    val sizeBytes: Long,
    @ColumnInfo(name = CacheTableConstants.LAST_ACCESSED_AT)
    val lastAccessedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmailMessageBodyCacheEntity

        if (messageId != other.messageId) return false
        if (sudoId != other.sudoId) return false
        if (emailAddressId != other.emailAddressId) return false
        if (content != null) {
            if (other.content == null) return false
            if (!content.contentEquals(other.content)) return false
        } else if (other.content != null) {
            return false
        }
        if (fsPath != other.fsPath) return false
        if (contentEncoding != other.contentEncoding) return false
        if (sizeBytes != other.sizeBytes) return false
        if (lastAccessedAt != other.lastAccessedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + (sudoId?.hashCode() ?: 0)
        result = 31 * result + emailAddressId.hashCode()
        result = 31 * result + (content?.contentHashCode() ?: 0)
        result = 31 * result + (fsPath?.hashCode() ?: 0)
        result = 31 * result + (contentEncoding?.hashCode() ?: 0)
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + lastAccessedAt.hashCode()
        return result
    }
}
