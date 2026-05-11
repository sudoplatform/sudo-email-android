/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache

/**
 * Input for [EmailMessageBodyCache.put].
 *
 * @property messageId [String] Unique identifier of the email message.
 * @property sudoId [String] Identifier of the sudo that owns the email message, if available.
 * @property emailAddressId [String] Identifier of the email address associated with the email message.
 * @property sealedBlob [ByteArray] The sealed message blob to cache.
 * @property contentEncoding [String] The S3 content-encoding header value, stored alongside the blob for decoding on cache hit.
 */
internal data class CachePutInput(
    val messageId: String,
    val sudoId: String?,
    val emailAddressId: String,
    val sealedBlob: ByteArray,
    val contentEncoding: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CachePutInput

        if (messageId != other.messageId) return false
        if (sudoId != other.sudoId) return false
        if (emailAddressId != other.emailAddressId) return false
        if (!sealedBlob.contentEquals(other.sealedBlob)) return false
        if (contentEncoding != other.contentEncoding) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + (sudoId?.hashCode() ?: 0)
        result = 31 * result + emailAddressId.hashCode()
        result = 31 * result + sealedBlob.contentHashCode()
        result = 31 * result + (contentEncoding?.hashCode() ?: 0)
        return result
    }
}
