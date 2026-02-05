package com.sudoplatform.sudoemail.types.inputs

import java.util.Date

/**
 * Input object containing the properties needed to provision an email mask.
 *
 * @property maskAddress [String] The email mask address to be provisioned in the [local-part]@[domain] format.
 * @property realAddress [String] The real email address that the mask will forward to in the [local-part]@[domain] format.
 * @property ownershipProofToken [String] The signed ownership proof of the Sudo to be associated with the provisioned email mask.
 *  The ownership proof must contain an audience of "sudoplatform".
 * @property metadata Optional metadata to associate with the email mask.
 * @property expiresAt Optional expiration date for the email mask. If not provided, the mask will not expire.
 */
data class ProvisionEmailMaskInput(
    val maskAddress: String,
    val realAddress: String,
    val ownershipProofToken: String,
    val metadata: Map<String, String>? = null,
    val expiresAt: Date? = null,
    val keyId: String? = null,
)
