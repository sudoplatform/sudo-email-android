/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

object EmailAddressParser {
    fun normalize(email: String): String {
        val (localPart, domain) = email.lowercase().split('@', limit = 2)

        // If the string does not contain a @, then both localPart and domain will
        // be assigned the whole string
        return if (domain == localPart) {
            localPart
        } else {
            "$localPart@$domain"
        }
    }
}
