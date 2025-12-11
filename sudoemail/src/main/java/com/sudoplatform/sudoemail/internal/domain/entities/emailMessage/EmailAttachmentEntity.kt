/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Core entity representation of an email attachment used in the Sudo Platform Email SDK.
 *
 * @property fileName [String] The name of the email attachment file.
 * @property contentId [String] Identifier used to identify an attachment within an email body.
 * @property mimeType [String] The type of content that is attached.
 * @property inlineAttachment [Boolean] Flag indicating whether this is an inline attachment or not.
 * @property data [ByteArray] The email attachment data.
 */
@Parcelize
internal data class EmailAttachmentEntity(
    val fileName: String,
    val contentId: String,
    val mimeType: String,
    val inlineAttachment: Boolean,
    val data: ByteArray,
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmailAttachmentEntity

        if (fileName != other.fileName) return false
        if (contentId != other.contentId) return false
        if (mimeType != other.mimeType) return false
        if (inlineAttachment != other.inlineAttachment) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + contentId.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + inlineAttachment.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
