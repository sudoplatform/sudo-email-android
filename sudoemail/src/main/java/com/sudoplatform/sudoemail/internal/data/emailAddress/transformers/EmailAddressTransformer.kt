/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailAddress.transformers

import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.internal.data.common.transformers.OwnerTransformer
import com.sudoplatform.sudoemail.internal.data.emailFolder.transformers.EmailFolderTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PartialEmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.SealedEmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.UnsealedEmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.UnsealedEmailFolderEntity
import com.sudoplatform.sudoemail.internal.util.toDate
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.PartialEmailAddress
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddress as EmailAddressFragment

/**
 * Transformer responsible for transforming the EmailAddress GraphQL data
 * types to the entity type that is exposed to users.
 */
internal object EmailAddressTransformer {
    /**
     * Transforms an [EmailAddressEntity] to unsealed entity type.
     *
     * @param entity [EmailAddressEntity] The entity to transform.
     * @param folders [List] of [UnsealedEmailFolderEntity] folders.
     * @param alias [String] Optional alias.
     * @return [UnsealedEmailAddressEntity] The unsealed entity.
     */
    fun toUnsealedEntity(
        entity: EmailAddressEntity,
        folders: List<UnsealedEmailFolderEntity>,
        alias: String? = null,
    ): UnsealedEmailAddressEntity =
        UnsealedEmailAddressEntity(
            id = entity.id,
            owner = entity.owner,
            owners = entity.owners,
            emailAddress = entity.emailAddress,
            size = entity.size,
            numberOfEmailMessages = entity.numberOfEmailMessages,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastReceivedAt = entity.lastReceivedAt,
            alias = alias,
            folders = folders,
        )

    /**
     * Transform the [EmailAddressFragment] GraphQL type to its entity type.
     *
     * @param emailAddress [EmailAddressFragment] The GraphQL type.
     * @return The [EmailAddress] entity type.
     */
    fun graphQLToSealedEntity(emailAddress: EmailAddressFragment): SealedEmailAddressEntity {
        val emailAddressWithoutFolders = emailAddress.emailAddressWithoutFolders
        val emailAddressWithoutFoldersEntity =
            this.graphQLToSealedEntity(
                emailAddressWithoutFolders = emailAddressWithoutFolders,
            )
        return SealedEmailAddressEntity(
            id = emailAddressWithoutFoldersEntity.id,
            owner = emailAddressWithoutFoldersEntity.owner,
            owners = emailAddressWithoutFoldersEntity.owners,
            emailAddress = emailAddressWithoutFoldersEntity.emailAddress,
            size = emailAddressWithoutFoldersEntity.size,
            numberOfEmailMessages = emailAddressWithoutFoldersEntity.numberOfEmailMessages,
            version = emailAddressWithoutFoldersEntity.version,
            createdAt = emailAddressWithoutFoldersEntity.createdAt,
            updatedAt = emailAddressWithoutFoldersEntity.updatedAt,
            lastReceivedAt = emailAddressWithoutFoldersEntity.lastReceivedAt,
            sealedAlias = emailAddressWithoutFoldersEntity.sealedAlias,
            folders = emailAddress.folders.map { EmailFolderTransformer.graphQLToSealedEntity(it.emailFolder) },
        )
    }

    /**
     * Transform the [EmailAddressWithoutFolders] GraphQL type to its entity type.
     *
     * @param emailAddressWithoutFolders [EmailAddressWithoutFolders] The GraphQL type.
     * @return The [EmailAddress] entity type.
     */
    fun graphQLToSealedEntity(emailAddressWithoutFolders: EmailAddressWithoutFolders): SealedEmailAddressEntity =
        SealedEmailAddressEntity(
            id = emailAddressWithoutFolders.id,
            owner = emailAddressWithoutFolders.owner,
            owners = emailAddressWithoutFolders.owners.map { OwnerTransformer.graphQLToEntity(it) },
            emailAddress = emailAddressWithoutFolders.emailAddress,
            size = emailAddressWithoutFolders.size,
            numberOfEmailMessages = emailAddressWithoutFolders.numberOfEmailMessages,
            version = emailAddressWithoutFolders.version,
            createdAt = emailAddressWithoutFolders.createdAtEpochMs.toDate(),
            updatedAt = emailAddressWithoutFolders.updatedAtEpochMs.toDate(),
            lastReceivedAt = emailAddressWithoutFolders.lastReceivedAtEpochMs.toDate(),
            sealedAlias = emailAddressWithoutFolders.alias?.toSealedAttributeEntity(),
            folders = emptyList(),
        )

