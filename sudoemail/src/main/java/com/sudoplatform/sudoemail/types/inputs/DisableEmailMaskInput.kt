package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing the properties needed to disable an email mask.
 *
 * @property emailMaskId [String] The identifier of the email mask address to be disabled.
 */
data class DisableEmailMaskInput(
    val emailMaskId: String,
)
