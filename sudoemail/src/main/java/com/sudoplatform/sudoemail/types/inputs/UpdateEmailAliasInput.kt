package com.sudoplatform.sudoemail.types.inputs

import java.util.Date

/**
 * Input object containing the properties needed to update an email alias.
 *
 * @property emailAliasId [String] The identifier of the email alias address to be updated.
 * @property metadata Optional metadata to associate with the email alias. If the empty map is provided, all existing metadata will be removed.
 * @property expiresAt Optional expiration date for the email alias. If expiresAt is a zero date, the expiry date is removed.
 * @property realAddress Optional real email address for the email alias. Only valid for external aliases in a pending state. Intended for the
 * use case of accidentally inputting the wrong external address.
 */
data class UpdateEmailAliasInput(
    val emailAliasId: String,
    val metadata: Map<String, String>? = null,
    val expiresAt: Date? = null,
    val realAddress: String? = null,
)
