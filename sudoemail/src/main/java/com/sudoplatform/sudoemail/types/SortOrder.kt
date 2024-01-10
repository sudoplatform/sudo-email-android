/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import com.sudoplatform.sudoemail.graphql.type.SortOrder as SortOrderInput

/**
 * An enumeration depicting sort order in the Sudo Platform Email SDK.
 *
 * @enum SortOrder
 */
enum class SortOrder {
    /**
     * Sort the list of results in ascending order.
     */
    ASC,

    /**
     * Sort the list of results in descending order.
     */
    DESC,

    ;

    fun toSortOrderInput(sortOrder: SortOrder): SortOrderInput {
        return when (sortOrder) {
            ASC -> {
                SortOrderInput.ASC
            }
            DESC -> {
                SortOrderInput.DESC
            }
        }
    }
}
