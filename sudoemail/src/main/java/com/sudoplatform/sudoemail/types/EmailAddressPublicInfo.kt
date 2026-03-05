/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import com.sudoplatform.sudoemail.util.Constants.DEFAULT_PUBLIC_KEY_ALGORITHM
import kotlinx.parcelize.Parcelize

/**
 * Representation of public information for an email address in the Sudo Platform Email SDK.
 *
 * @property emailAddress [String] The email address in format 'local-part@domain'.
 * @property keyId [String] Identifier associated with the public key.
 * @property publicKey [String] The raw value of the public key for the email address.
 * @property publicKeyDetails [EmailAddressPublicKey] The public key for the email address,
 * including format and algorithm details.
 * @property enableEncryption [Boolean] Whether or not to enable encryption for the email address.
 */
@Parcelize
data class EmailAddressPublicInfo(
    val emailAddress: String,
    val keyId: String,
    @Deprecated(
        message = "The publicKey property is deprecated. Use publicKeyDetails for more detailed information.",
        replaceWith = ReplaceWith("publicKeyDetails"),
    )
    val publicKey: String,
    val publicKeyDetails: EmailAddressPublicKey,
    val enableEncryption: Boolean,
) : Parcelable {
    /**
     * (Deprecated) Constructor for creating an instance of [EmailAddressPublicInfo].
     *
     * @param emailAddress The email address in the format 'local-part@domain'.
     * @param keyId A unique identifier associated with the public key.
     * @param publicKey (Deprecated) The raw public key value associated with the email address.
     *                  Use the constructor accepting `publicKeyDetails` instead.
     */
    @Deprecated(
        message = "This constructor is deprecated. Use EmailAddressPublicInfo(emailAddress:keyId:publicKeyDetails) instead.",
        replaceWith = ReplaceWith("EmailAddressPublicInfo(emailAddress:keyId:publicKeyDetails)"),
    )
    constructor(
        emailAddress: String,
        keyId: String,
        publicKey: String,
    ) : this(
        emailAddress = emailAddress,
        keyId = keyId,
        publicKey = publicKey,
        publicKeyDetails = EmailAddressPublicKey(publicKey, PublicKeyFormat.RSA_PUBLIC_KEY, DEFAULT_PUBLIC_KEY_ALGORITHM),
        enableEncryption = true,
    )

    /**
     * Constructor for creating an instance of [EmailAddressPublicInfo].
     *
     * @param emailAddress The email address in the format 'local-part@domain'.
     * @param keyId A unique identifier associated with the public key.
     * @param publicKeyDetails Detailed information about the public key, including its raw value, format,
     *                         and algorithm.
     * @param enableEncryption Whether or not to enable encryption for the email address.
     *
     */
    constructor(
        emailAddress: String,
        keyId: String,
        publicKeyDetails: EmailAddressPublicKey,
        enableEncryption: Boolean,
    ) : this(
        emailAddress = emailAddress,
        keyId = keyId,
        publicKey = publicKeyDetails.publicKey,
        publicKeyDetails = publicKeyDetails,
        enableEncryption = enableEncryption,
    )
}
