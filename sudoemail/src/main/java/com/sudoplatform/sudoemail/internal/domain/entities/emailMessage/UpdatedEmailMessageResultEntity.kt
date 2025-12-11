/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

import java.util.Date

/**
 * Core entity representation of the result of an update to an email message.
 *
 * Consists of two subclasses. One for success and one for failure.
 */
sealed class UpdatedEmailMessageResultEntity {
    /**
     * Core entity representation of the result of a successful update to an email message.
     *
     * @property id [String] The unique identifier of the message.
     * @property createdAt [java.util.Date] The timestamp of when the message was created.
     * @property updatedAt [java.util.Date] The timestamp of when the message was updated.
     */
    data class UpdatedEmailMessageSuccessEntity(
        val id: String,
        val createdAt: Date,
        val updatedAt: Date,
    ) : UpdatedEmailMessageResultEntity()

    /**
     * Core entity representation of the result of an unsuccessful update to an email message.
     *
     * @property id [String] The unique identifier of the message.
     * @property errorType [String] A description of the error that cause the message to not be updated.
     */
    data class UpdatedEmailMessageFailureEntity(
        val id: String,
        val errorType: String,
    ) : UpdatedEmailMessageResultEntity()
}
