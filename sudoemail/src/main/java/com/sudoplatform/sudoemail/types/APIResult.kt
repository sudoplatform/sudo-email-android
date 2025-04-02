/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

/**
 * Result type of an API that returns a list. Supports partial results.
 */
sealed class ListAPIResult<out T, out P> {

    /** Result is a success and returns a list of all results. **/
    data class Success<T>(val result: ListSuccessResult<T>) : ListAPIResult<T, Nothing>()

    /**
     * Result is partial and returns a list of mixed success and partial results and an exception
     * for each partial result indicating the failure.
     */
    data class Partial<T, P>(val result: ListPartialResult<T, P>) : ListAPIResult<T, P>()

    /**
     * Represents a successful list result.
     *
     * @property items [List<T>] Items returned from a successful list query output.
     * @property nextToken [String] Generated next token to call for the next page of paginated results.
     */
    data class ListSuccessResult<T>(
        val items: List<T>,
        val nextToken: String?,
    )

    /**
     * Represents a partial list result.
     *
     * @property items [List<T>] Items returned from a successful list query output.
     * @property failed [List<PartialResult<P>>] Items returned from a partial list query output.
     * @property nextToken [String] Generated next token to call for the next page of paginated results.
     */
    data class ListPartialResult<T, P>(
        val items: List<T>,
        val failed: List<PartialResult<P>>,
        val nextToken: String?,
    )
}

/**
 * Represents a single partial result.
 *
 * @property partial [P] The partial result.
 * @property cause [Exception] The exception indicating the failure.
 */
data class PartialResult<P>(
    val partial: P,
    val cause: Exception,
)
