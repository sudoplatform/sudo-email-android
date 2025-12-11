/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Core entity representation of an email message's RFC 822 data used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email message.
 * @property rfc822Data [ByteArray] The RFC 822 formatted email message content.
 */
@Parcelize
internal data class EmailMessageRfc822DataEntity(
    val id: String,
    val rfc822Data: ByteArray,
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmailMessageRfc822DataEntity

        if (id != other.id) return false
        if (!rfc822Data.contentEquals(other.rfc822Data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + rfc822Data.contentHashCode()
        return result
    }
}
