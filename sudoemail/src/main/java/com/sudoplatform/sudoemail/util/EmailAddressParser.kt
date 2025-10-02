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

    fun validate(email: String): Boolean {
        val emailRegex = "^\\S+@\\S+\\.\\S+$".toRegex()
        return email.matches(emailRegex)
    }

    fun getDomain(email: String): String {
        val (_, domain) = email.lowercase().split('@', limit = 2)
        return domain
    }

    fun removeDisplayName(email: String): String {
        // Regex to match display name and extract the email address
        val regex = Regex("""^(.*<)?([^<>]+@[^<>]+)(>)?$""")
        val matchResult = regex.find(email.trim())
        return matchResult
            ?.groups
            ?.get(2)
            ?.value
            ?.trim() ?: email.trim()
    }
}
