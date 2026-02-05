/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMask.transformers

import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskEntityStatus
import com.sudoplatform.sudoemail.types.EmailMaskStatus
import com.sudoplatform.sudoemail.graphql.type.EmailMaskStatus as EmailMaskStatusGraphQl

/**
 * Transformer for converting email mask status between GraphQL, entity, and API representations.
 */
internal object EmailMaskStatusTransformer {
    /**
     * Transforms a GraphQL email mask status to entity type.
     *
     * @param graphQL [EmailMaskStatusGraphQl] The GraphQL status.
     * @return [EmailMaskEntityStatus] The entity status.
     */
    fun graphQLToEntity(graphQL: EmailMaskStatusGraphQl): EmailMaskEntityStatus {
        for (value in EmailMaskEntityStatus.entries) {
            if (value.name == graphQL.name) {
                return value
            }
        }
        return EmailMaskEntityStatus.UNKNOWN
    }

    /**
     * Transforms a [EmailMaskEntityStatus] to API type.
     *
     * @param entity [EmailMaskEntityStatus] The entity state.
     * @return [EmailMaskStatus] The API state.
     */
    fun entityToApi(entity: EmailMaskEntityStatus): EmailMaskStatus {
        for (value in EmailMaskStatus.entries) {
            if (value.name == entity.name) {
                return value
            }
        }
        return EmailMaskStatus.UNKNOWN
    }

    /**
     * Transforms a [EmailMaskEntityStatus] to GraphQL type.
     *
     * @param entity [EmailMaskEntityStatus] The entity state.
     * @return [EmailMaskStatusGraphQl] The GraphQL state.
     */
    fun entityToGraphQL(entity: EmailMaskEntityStatus): EmailMaskStatusGraphQl {
        for (value in EmailMaskStatusGraphQl.entries) {
            if (value.name == entity.name) {
                return value
            }
        }
        return EmailMaskStatusGraphQl.UNKNOWN__
    }

    /**
     * Transforms an API email mask status to entity type.
     *
     * @param api [EmailMaskStatus] The API status.
     * @return [EmailMaskEntityStatus] The entity status.
     */
    fun apiToEntity(api: EmailMaskStatus): EmailMaskEntityStatus {
        for (value in EmailMaskEntityStatus.entries) {
            if (value.name == api.name) {
                return value
            }
        }
        return EmailMaskEntityStatus.UNKNOWN
    }
}
