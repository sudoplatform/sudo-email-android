/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailAddress

/**
 * Core entity representation of public key format used in the Sudo Platform Email SDK.
 */
enum class PublicKeyFormatEntity {
    /** RSA Public Key format */
    RSA_PUBLIC_KEY,

    /** Subject Public Key Info format */
    SPKI,
}
