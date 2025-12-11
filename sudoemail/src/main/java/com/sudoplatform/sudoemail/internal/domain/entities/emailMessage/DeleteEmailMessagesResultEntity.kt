/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

/**
 * Core entity representation of the result of an email message delete operation.
 *
 * @property successIds [List<String>] List of identifiers of email messages in which the
 *  operation completed successfully.
 * @property failureIds [List<String>] List of identifiers of email messages in which the
 *  operation failed to complete.
 */
internal data class DeleteEmailMessagesResultEntity(
    val successIds: List<String>,
    val failureIds: List<String>,
)

/**
 * Core entity representation of the result of a successful email message delete operation.
 *
 * @property id [String] The unique identifier of the email message that was deleted.
 */
internal data class DeleteEmailMessageSuccessResultEntity(
    val id: String,
)
