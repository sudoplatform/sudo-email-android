/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Representation of an email domain used in the Sudo Platform Email SDK.
 *
 * @property domain [String] The domain name.
 * @property isMaskDomain [Boolean] Whether the domain is a mask domain.
 * @property metadata [Map] Metadata associated with the domain as key-value pairs.
 */
@Parcelize
data class EmailDomain(
    val domain: String,
    val isMaskDomain: Boolean,
    val metadata: Map<String, String>,
) : Parcelable
