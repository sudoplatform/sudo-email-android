package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing the properties needed to deprovision an email mask.
 *
 * @property emailMaskId [String] The identifier of the email mask address to be deprovisioned.
 */
data class DeprovisionEmailMaskInput(
    val emailMaskId: String,
)
