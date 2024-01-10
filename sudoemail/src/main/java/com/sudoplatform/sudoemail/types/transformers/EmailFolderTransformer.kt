/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.graphql.ListEmailFoldersForEmailAddressIdQuery
import com.sudoplatform.sudoemail.types.EmailFolder
import com.sudoplatform.sudoemail.types.Owner
import com.sudoplatform.sudoemail.graphql.fragment.EmailFolder as EmailFolderFragment

/**
 * Transformer responsible for transforming the [EmailFolder] GraphQL data
 * types to the entity type that is exposed to users.
 */
internal object EmailFolderTransformer {

    /**
     * Transform the results of the [ListEmailFoldersForEmailAddressIdQuery].
     *
     * @param result [List<ListEmailFoldersForEmailAddressIdQuery.Item>] The GraphQL query results.
     * @return The list of [EmailFolder]s entity type.
     */
    fun toEntity(
        result: List<ListEmailFoldersForEmailAddressIdQuery.Item>,
    ): List<EmailFolder> {
        return result.map {
            val emailFolder = it.fragments().emailFolder()
            EmailFolder(
                id = emailFolder.id(),
                owner = emailFolder.owner(),
                owners = emailFolder.owners().toOwners(),
                emailAddressId = emailFolder.emailAddressId(),
                folderName = emailFolder.folderName(),
                size = emailFolder.size(),
                unseenCount = emailFolder.unseenCount().toInt(),
                version = emailFolder.version(),
                createdAt = emailFolder.createdAtEpochMs().toDate(),
                updatedAt = emailFolder.updatedAtEpochMs().toDate(),
            )
        }
    }

    private fun List<EmailFolderFragment.Owner>.toOwners(): List<Owner> {
        return this.map {
            it.toOwner()
        }
    }

    private fun EmailFolderFragment.Owner.toOwner(): Owner {
        return Owner(id = id(), issuer = issuer())
    }
}
