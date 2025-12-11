/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailAddress

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Representation of public key details associated with an email address in the Platform SDK.
 *
 * @property publicKey [String] The raw value of the public key for the email address.
 * @property keyFormat [PublicKeyFormatEntity] The format of the public key (i.e. RSA Public Key or SPKI).
 * @property algorithm [String] The algorithm to use with the public key.
 */
@Parcelize
internal data class EmailAddressPublicKeyEntity(
    val publicKey: String,
    val keyFormat: PublicKeyFormatEntity,
    val algorithm: String,
) : Parcelable
