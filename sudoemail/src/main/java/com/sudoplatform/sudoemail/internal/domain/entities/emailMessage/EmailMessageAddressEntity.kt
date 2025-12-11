/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Core entity representation of an email message address used in the Sudo Platform Email SDK.
 *
 * @property emailAddress [String] Address in format 'local-part@domain' of the email.
 * @property displayName [String] The display name (or personal name) of the email address.
 */
@Parcelize
internal data class EmailMessageAddressEntity(
    val emailAddress: String,
    val displayName: String? = null,
) : Parcelable {
    override fun toString(): String {
        if (displayName?.isNotBlank() == true) {
            val escapedDisplayName =
                displayName
                    .replace(Regex("\\\\g"), "\\\\\\\\") // Escape backslashes
                    .replace(Regex("\"g"), "\\\"") // Escape double quotes
            return "\"$escapedDisplayName\" <$emailAddress>" // Wrap display name in double quotes and build email address
        } else {
            return emailAddress
        }
    }
}
