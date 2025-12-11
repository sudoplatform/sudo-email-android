/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.common

/**
 * Core entity representation of the result of a batch operation.
 *
 * @property status [BatchOperationStatusEntity] The status of the batch operation.
 * @property successValues [List<S>] List of successfully processed values.
 * @property failureValues [List<F>] List of values that failed to process.
 */
internal data class BatchOperationResultEntity<S, F>(
    val status: BatchOperationStatusEntity,
    var successValues: List<S>? = null,
    val failureValues: List<F>? = null,
)

/**
 * Status of the batch operation.
 */
internal enum class BatchOperationStatusEntity {
    SUCCESS,
    PARTIAL,
    FAILURE,
}

/**
 * Core entity representation of the result of an unsuccessful operation on an email
 * message in the Sudo Platform Email SDK.
 *
 * @property id [String] The unique identifier of the message.
 * @property errorType [String] A description of the error that cause the message to not be updated.
 */
internal data class EmailMessageOperationFailureResultEntity(
    val id: String,
    val errorType: String,
)
