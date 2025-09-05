/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.types.Owner
import com.sudoplatform.sudoemail.types.ScheduledDraftMessage
import com.sudoplatform.sudoemail.graphql.fragment.ScheduledDraftMessage as ScheduledDraftMessageGraphQl

/**
 * Transformer responsible for transforming the [ScheduledDraftMessageGraphQl] data to the
 * entity type that is exposed to users.
 */
object ScheduledDraftMessageTransformer {
    fun toEntity(graphQl: ScheduledDraftMessageGraphQl): ScheduledDraftMessage =
        ScheduledDraftMessage(
            id = graphQl.draftMessageKey.substringAfterLast('/'),
            emailAddressId = graphQl.emailAddressId,
            sendAt = graphQl.sendAtEpochMs.toDate(),
            owner = graphQl.owner,
            owners = graphQl.owners.toOwners(),
            state = ScheduledDraftMessageStateTransformer.toEntity(graphQl.state),
            updatedAt = graphQl.updatedAtEpochMs.toDate(),
            createdAt = graphQl.createdAtEpochMs.toDate(),
        )

    private fun List<ScheduledDraftMessageGraphQl.Owner>.toOwners(): List<Owner> = this.map { it.toOwner() }

    private fun ScheduledDraftMessageGraphQl.Owner.toOwner(): Owner = Owner(id = id, issuer = issuer)
}
