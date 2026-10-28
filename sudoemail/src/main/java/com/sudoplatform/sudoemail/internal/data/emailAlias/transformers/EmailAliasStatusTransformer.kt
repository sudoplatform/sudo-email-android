/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailAlias.transformers

import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasEntityStatus
import com.sudoplatform.sudoemail.types.EmailAliasStatus
import com.sudoplatform.sudoemail.graphql.type.EmailMaskStatus as EmailMaskStatusGraphQl

/**
 * Transformer for converting email alias status between GraphQL, entity, and API representations.
 */
internal object EmailAliasStatusTransformer {
    /**
     * Transforms a GraphQL email alias status to entity type.
     *
     * @param graphQL [EmailMaskStatusGraphQl] The GraphQL status.
     * @return [EmailAliasEntityStatus] The entity status.
     */
    fun graphQLToEntity(graphQL: EmailMaskStatusGraphQl): EmailAliasEntityStatus {
        for (value in EmailAliasEntityStatus.entries) {
            if (value.name == graphQL.name) {
                return value
            }
        }
        return EmailAliasEntityStatus.UNKNOWN
    }

    /**
     * Transforms a [EmailAliasEntityStatus] to API type.
     *
     * @param entity [EmailAliasEntityStatus] The entity state.
     * @return [EmailAliasStatus] The API state.
     */
    fun entityToApi(entity: EmailAliasEntityStatus): EmailAliasStatus {
        for (value in EmailAliasStatus.entries) {
            if (value.name == entity.name) {
                return value
            }
        }
        return EmailAliasStatus.UNKNOWN
    }

    /**
     * Transforms a [EmailAliasEntityStatus] to GraphQL type.
     *
     * @param entity [EmailAliasEntityStatus] The entity state.
     * @return [EmailMaskStatusGraphQl] The GraphQL state.
     */
    fun entityToGraphQL(entity: EmailAliasEntityStatus): EmailMaskStatusGraphQl {
        for (value in EmailMaskStatusGraphQl.entries) {
            if (value.name == entity.name) {
                return value
            }
        }
        return EmailMaskStatusGraphQl.UNKNOWN__
    }

    /**
     * Transforms an API email alias status to entity type.
     *
     * @param api [EmailAliasStatus] The API status.
     * @return [EmailAliasEntityStatus] The entity status.
     */
    fun apiToEntity(api: EmailAliasStatus): EmailAliasEntityStatus {
        for (value in EmailAliasEntityStatus.entries) {
            if (value.name == api.name) {
                return value
            }
        }
        return EmailAliasEntityStatus.UNKNOWN
    }
}
