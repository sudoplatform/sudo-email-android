/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.draftMessage.transformers

import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageStateEntity
import com.sudoplatform.sudoemail.types.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageState as ScheduledDraftMessageStateGraphQl

/**
 * Transformer for converting scheduled draft message state between GraphQL, entity, and API representations.
 */
internal object ScheduledDraftMessageStateTransformer {
    /**
     * Transforms a GraphQL scheduled draft message state to entity type.
     *
     * @param graphQL [ScheduledDraftMessageStateGraphQl] The GraphQL state.
     * @return [ScheduledDraftMessageStateEntity] The entity state.
     */
    fun graphQLToEntity(graphQL: ScheduledDraftMessageStateGraphQl): ScheduledDraftMessageStateEntity {
        for (value in ScheduledDraftMessageStateEntity.entries) {
            if (value.name == graphQL.name) {
                return value
            }
        }
        return ScheduledDraftMessageStateEntity.UNKNOWN
    }

    /**
     * Transforms a [ScheduledDraftMessageStateEntity] to API type.
     *
     * @param entity [ScheduledDraftMessageStateEntity] The entity state.
     * @return [ScheduledDraftMessageState] The API state.
     */
    fun entityToApi(entity: ScheduledDraftMessageStateEntity): ScheduledDraftMessageState {
        for (value in ScheduledDraftMessageState.entries) {
            if (value.name == entity.name) {
                return value
            }
        }
        return ScheduledDraftMessageState.UNKNOWN
    }

    /**
     * Transforms a [ScheduledDraftMessageStateEntity] to GraphQL type.
     *
     * @param entity [ScheduledDraftMessageStateEntity] The entity state.
     * @return [ScheduledDraftMessageStateGraphQl] The GraphQL state.
     */
    fun entityToGraphQL(entity: ScheduledDraftMessageStateEntity): ScheduledDraftMessageStateGraphQl {
        for (value in ScheduledDraftMessageStateGraphQl.entries) {
            if (value.name == entity.name) {
                return value
            }
        }
        return ScheduledDraftMessageStateGraphQl.UNKNOWN__
    }

    /**
     * Transforms an API scheduled draft message state to entity type.
     *
     * @param api [ScheduledDraftMessageState] The API state.
     * @return [ScheduledDraftMessageStateEntity] The entity state.
     */
    fun apiToEntity(api: ScheduledDraftMessageState): ScheduledDraftMessageStateEntity {
        for (value in ScheduledDraftMessageStateEntity.entries) {
            if (value.name == api.name) {
                return value
            }
        }
        return ScheduledDraftMessageStateEntity.UNKNOWN
    }
}
