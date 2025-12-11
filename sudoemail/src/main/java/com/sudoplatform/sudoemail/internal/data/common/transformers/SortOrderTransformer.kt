/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.common.transformers

import com.sudoplatform.sudoemail.internal.domain.entities.common.SortOrderEntity
import com.sudoplatform.sudoemail.types.SortOrder
import com.sudoplatform.sudoemail.graphql.type.SortOrder as SortOrderFragment

/**
 * Transformer for converting sort order types between different representations.
 */
internal object SortOrderTransformer {
    /**
     * Transforms a sort order entity to GraphQL type.
     *
     * @param entity [SortOrderEntity] The sort order entity.
     * @return The [SortOrderFragment] type.
     */
    fun entityToGraphQL(entity: SortOrderEntity): SortOrderFragment =
        when (entity) {
            SortOrderEntity.ASC -> {
                SortOrderFragment.ASC
            }
            SortOrderEntity.DESC -> {
                SortOrderFragment.DESC
            }
        }

    /**
     * Transforms an API sort order to entity type.
     *
     * @param api [SortOrder] The API sort order.
     * @return The [SortOrderEntity].
     */
    fun apiToEntity(api: SortOrder): SortOrderEntity =
        when (api) {
            SortOrder.ASC -> {
                SortOrderEntity.ASC
            }
            SortOrder.DESC -> {
                SortOrderEntity.DESC
            }
        }
}