    /**
     * Transforms a [SealedEmailAddressEntity] to API type.
     *
     * @param entity [SealedEmailAddressEntity] The sealed entity to transform.
     * @return [EmailAddress] The API type.
     */
    fun sealedEntityToApi(entity: SealedEmailAddressEntity): EmailAddress =
        EmailAddress(
            id = entity.id,
            owner = entity.owner,
            owners = entity.owners.map { OwnerTransformer.entityToApi(it) },
            emailAddress = entity.emailAddress,
            size = entity.size,
            numberOfEmailMessages = entity.numberOfEmailMessages,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastReceivedAt = entity.lastReceivedAt,
            alias = null,
            folders = entity.folders.map { EmailFolderTransformer.sealedEntityToApi(it) },
        )

    /**
     * Transforms an [UnsealedEmailAddressEntity] to API type.
     *
     * @param entity [UnsealedEmailAddressEntity] The unsealed entity to transform.
     * @return [EmailAddress] The API type.
     */
    fun unsealedEntityToApi(entity: UnsealedEmailAddressEntity): EmailAddress =
        EmailAddress(
            id = entity.id,
            owner = entity.owner,
            owners = entity.owners.map { OwnerTransformer.entityToApi(it) },
            emailAddress = entity.emailAddress,
            size = entity.size,
            numberOfEmailMessages = entity.numberOfEmailMessages,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastReceivedAt = entity.lastReceivedAt,
            alias = entity.alias,
            folders = entity.folders.map { EmailFolderTransformer.unsealedEntityToApi(it) },
        )

    /**
     * Transforms a [PartialEmailAddressEntity] to API type.
     *
     * @param entity [PartialEmailAddressEntity] The partial entity to transform.
     * @return [PartialEmailAddress] The API type.
     */
    fun partialEntityToApi(entity: PartialEmailAddressEntity): PartialEmailAddress =
        PartialEmailAddress(
            id = entity.id,
            owner = entity.owner,
            owners = entity.owners.map { OwnerTransformer.entityToApi(it) },
            emailAddress = entity.emailAddress,
            size = entity.size,
            numberOfEmailMessages = entity.numberOfEmailMessages,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastReceivedAt = entity.lastReceivedAt,
            folders = entity.folders.map { EmailFolderTransformer.partialEntityToApi(it) },
        )

    /**
     * Transforms a [SealedEmailAddressEntity] to partial entity type.
     *
     * @param entity [SealedEmailAddressEntity] The sealed entity to transform.
     * @return [PartialEmailAddressEntity] The partial entity.
     */
    fun sealedEntityToPartialEntity(entity: SealedEmailAddressEntity): PartialEmailAddressEntity =
        PartialEmailAddressEntity(
            id = entity.id,
            owner = entity.owner,
            owners = entity.owners,
            emailAddress = entity.emailAddress,
            size = entity.size,
            numberOfEmailMessages = entity.numberOfEmailMessages,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastReceivedAt = entity.lastReceivedAt,
            folders = entity.folders.map { EmailFolderTransformer.sealedEntityToPartialEntity(it) },
        )

    private fun EmailAddressWithoutFolders.Alias.toSealedAttributeEntity(): SealedAttributeEntity =
        SealedAttributeEntity(
            algorithm = sealedAttribute.algorithm,
            keyId = sealedAttribute.keyId,
            plainTextType = sealedAttribute.plainTextType,
            base64EncodedSealedData = sealedAttribute.base64EncodedSealedData,
        )
}
