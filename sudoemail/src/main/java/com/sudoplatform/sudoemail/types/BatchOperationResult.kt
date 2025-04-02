/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

/**
 * Result type of an API that performs a batch operation.
 */
data class BatchOperationResult<S, F>(
    val status: BatchOperationStatus,
    var successValues: List<S>? = null,
    val failureValues: List<F>? = null,
) {
    companion object {
        /**
         * Creates a [BatchOperationResult] where the [successValues] and [failureValues] are
         * of the same type [S].
         */
        fun <S> createSame(
            status: BatchOperationStatus,
            successValues: List<S>? = null,
            failureValues: List<S>? = null,
        ): BatchOperationResult<S, S> {
            return BatchOperationResult(status, successValues, failureValues)
        }

        /**
         * Creates a [BatchOperationResult] where the [successValues] and [failureValues] can
         * be of different types ([S] and [F], respectively).
         */
        fun <S, F> createDifferent(
            status: BatchOperationStatus,
            successValues: List<S>? = null,
            failureValues: List<F>? = null,
        ): BatchOperationResult<S, F> {
            return BatchOperationResult(status, successValues, failureValues)
        }
    }
}

/**
 * Status of the [BatchOperationResult].
 */
enum class BatchOperationStatus {
    SUCCESS,
    PARTIAL,
    FAILURE,
}

/**
 * Representation of the result of an unsuccessful operation on an email
 * message in the Sudo Platform Email SDK.
 *
 * @property id [String] The unique identifier of the message.
 * @property errorType [String] A description of the error that cause the
 * message to not be updated.
 */
data class EmailMessageOperationFailureResult(
    val id: String,
    val errorType: String,
)
