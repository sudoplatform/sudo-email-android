/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Representation of public information for an email address in the Sudo Platform Email SDK.
 *
 * @property emailAddress [String] The email address in format 'local-part@domain'.
 * @property keyId [String] Identifier associated with the public key.
 * @property publicKeyDetails [EmailAddressPublicKey] The public key for the email address,
 * including format and algorithm details.
 * @property enableEncryption [Boolean] Whether or not to enable encryption for the email address.
 */
@Parcelize
data class EmailAddressPublicInfo(
    val emailAddress: String,
    val keyId: String,
    val publicKeyDetails: EmailAddressPublicKey,
    val enableEncryption: Boolean,
) : Parcelable
