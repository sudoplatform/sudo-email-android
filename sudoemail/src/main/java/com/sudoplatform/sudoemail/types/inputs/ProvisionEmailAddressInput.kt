/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing information required to provision an email address.
 *
 * @property emailAddress [String] The email address to provision, in the form `${localPart}@${domain}`.
 * @property ownershipProofToken [String] Proof of sudo ownership for provisioning an email address.
 *  The ownership proof must contain an audience of "sudoplatform.email.email-address".
 * @property alias [String] An optional user defined alias name for the the email address.
 */
data class ProvisionEmailAddressInput(
    val emailAddress: String,
    val ownershipProofToken: String,
    val alias: String? = null,
)
