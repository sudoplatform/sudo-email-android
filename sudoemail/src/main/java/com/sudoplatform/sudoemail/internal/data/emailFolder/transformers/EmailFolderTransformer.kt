/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailFolder.transformers

import com.sudoplatform.sudoemail.internal.data.common.transformers.OwnerTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.EmailFolderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.PartialEmailFolderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.SealedEmailFolderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.UnsealedEmailFolderEntity
import com.sudoplatform.sudoemail.internal.util.toDate
import com.sudoplatform.sudoemail.types.EmailFolder
import com.sudoplatform.sudoemail.types.PartialEmailFolder
import com.sudoplatform.sudoemail.graphql.fragment.EmailFolder as EmailFolderGraphQL

/**
 * Transformer for converting email folder data between GraphQL, entity, and API representations.
 */
internal object EmailFolderTransformer {
    /**
     * Transforms a GraphQL email folder to sealed entity type.
     *
     * @param graphQL [EmailFolderGraphQL] The GraphQL email folder.
     * @return [SealedEmailFolderEntity] The sealed entity.
     */
    fun graphQLToSealedEntity(graphQL: EmailFolderGraphQL): SealedEmailFolderEntity =
        SealedEmailFolderEntity(
            id = graphQL.id,
            owner = graphQL.owner,
            owners = graphQL.owners.map { OwnerTransformer.graphQLToEntity(it) },
            emailAddressId = graphQL.emailAddressId,
            folderName = graphQL.folderName,
            size = graphQL.size,
            unseenCount = graphQL.unseenCount.toInt(),
            version = graphQL.version,
            createdAt = graphQL.createdAtEpochMs.toDate(),
            updatedAt = graphQL.updatedAtEpochMs.toDate(),
            sealedCustomFolderName =
                graphQL.customFolderName?.let { sealedCustomFolderNameGraphQL ->
                    SealedAttributeEntity(
                        algorithm = sealedCustomFolderNameGraphQL.sealedAttribute.algorithm,
                        keyId = sealedCustomFolderNameGraphQL.sealedAttribute.keyId,
                        plainTextType = sealedCustomFolderNameGraphQL.sealedAttribute.plainTextType,
                        base64EncodedSealedData = sealedCustomFolderNameGraphQL.sealedAttribute.base64EncodedSealedData,
                    )
                },
        )

    /**
     * Transforms a [SealedEmailFolderEntity] to API type.
     *
     * @param entity [SealedEmailFolderEntity] The sealed entity to transform.
     * @return [EmailFolder] The API type.
     */
    fun sealedEntityToApi(entity: SealedEmailFolderEntity): EmailFolder =
        EmailFolder(
            id = entity.id,
            owner = entity.owner,
            owners = entity.owners.map { OwnerTransformer.entityToApi(it) },
            emailAddressId = entity.emailAddressId,
            folderName = entity.folderName,
            size = entity.size,
            unseenCount = entity.unseenCount,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            customFolderName = null,
        )

    /**
     * Transforms an [UnsealedEmailFolderEntity] to API type.
     *
     * @param entity [UnsealedEmailFolderEntity] The unsealed entity to transform.
     * @return [EmailFolder] The API type.
     */
    fun unsealedEntityToApi(entity: UnsealedEmailFolderEntity): EmailFolder =
        EmailFolder(
            id = entity.id,
            owner = entity.owner,
            owners = entity.owners.map { OwnerTransformer.entityToApi(it) },
            emailAddressId = entity.emailAddressId,
            folderName = entity.folderName,
            size = entity.size,
            unseenCount = entity.unseenCount,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            customFolderName = entity.customFolderName,
        )

    /**
     * Transforms a [PartialEmailFolderEntity] to API type.
     *
     * @param entity [PartialEmailFolderEntity] The partial entity to transform.
     * @return [PartialEmailFolder] The API type.
     */
    fun partialEntityToApi(entity: PartialEmailFolderEntity): PartialEmailFolder =
        PartialEmailFolder(
            id = entity.id,
            owner = entity.owner,
            owners = entity.owners.map { OwnerTransformer.entityToApi(it) },
            emailAddressId = entity.emailAddressId,
            folderName = entity.folderName,
            size = entity.size,
            unseenCount = entity.unseenCount,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    /**
     * Transforms an [EmailFolderEntity] to unsealed entity type.
     *
     * @param entity [EmailFolderEntity] The entity to transform.
     * @param customFolderName [String] Optional custom folder name.
     * @return [UnsealedEmailFolderEntity] The unsealed entity.
     */
    fun toUnsealedEntity(
        entity: EmailFolderEntity,
        customFolderName: String? = null,
    ): UnsealedEmailFolderEntity =
        UnsealedEmailFolderEntity(
            id = entity.id,
            owner = entity.owner,
            owners = entity.owners,
            emailAddressId = entity.emailAddressId,
            folderName = entity.folderName,
            size = entity.size,
            unseenCount = entity.unseenCount,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            customFolderName = customFolderName,
        )

    /**
     * Transforms a [SealedEmailFolderEntity] to partial entity type.
     *
     * @param entity [SealedEmailFolderEntity] The sealed entity to transform.
     * @return [PartialEmailFolderEntity] The partial entity.
     */
    fun sealedEntityToPartialEntity(entity: SealedEmailFolderEntity): PartialEmailFolderEntity =
        PartialEmailFolderEntity(
            id = entity.id,
            owner = entity.owner,
            owners = entity.owners,
            emailAddressId = entity.emailAddressId,
            folderName = entity.folderName,
            size = entity.size,
            unseenCount = entity.unseenCount,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
