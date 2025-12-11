/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.common

/**
 * Core entity representation of supported symmetric key encryption algorithms.
 */
internal enum class SymmetricKeyEncryptionAlgorithmEntity(
    val algorithmName: String,
) {
    AES_CBC_PKCS7PADDING("AES/CBC/PKCS7Padding"),
}
