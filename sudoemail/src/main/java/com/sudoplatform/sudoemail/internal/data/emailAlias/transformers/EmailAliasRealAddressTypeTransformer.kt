/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailAlias.transformers

import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasEntityRealAddressType
import com.sudoplatform.sudoemail.types.EmailAliasRealAddressType
import com.sudoplatform.sudoemail.graphql.type.EmailMaskRealAddressType as EmailMaskRealAddressTypeGraphQl

/**
 * Transformer for converting email alias real address type between GraphQL, entity, and API representations.
 */
internal object EmailAliasRealAddressTypeTransformer {
    /**
     * Transforms a GraphQL real address type to entity type.
     *
     * @param graphQL [EmailMaskRealAddressTypeGraphQl] The GraphQL real address type.
     * @return [EmailAliasEntityRealAddressType] The entity real address type.
     */
    fun graphQLToEntity(graphQL: EmailMaskRealAddressTypeGraphQl): EmailAliasEntityRealAddressType =
        when (graphQL) {
            EmailMaskRealAddressTypeGraphQl.INTERNAL -> EmailAliasEntityRealAddressType.INTERNAL
            EmailMaskRealAddressTypeGraphQl.EXTERNAL -> EmailAliasEntityRealAddressType.EXTERNAL
            else -> EmailAliasEntityRealAddressType.UNKNOWN
        }

    /**
     * Transforms a [EmailAliasEntityRealAddressType] to API type.
     *
     * @param entity [EmailAliasEntityRealAddressType] The entity real address type.
     * @return [EmailAliasRealAddressType] The API real address type.
     */
    fun entityToApi(entity: EmailAliasEntityRealAddressType): EmailAliasRealAddressType =
        when (entity) {
            EmailAliasEntityRealAddressType.INTERNAL -> EmailAliasRealAddressType.INTERNAL
            EmailAliasEntityRealAddressType.EXTERNAL -> EmailAliasRealAddressType.EXTERNAL
            EmailAliasEntityRealAddressType.UNKNOWN -> EmailAliasRealAddressType.UNKNOWN
        }

    /**
     * Transforms a [EmailAliasEntityRealAddressType] to GraphQL type.
     *
     * @param entity [EmailAliasEntityRealAddressType] The entity real address type.
     * @return [EmailMaskRealAddressTypeGraphQl] The GraphQL real address type.
     */
    fun entityToGraphQL(entity: EmailAliasEntityRealAddressType): EmailMaskRealAddressTypeGraphQl =
        when (entity) {
            EmailAliasEntityRealAddressType.INTERNAL -> EmailMaskRealAddressTypeGraphQl.INTERNAL
            EmailAliasEntityRealAddressType.EXTERNAL -> EmailMaskRealAddressTypeGraphQl.EXTERNAL
            EmailAliasEntityRealAddressType.UNKNOWN -> EmailMaskRealAddressTypeGraphQl.UNKNOWN__
        }

    /**
     * Transforms an API real address type to entity type.
     *
     * @param api [EmailAliasRealAddressType] The API real address type.
     * @return [EmailAliasEntityRealAddressType] The entity real address type.
     */
    fun apiToEntity(api: EmailAliasRealAddressType): EmailAliasEntityRealAddressType =
        when (api) {
            EmailAliasRealAddressType.INTERNAL -> EmailAliasEntityRealAddressType.INTERNAL
            EmailAliasRealAddressType.EXTERNAL -> EmailAliasEntityRealAddressType.EXTERNAL
            EmailAliasRealAddressType.UNKNOWN -> EmailAliasEntityRealAddressType.UNKNOWN
        }
}
