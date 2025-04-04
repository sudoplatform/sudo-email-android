/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Representation of a draft email message in the Sudo Platform Email SDK.
 *
 * @implements [DraftEmailMessage]
 * @property id [String] Unique identifier of the draft message.
 * @property emailAddressId [String] Unique identifier of the email address associated with the draft
 *  email message.
 * @property updatedAt [Date] When the draft message was last updated.
 * @property rfc822Data [ByteArray] The rfc822 compliant data of the draft message.
 */
@Parcelize
data class DraftEmailMessageWithContent(
    override val id: String,
    override val emailAddressId: String,
    override val updatedAt: Date,
    val rfc822Data: ByteArray,
) : Parcelable, DraftEmailMessage {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DraftEmailMessageWithContent

        if (id != other.id) return false
        if (emailAddressId != other.emailAddressId) return false
        if (updatedAt != other.updatedAt) return false
        return rfc822Data.contentEquals(other.rfc822Data)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + emailAddressId.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + rfc822Data.contentHashCode()
        return result
    }
}
