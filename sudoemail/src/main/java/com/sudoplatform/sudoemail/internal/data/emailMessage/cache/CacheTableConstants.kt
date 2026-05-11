/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage.cache

/**
 * Constants for table and column names used in the email message body cache database.
 */
internal object CacheTableConstants {
    const val CACHE_TABLE_NAME = "email_message_body_cache"
    const val SETTINGS_TABLE_NAME = "email_message_cache_settings"

    const val MESSAGE_ID = "message_id"
    const val SUDO_ID = "sudo_id"
    const val EMAIL_ADDRESS_ID = "email_address_id"
    const val CONTENT = "content"
    const val FS_PATH = "fs_path"
    const val CONTENT_ENCODING = "content_encoding"
    const val SIZE_BYTES = "size_bytes"
    const val LAST_ACCESSED_AT = "last_accessed_at"

    const val KEY = "key"
    const val VALUE = "value"

    const val SETTINGS_KEY_CACHE_SIZE_LIMIT = "cache_size_limit_bytes"
}
