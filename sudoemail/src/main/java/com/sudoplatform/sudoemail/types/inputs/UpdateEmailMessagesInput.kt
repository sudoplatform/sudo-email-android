/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object containing information required to update an email message.
 *
 * @property ids [List<String>] A list of one or more identifiers of the email messages to be updated.
 *  There is a limit of 100 email message identifiers per request. Exceeding this will cause an exception.
 *  to be thrown.
 * @property values [UpdatableValues] The new value(s) to set for each email message.
 */
data class UpdateEmailMessagesInput(
    val ids: List<String>,
    val values: UpdatableValues,
) {
    /**
     * Email message values that can be updated. If null is passed into these parameters then
     * no update is performed.
     *
     * @property folderId [String] The identifier of the email folder that the email message is assigned to.
     * @property seen [Boolean] Whether the user has previously seen the email message
     */
    data class UpdatableValues(
        val folderId: String? = null,
        val seen: Boolean? = null,
    )
}
