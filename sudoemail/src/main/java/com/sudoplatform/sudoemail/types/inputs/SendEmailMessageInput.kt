/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import com.sudoplatform.sudoemail.types.EmailAddress

/**
 * Input object containing information required to send an email message.
 *
 * @property rfc822Data [ByteArray] Email message data formatted under the RFC-6854 (supersedes RFC 822)
 * (https://tools.ietf.org/html/rfc6854) standard. Some further rules (beyond RFC 6854) must also be
 * applied to the data:
 *  - At least one recipient must exist (to, cc, bcc).
 *  - For all email addresses:
 *    - Total length (including both local part and domain) must not exceed 256 characters.
 *    - Local part must not exceed more than 64 characters.
 *    - Input domain parts (domain separated by `.`) must not exceed 63 characters.
 *    - Address must match standard email address pattern:
 *       `^[a-zA-Z0-9](\.?[-_a-zA-Z0-9])*@[a-zA-Z0-9](-*\.?[a-zA-Z0-9])*\.[a-zA-Z](-?[a-zA-Z0-9])+$`.
 * @param senderEmailAddressId [String] Identifier of the [EmailAddress] being used to send the email. The identifier
 * must match the identifier of the address of the `from` field in the RFC 6854 data.
 */
data class SendEmailMessageInput(
    val rfc822Data: ByteArray,
    val senderEmailAddressId: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SendEmailMessageInput

        if (!rfc822Data.contentEquals(other.rfc822Data)) return false
        if (senderEmailAddressId != other.senderEmailAddressId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rfc822Data.contentHashCode()
        result = 31 * result + senderEmailAddressId.hashCode()
        return result
    }
}
