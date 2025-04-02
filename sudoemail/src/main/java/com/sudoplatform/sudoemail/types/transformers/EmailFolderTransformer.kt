/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.graphql.ListEmailFoldersForEmailAddressIdQuery
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
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
        deviceKeyManager: DeviceKeyManager,
        result: List<ListEmailFoldersForEmailAddressIdQuery.Item>,
    ): List<EmailFolder> {
        return result.map {
            val emailFolder = it.emailFolder
            EmailFolder(
                id = emailFolder.id,
                owner = emailFolder.owner,
                owners = emailFolder.owners.toOwners(),
                emailAddressId = emailFolder.emailAddressId,
                folderName = emailFolder.folderName,
                size = emailFolder.size,
                unseenCount = emailFolder.unseenCount.toInt(),
                version = emailFolder.version,
                createdAt = emailFolder.createdAtEpochMs.toDate(),
                updatedAt = emailFolder.updatedAtEpochMs.toDate(),
                customFolderName = emailFolder.customFolderName?.let { sealedName ->
                    val sealedAttribute = sealedName.sealedAttribute
                    val symmetricKeyInfo = KeyInfo(sealedAttribute.keyId, KeyType.SYMMETRIC_KEY, sealedAttribute.algorithm)
                    val folderNameUnsealer = Unsealer(deviceKeyManager, symmetricKeyInfo)
                    folderNameUnsealer.unseal(sealedName)
                },
            )
        }
    }

    fun toEntity(
        deviceKeyManager: DeviceKeyManager,
        model: EmailFolderFragment,
    ): EmailFolder {
        return EmailFolder(
            id = model.id,
            owner = model.owner,
            owners = model.owners.toOwners(),
            emailAddressId = model.emailAddressId,
            folderName = model.folderName,
            size = model.size,
            unseenCount = model.unseenCount.toInt(),
            version = model.version,
            createdAt = model.createdAtEpochMs.toDate(),
            updatedAt = model.updatedAtEpochMs.toDate(),
            customFolderName = model.customFolderName?.let {
                val sealedAttribute = it.sealedAttribute
                val symmetricKeyInfo = KeyInfo(sealedAttribute.keyId, KeyType.SYMMETRIC_KEY, sealedAttribute.algorithm)
                val folderNameUnsealer = Unsealer(deviceKeyManager, symmetricKeyInfo)
                folderNameUnsealer.unseal(it)
            },
        )
    }

    private fun List<EmailFolderFragment.Owner>.toOwners(): List<Owner> {
        return this.map {
            it.toOwner()
        }
    }

    private fun EmailFolderFragment.Owner.toOwner(): Owner {
        return Owner(id = id, issuer = issuer)
    }
}
