package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing the properties needed to disable an email alias.
 *
 * @property emailAliasId [String] The identifier of the email alias address to be disabled.
 */
data class DisableEmailAliasInput(
    val emailAliasId: String,
)
