/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.blockedAddress.transformers

import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedAddressHashAlgorithmEntity
import com.sudoplatform.sudoemail.graphql.type.BlockedAddressHashAlgorithm as BlockedAddressHashAlgorithmGraphQL

/**
 * Transformer for converting blocked address hash algorithm types between entity and GraphQL representations.
 */
internal object BlockedAddressHashAlgorithmTransformer {
    /**
     * Transforms a hash algorithm entity to GraphQL type.
     *
     * @param entity [BlockedAddressHashAlgorithmEntity] The hash algorithm entity.
     * @return The [BlockedAddressHashAlgorithmGraphQL] type.
     */
    fun entityToGraphQL(entity: BlockedAddressHashAlgorithmEntity): BlockedAddressHashAlgorithmGraphQL =
        when (entity) {
            BlockedAddressHashAlgorithmEntity.SHA256 -> BlockedAddressHashAlgorithmGraphQL.SHA256
        }
}
