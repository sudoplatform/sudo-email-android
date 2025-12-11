/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.blockedAddress.transformers

import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedEmailAddressActionEntity
import com.sudoplatform.sudoemail.types.BlockedEmailAddressAction
import com.sudoplatform.sudoemail.graphql.type.BlockedAddressAction as BlockedAddressActionGraphQL

/**
 * Transformer for converting blocked address action types between different representations.
 */
internal object BlockedAddressActionTransformer {
    /**
     * Transforms a blocked address action entity to GraphQL type.
     *
     * @param entity [BlockedEmailAddressActionEntity] The blocked address action entity.
     * @return The [BlockedAddressActionGraphQL].
     */
    fun entityToGraphQL(entity: BlockedEmailAddressActionEntity): BlockedAddressActionGraphQL =
        when (entity) {
            BlockedEmailAddressActionEntity.DROP -> BlockedAddressActionGraphQL.DROP
            BlockedEmailAddressActionEntity.SPAM -> BlockedAddressActionGraphQL.SPAM
        }

    /**
     * Transforms a GraphQL blocked address action to entity type.
     *
     * @param graphQL [BlockedAddressActionGraphQL] The GraphQL blocked address action. Defaults to DROP if null.
     * @return The [BlockedEmailAddressActionEntity].
     */
    fun graphQLToEntity(graphQL: BlockedAddressActionGraphQL?): BlockedEmailAddressActionEntity =
        when (graphQL) {
            BlockedAddressActionGraphQL.SPAM -> BlockedEmailAddressActionEntity.SPAM
            else -> BlockedEmailAddressActionEntity.DROP
        }

    /**
     * Transforms an API blocked address action to entity type.
     *
     * @param api [BlockedEmailAddressAction] The API blocked address action.
     * @return The [BlockedEmailAddressActionEntity].
     */
    fun apiToEntity(api: BlockedEmailAddressAction): BlockedEmailAddressActionEntity =
        when (api) {
            BlockedEmailAddressAction.DROP -> BlockedEmailAddressActionEntity.DROP
            BlockedEmailAddressAction.SPAM -> BlockedEmailAddressActionEntity.SPAM
        }

    /**
     * Transforms a blocked address action entity to API type.
     *
     * @param entity [BlockedEmailAddressActionEntity] The blocked address action entity.
     * @return The [BlockedEmailAddressAction].
     */
    fun entityToApi(entity: BlockedEmailAddressActionEntity): BlockedEmailAddressAction =
        when (entity) {
            BlockedEmailAddressActionEntity.DROP -> BlockedEmailAddressAction.DROP
            BlockedEmailAddressActionEntity.SPAM -> BlockedEmailAddressAction.SPAM
        }
}
