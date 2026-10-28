package com.sudoplatform.sudoemail.types.inputs

import java.util.Date

/**
 * Input object containing the properties needed to provision an email alias.
 *
 * @property aliasAddress [String] The email alias address to be provisioned in the [local-part]@[domain] format.
 * @property realAddress [String] The real email address that the alias will forward to in the [local-part]@[domain] format.
 * @property ownershipProofToken [String] The signed ownership proof of the Sudo to be associated with the provisioned email alias.
 *  The ownership proof must contain an audience of "sudoplatform".
 * @property metadata Optional metadata to associate with the email alias.
 * @property expiresAt Optional expiration date for the email alias. If not provided, the alias will not expire.
 */
data class ProvisionEmailAliasInput(
    val aliasAddress: String,
    val realAddress: String,
    val ownershipProofToken: String,
    val metadata: Map<String, String>? = null,
    val expiresAt: Date? = null,
    val keyId: String? = null,
)
