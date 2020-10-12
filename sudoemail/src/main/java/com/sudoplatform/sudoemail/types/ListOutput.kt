/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

/**
 * Representation of a generic type to wrap around a GraphQL list type. This is useful for
 * exposing a list of [items] and [nextToken] to allow for pagination by calling for the next
 * set of paginated results.
 *
 * @property items Items returned from a list query output.
 * @property nextToken Generated next token to call for the next page of paginated results.
 *
 * @since 2020-08-05
 */
data class ListOutput<T> (
    val items: List<T>,
    val nextToken: String?
)
