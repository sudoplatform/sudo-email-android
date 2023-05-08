/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

/**
 * An internal result type for an email message delete operation.
 *
 * @property successIds [List<String>] List of identifiers of email messages in which the
 *  operation completed successfully.
 * @property failureIds [List<String>] List of identifiers of email messages in which the
 *  operation failed to complete.
 */
internal data class DeleteEmailMessagesResult(
    val successIds: List<String>,
    val failureIds: List<String>
)
