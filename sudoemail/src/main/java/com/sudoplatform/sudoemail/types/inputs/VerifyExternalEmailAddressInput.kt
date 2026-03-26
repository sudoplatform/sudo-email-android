/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing the properties needed to verify an external email address for an email mask.
 *
 * @property emailAddress [String] The external email address to verify.
 * @property emailMaskId [String] The ID of the email mask to associate with the external address.
 * @property verificationCode [String] Optional verification code. If null, triggers sending a verification email.
 *  If provided, attempts to verify the external address with the given code.
 */
data class VerifyExternalEmailAddressInput(
    val emailAddress: String,
    val emailMaskId: String,
    val verificationCode: String? = null,
)
