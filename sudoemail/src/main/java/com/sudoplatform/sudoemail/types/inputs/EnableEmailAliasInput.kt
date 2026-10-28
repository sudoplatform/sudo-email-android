package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing the properties needed to enable an email alias.
 *
 * @property emailAliasId [String] The identifier of the email alias address to be enabled.
 */
data class EnableEmailAliasInput(
    val emailAliasId: String,
)
