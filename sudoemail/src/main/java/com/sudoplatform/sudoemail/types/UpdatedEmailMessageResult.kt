/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import java.util.Date

/**
 * Representation of the result of an update to an email message in
 * the Sudo Platform Email SDK.
 *
 * Consists of two subclasses. One for success and one for failure.
 */
sealed class UpdatedEmailMessageResult {
    /**
     * Representation of the result of a successful update to an email
     * message in the Sudo Platform Email SDK.
     *
     * @property id [String] The unique identifier of the message.
     * @property createdAt [Date] The timestamp of when the message was created.
     * @property updatedAt [Date] The timestamp of when the message was updated.
     */
    data class UpdatedEmailMessageSuccess(
        val id: String,
        val createdAt: Date,
        val updatedAt: Date,
    ) : UpdatedEmailMessageResult()

    /**
     * Representation of the result of an unsuccessful update to an email
     * message in the Sudo Platform Email SDK.
     *
     * @property id [String] The unique identifier of the message.
     * @property errorType [String] A description of the error that cause the
     * message to not be updated.
     */
    data class UpdatedEmailMessageFailure(
        val id: String,
        val errorType: String,
    ) : UpdatedEmailMessageResult()
}
