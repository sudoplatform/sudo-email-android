package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing the properties needed to enable an email mask.
 *
 * @property emailMaskId [String] The identifier of the email mask address to be enabled.
 */
data class EnableEmailMaskInput(
    val emailMaskId: String,
)
