/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.common

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Core entity representation of a sealed (encrypted) attribute.
 *
 * @property algorithm [String] The encryption algorithm used to seal the data.
 * @property keyId [String] The identifier of the key used for encryption.
 * @property plainTextType [String] The type description of the plaintext data.
 * @property base64EncodedSealedData [String] The Base64-encoded sealed (encrypted) data.
 */
@Parcelize
internal data class SealedAttributeEntity(
    val algorithm: String,
    val keyId: String,
    val plainTextType: String,
    val base64EncodedSealedData: String,
) : Parcelable
