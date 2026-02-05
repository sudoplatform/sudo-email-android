/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMask.transformers

import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskEntityRealAddressType
import com.sudoplatform.sudoemail.types.EmailMaskRealAddressType
import com.sudoplatform.sudoemail.graphql.type.EmailMaskRealAddressType as EmailMaskRealAddressTypeGraphQl

/**
 * Transformer for converting email mask real address type between GraphQL, entity, and API representations.
 */
internal object EmailMaskRealAddressTypeTransformer {
    /**
     * Transforms a GraphQL real address type to entity type.
     *
     * @param graphQL [EmailMaskRealAddressTypeGraphQl] The GraphQL real address type.
     * @return [EmailMaskEntityRealAddressType] The entity real address type.
     */
    fun graphQLToEntity(graphQL: EmailMaskRealAddressTypeGraphQl): EmailMaskEntityRealAddressType =
        when (graphQL) {
            EmailMaskRealAddressTypeGraphQl.INTERNAL -> EmailMaskEntityRealAddressType.INTERNAL
            EmailMaskRealAddressTypeGraphQl.EXTERNAL -> EmailMaskEntityRealAddressType.EXTERNAL
            else -> EmailMaskEntityRealAddressType.UNKNOWN
        }

    /**
     * Transforms a [EmailMaskEntityRealAddressType] to API type.
     *
     * @param entity [EmailMaskEntityRealAddressType] The entity real address type.
     * @return [EmailMaskRealAddressType] The API real address type.
     */
    fun entityToApi(entity: EmailMaskEntityRealAddressType): EmailMaskRealAddressType =
        when (entity) {
            EmailMaskEntityRealAddressType.INTERNAL -> EmailMaskRealAddressType.INTERNAL
            EmailMaskEntityRealAddressType.EXTERNAL -> EmailMaskRealAddressType.EXTERNAL
            EmailMaskEntityRealAddressType.UNKNOWN -> EmailMaskRealAddressType.UNKNOWN
        }

    /**
     * Transforms a [EmailMaskEntityRealAddressType] to GraphQL type.
     *
     * @param entity [EmailMaskEntityRealAddressType] The entity real address type.
     * @return [EmailMaskRealAddressTypeGraphQl] The GraphQL real address type.
     */
    fun entityToGraphQL(entity: EmailMaskEntityRealAddressType): EmailMaskRealAddressTypeGraphQl =
        when (entity) {
            EmailMaskEntityRealAddressType.INTERNAL -> EmailMaskRealAddressTypeGraphQl.INTERNAL
            EmailMaskEntityRealAddressType.EXTERNAL -> EmailMaskRealAddressTypeGraphQl.EXTERNAL
            EmailMaskEntityRealAddressType.UNKNOWN -> EmailMaskRealAddressTypeGraphQl.UNKNOWN__
        }

    /**
     * Transforms an API real address type to entity type.
     *
     * @param api [EmailMaskRealAddressType] The API real address type.
     * @return [EmailMaskEntityRealAddressType] The entity real address type.
     */
    fun apiToEntity(api: EmailMaskRealAddressType): EmailMaskEntityRealAddressType =
        when (api) {
            EmailMaskRealAddressType.INTERNAL -> EmailMaskEntityRealAddressType.INTERNAL
            EmailMaskRealAddressType.EXTERNAL -> EmailMaskEntityRealAddressType.EXTERNAL
            EmailMaskRealAddressType.UNKNOWN -> EmailMaskEntityRealAddressType.UNKNOWN
        }
}
