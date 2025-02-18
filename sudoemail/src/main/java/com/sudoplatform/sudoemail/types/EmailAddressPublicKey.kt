package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Representation of public key details associated with an email address in the Platform SDK.
 *
 * @property publicKey [String] The raw value of the public key for the email address.
 * @property keyFormat [PublicKeyFormat] The format of the public key (i.e. RSA Public Key or SPKI).
 * @property algorithm [String] The algorithm to use with the public key.
 */
@Parcelize
data class EmailAddressPublicKey(
    val publicKey: String,
    val keyFormat: PublicKeyFormat,
    val algorithm: String,
) : Parcelable
