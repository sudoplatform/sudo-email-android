/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
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
 * @property publicKey [String] The raw value of the public key for the email address.
 */
@Parcelize
data class EmailAddressPublicInfo(
    val emailAddress: String,
    val keyId: String,
    val publicKey: String,
) : Parcelable
