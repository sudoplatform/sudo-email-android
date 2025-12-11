/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

/**
 * An enumeration depicting different public key formats.
 *
 * @enum PublicKeyFormat
 */
enum class PublicKeyFormat {
    /** PKCS#1 RSA Public Key.*/
    RSA_PUBLIC_KEY,

    /** X.509 Subject Public Key Info.*/
    SPKI,
}
