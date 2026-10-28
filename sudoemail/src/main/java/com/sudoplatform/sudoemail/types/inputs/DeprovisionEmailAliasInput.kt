package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing the properties needed to deprovision an email alias.
 *
 * @property emailAliasId [String] The identifier of the email alias address to be deprovisioned.
 */
data class DeprovisionEmailAliasInput(
    val emailAliasId: String,
)
