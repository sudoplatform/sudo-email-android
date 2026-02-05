package com.sudoplatform.sudoemail.types.inputs

import java.util.Date

/**
 * Input object containing the properties needed to update an email mask.
 *
 * @property emailMaskId [String] The identifier of the email mask address to be updated.
 * @property metadata Optional metadata to associate with the email mask. If the empty map is provided, all existing metadata will be removed.
 * @property expiresAt Optional expiration date for the email mask. If expiresAt is a zero date, the expiry date is removed.
 */
data class UpdateEmailMaskInput(
    val emailMaskId: String,
    val metadata: Map<String, String>? = null,
    val expiresAt: Date? = null,
)
