/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.common

/**
 * Core entity representation of an API result that returns a list and supports partial results.
 */
internal sealed class ListAPIResultEntity<out T, out P> {
    /** Result is a success and returns a list of all results. **/
    data class Success<T>(
        val result: ListSuccessResultEntity<T>,
    ) : ListAPIResultEntity<T, Nothing>()

    /**
     * Result is partial and returns a list of mixed success and partial results and an exception
     * for each partial result indicating the failure.
     */
    data class Partial<T, P>(
        val result: ListPartialResultEntity<T, P>,
    ) : ListAPIResultEntity<T, P>()
}

/**
 * Represents a successful list result.
 *
 * @property items [List<T>] Items returned from a successful list query output.
 * @property nextToken [String] Generated next token to call for the next page of paginated results.
 */
internal data class ListSuccessResultEntity<T>(
    val items: List<T>,
    val nextToken: String?,
)

/**
 * Represents a partial list result.
 *
 * @property items [List] Items returned from a successful list query output.
 * @property failed [PartialResultEntity] Items returned from a partial list query output.
 * @property nextToken [String] Generated next token to call for the next page of paginated results.
 */
internal data class ListPartialResultEntity<T, P>(
    val items: List<T>,
    val failed: List<PartialResultEntity<P>>,
    val nextToken: String?,
)

/**
 * Represents a partial result item.
 *
 * @property partial [P] The partial result item.
 * @property cause [Exception] The exception that caused the partial result.
 */
internal data class PartialResultEntity<P>(
    val partial: P,
    val cause: Exception,
)
