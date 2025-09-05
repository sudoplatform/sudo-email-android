/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import java.security.MessageDigest
import java.util.Base64

object StringHasher {
    fun hashString(
        input: String,
        algorithm: String = "SHA-256",
    ): String {
        val hashedBytes =
            MessageDigest
                .getInstance(algorithm)
                .digest(input.toByteArray())

        return Base64.getEncoder().encodeToString(hashedBytes)
    }
}
