/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailAddress

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Core entity representation of public information for an email address in the Sudo Platform Email SDK.
 *
 * @property emailAddress [String] The email address in format 'local-part@domain'.
 * @property keyId [String] Identifier associated with the public key.
 * @property publicKeyDetails [EmailAddressPublicKeyEntity] The public key for the email address,
 */
@Parcelize
internal data class EmailAddressPublicInfoEntity(
    val emailAddress: String,
    val keyId: String,
    val publicKeyDetails: EmailAddressPublicKeyEntity,
) : Parcelable
