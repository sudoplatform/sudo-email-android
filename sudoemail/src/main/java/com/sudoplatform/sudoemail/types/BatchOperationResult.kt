/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

/**
 * Result type of an API that performs a batch operation.
 */
sealed class BatchOperationResult<out T> {

    /** Representation of a success or failure result from a batch operation. */
    data class SuccessOrFailureResult(val status: BatchOperationStatus) : BatchOperationResult<Nothing>()

    /** Representation of a partial result from a batch operation. */
    data class PartialResult<T>(
        val status: BatchOperationStatus = BatchOperationStatus.PARTIAL,
        val successValues: List<T>,
        val failureValues: List<T>,
    ) : BatchOperationResult<T>()
}

/**
 * Status of the [BatchOperationResult].
 */
enum class BatchOperationStatus {
    SUCCESS,
    PARTIAL,
    FAILURE
}
