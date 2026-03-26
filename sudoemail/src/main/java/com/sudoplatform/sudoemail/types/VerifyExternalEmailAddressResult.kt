/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

/**
 * Result of verifying an external email address for an email mask.
 *
 * @property isVerified [Boolean] True if the external address was successfully verified, false otherwise.
 * @property reason [String] Optional reason for verification failure. Present when verification fails.
 */
data class VerifyExternalEmailAddressResult(
    val isVerified: Boolean,
    val reason: String? = null,
)
