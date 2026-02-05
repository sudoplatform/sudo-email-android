/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.common.transformers

import com.sudoplatform.sudoemail.graphql.fragment.BlockedAddress
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.fragment.EmailFolder
import com.sudoplatform.sudoemail.graphql.fragment.EmailMask
import com.sudoplatform.sudoemail.graphql.fragment.ScheduledDraftMessage
import com.sudoplatform.sudoemail.graphql.fragment.SealedEmailMessage
import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import com.sudoplatform.sudoemail.types.Owner

/**
 * Transformer for converting owner data between GraphQL, entity, and API representations.
 */
internal object OwnerTransformer {
    /**
     * Transforms a GraphQL email folder owner to entity type.
     *
     * @param graphQL [EmailFolder.Owner] The GraphQL owner from an email folder.
     * @return The [OwnerEntity].
     */
    fun graphQLToEntity(graphQL: EmailFolder.Owner): OwnerEntity =
        OwnerEntity(
            id = graphQL.id,
            issuer = graphQL.issuer,
        )

    /**
     * Transforms a GraphQL email address owner to entity type.
     *
     * @param graphQL [EmailAddressWithoutFolders.Owner] The GraphQL owner from an email address.
     * @return The [OwnerEntity].
     */
    fun graphQLToEntity(graphQL: EmailAddressWithoutFolders.Owner): OwnerEntity =
        OwnerEntity(
            id = graphQL.id,
            issuer = graphQL.issuer,
        )

    /**
     * Transforms a GraphQL sealed email message owner to entity type.
     *
     * @param graphQL [SealedEmailMessage.Owner] The GraphQL owner from a sealed email message.
     * @return The [OwnerEntity].
     */
    fun graphQLToEntity(graphQL: SealedEmailMessage.Owner): OwnerEntity =
        OwnerEntity(
            id = graphQL.id,
            issuer = graphQL.issuer,
        )

    /**
     * Transforms a GraphQL scheduled draft message owner to entity type.
     *
     * @param graphQL [ScheduledDraftMessage.Owner] The GraphQL owner from a scheduled draft message.
     * @return The [OwnerEntity].
     */
    fun graphQLToEntity(graphQL: ScheduledDraftMessage.Owner): OwnerEntity =
        OwnerEntity(
            id = graphQL.id,
            issuer = graphQL.issuer,
        )

    /**
     * Transforms a GraphQL blocked address owner to entity type.
     *
     * @param graphQL [BlockedAddress.Owner] The GraphQL owner from a blocked address.
     * @return The [OwnerEntity].
     */
    fun graphQLToEntity(graphQL: BlockedAddress.Owner): OwnerEntity =
        graphQL.let {
            OwnerEntity(
                id = it.id,
                issuer = it.issuer,
            )
        }

    /**
     * Transforms a GraphQL email mask owner to entity type.
     *
     * @param graphQL [EmailMask.Owner] The GraphQL owner from an email mask.
     * @return The [OwnerEntity].
     */
    fun graphQLToEntity(graphQL: EmailMask.Owner): OwnerEntity =
        OwnerEntity(
            id = graphQL.id,
            issuer = graphQL.issuer,
        )

    /**
     * Transforms an owner entity to API type.
     *
     * @param entity [OwnerEntity] The owner entity.
     * @return The [Owner].
     */
    fun entityToApi(entity: OwnerEntity): Owner =
        Owner(
            id = entity.id,
            issuer = entity.issuer,
        )
}
