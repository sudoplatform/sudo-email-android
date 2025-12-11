/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.common

/**
 * Type of encryption key to use.
 */
internal enum class KeyType {
    PRIVATE_KEY,
    SYMMETRIC_KEY,
}

/**
 * Information pertaining to the encryption key identifier, type and algorithm
 * to use for encrypting data.
 *
 * @property keyId [String] Identifier of the encryption key.
 * @property keyType [KeyType] Type of encryption key to use.
 * @property algorithm [String] Encryption algorithm to use.
 */
internal data class KeyInfo(
    val keyId: String,
    val keyType: KeyType,
    val algorithm: String,
)
