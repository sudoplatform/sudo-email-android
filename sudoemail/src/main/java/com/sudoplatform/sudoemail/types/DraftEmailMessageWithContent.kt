/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import com.sudoplatform.sudoemail.types.inputs.SendEmailMessageInput
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Representation of a draft email message in the Sudo Platform Email SDK.
 *
 * @implements [DraftEmailMessage]
 * @property id [String] Unique identifier of the draft message.
 * @property updatedAt [Date] When the draft message was last updated.
 * @property rfc822Data [ByteArray] The rfc822 compliant data of the draft message.
 */
@Parcelize
data class DraftEmailMessageWithContent(
    override val id: String,
    override val updatedAt: Date,
    val rfc822Data: ByteArray,
) : Parcelable, DraftEmailMessage {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SendEmailMessageInput

        if (!rfc822Data.contentEquals(other.rfc822Data)) return false

        return true
    }

    override fun hashCode(): Int {
        return rfc822Data.contentHashCode()
    }
}
