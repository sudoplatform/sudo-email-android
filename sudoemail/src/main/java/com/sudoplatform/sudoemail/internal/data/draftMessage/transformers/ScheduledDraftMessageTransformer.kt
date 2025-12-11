/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.draftMessage.transformers

import com.sudoplatform.sudoemail.internal.data.common.transformers.OwnerTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageEntity
import com.sudoplatform.sudoemail.internal.util.toDate
import com.sudoplatform.sudoemail.types.ScheduledDraftMessage
import com.sudoplatform.sudoemail.graphql.fragment.ScheduledDraftMessage as ScheduledDraftMessageGraphQl

/**
 * Transformer for converting scheduled draft message data between GraphQL, entity, and API representations.
 */
internal object ScheduledDraftMessageTransformer {
    /**
     * Transforms a GraphQL scheduled draft message to entity type.
     *
     * @param graphQL [ScheduledDraftMessageGraphQl] The GraphQL scheduled draft message.
     * @return [ScheduledDraftMessageEntity] The entity.
     */
    fun graphQLToEntity(graphQL: ScheduledDraftMessageGraphQl): ScheduledDraftMessageEntity =
        ScheduledDraftMessageEntity(
            id = graphQL.draftMessageKey.substringAfterLast('/'),
            emailAddressId = graphQL.emailAddressId,
            sendAt = graphQL.sendAtEpochMs.toDate(),
            owner = graphQL.owner,
            owners = graphQL.owners.map { OwnerTransformer.graphQLToEntity(it) },
            state = ScheduledDraftMessageStateTransformer.graphQLToEntity(graphQL.state),
            updatedAt = graphQL.updatedAtEpochMs.toDate(),
            createdAt = graphQL.createdAtEpochMs.toDate(),
        )

    /**
     * Transforms a [ScheduledDraftMessageEntity] to API type.
     *
     * @param entity [ScheduledDraftMessageEntity] The entity to transform.
     * @return [ScheduledDraftMessage] The API type.
     */
    fun entityToApi(entity: ScheduledDraftMessageEntity): ScheduledDraftMessage =
        ScheduledDraftMessage(
            id = entity.id,
            emailAddressId = entity.emailAddressId,
            sendAt = entity.sendAt,
            owner = entity.owner,
            owners = entity.owners.map { OwnerTransformer.entityToApi(it) },
            state = ScheduledDraftMessageStateTransformer.entityToApi(entity.state),
            updatedAt = entity.updatedAt,
            createdAt = entity.createdAt,
        )
}
