/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailAlias.transformers

import com.sudoplatform.sudoemail.internal.data.common.transformers.OwnerTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.PartialEmailAliasEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.SealedEmailAliasEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.UnsealedEmailAliasEntity
import com.sudoplatform.sudoemail.internal.util.toDate
import com.sudoplatform.sudoemail.types.EmailAlias
import com.sudoplatform.sudoemail.types.PartialEmailAlias
import java.util.Date
import com.sudoplatform.sudoemail.graphql.fragment.EmailMask as EmailMaskFragment

/**
 * Transformer responsible for transforming the EmailMask GraphQL data
 * types to the entity type that is exposed to users.
 */
internal object EmailAliasTransformer {
    /**
     * Transforms a GraphQL EmailMask fragment to [EmailAliasEntity].
     *
     * @param fragment [EmailMaskFragment] The GraphQL fragment to transform.
     * @return [EmailAliasEntity] The transformed entity.
     */
    fun graphQLToSealedEntity(fragment: EmailMaskFragment): SealedEmailAliasEntity =
        SealedEmailAliasEntity(
            id = fragment.id,
            owner = fragment.owner,
            owners = fragment.owners.map { OwnerTransformer.graphQLToEntity(it) },
            identityId = fragment.identityId,
            aliasAddress = fragment.maskAddress,
            realAddress = fragment.realAddress,
            realAddressType = EmailAliasRealAddressTypeTransformer.graphQLToEntity(fragment.realAddressType),
            status = EmailAliasStatusTransformer.graphQLToEntity(fragment.status),
            inboundReceived = fragment.inboundReceived,
            inboundDelivered = fragment.inboundDelivered,
            outboundReceived = fragment.outboundReceived,
            outboundDelivered = fragment.outboundDelivered,
            spamCount = fragment.spamCount,
            virusCount = fragment.virusCount,
            sealedMetadata = fragment.metadata?.toSealedAttributeEntity(),
            expiresAt = fragment.expiresAtEpochSec?.let { Date(it * 1000L) },
            version = fragment.version,
            createdAt = fragment.createdAtEpochMs.toDate(),
            updatedAt = fragment.updatedAtEpochMs.toDate(),
        )

    fun unsealedEntityToApi(emailAlias: UnsealedEmailAliasEntity): EmailAlias =
        EmailAlias(
            id = emailAlias.id,
            owner = emailAlias.owner,
            owners = emailAlias.owners.map { OwnerTransformer.entityToApi(it) },
            identityId = emailAlias.identityId,
            aliasAddress = emailAlias.aliasAddress,
            realAddress = emailAlias.realAddress,
            realAddressType = EmailAliasRealAddressTypeTransformer.entityToApi(emailAlias.realAddressType),
            status = EmailAliasStatusTransformer.entityToApi(emailAlias.status),
            inboundReceived = emailAlias.inboundReceived,
            inboundDelivered = emailAlias.inboundDelivered,
            outboundReceived = emailAlias.outboundReceived,
            outboundDelivered = emailAlias.outboundDelivered,
            spamCount = emailAlias.spamCount,
            virusCount = emailAlias.virusCount,
            expiresAt = emailAlias.expiresAt,
            createdAt = emailAlias.createdAt,
            updatedAt = emailAlias.updatedAt,
            version = emailAlias.version,
            metadata = emailAlias.metadata,
        )

    private fun EmailMaskFragment.Metadata.toSealedAttributeEntity(): SealedAttributeEntity =
        SealedAttributeEntity(
            algorithm = sealedAttribute.algorithm,
            keyId = sealedAttribute.keyId,
            plainTextType = sealedAttribute.plainTextType,
            base64EncodedSealedData = sealedAttribute.base64EncodedSealedData,
        )

    /**
     * Transforms a sealed EmailAliasEntity to UnsealedEmailAliasEntity.
     *
     * @param sealedEmailAlias [EmailAliasEntity] The sealed email alias entity.
     * @param metadata [String] Optional unsealed metadata.
     * @return [UnsealedEmailAliasEntity] The unsealed email alias entity.
     */
    fun toUnsealedEntity(
        sealedEmailAlias: EmailAliasEntity,
        metadata: Map<String, String>? = null,
    ): UnsealedEmailAliasEntity =
        UnsealedEmailAliasEntity(
            id = sealedEmailAlias.id,
            owner = sealedEmailAlias.owner,
            owners = sealedEmailAlias.owners,
            identityId = sealedEmailAlias.identityId,
            aliasAddress = sealedEmailAlias.aliasAddress,
            realAddress = sealedEmailAlias.realAddress,
            realAddressType = sealedEmailAlias.realAddressType,
            status = sealedEmailAlias.status,
            inboundReceived = sealedEmailAlias.inboundReceived,
            inboundDelivered = sealedEmailAlias.inboundDelivered,
            outboundReceived = sealedEmailAlias.outboundReceived,
            outboundDelivered = sealedEmailAlias.outboundDelivered,
            spamCount = sealedEmailAlias.spamCount,
            virusCount = sealedEmailAlias.virusCount,
            expiresAt = sealedEmailAlias.expiresAt,
            version = sealedEmailAlias.version,
            createdAt = sealedEmailAlias.createdAt,
            updatedAt = sealedEmailAlias.updatedAt,
            metadata = metadata,
        )

    /**
     * Transforms a sealed EmailAliasEntity to PartialEmailAliasEntity.
     *
     * @param sealedEmailAlias [EmailAliasEntity] The sealed email alias entity.
     * @return [PartialEmailAliasEntity] The partial email alias entity.
     */
    fun toPartialEntity(sealedEmailAlias: EmailAliasEntity): PartialEmailAliasEntity =
        PartialEmailAliasEntity(
            id = sealedEmailAlias.id,
            owner = sealedEmailAlias.owner,
            owners = sealedEmailAlias.owners,
            identityId = sealedEmailAlias.identityId,
            aliasAddress = sealedEmailAlias.aliasAddress,
            realAddress = sealedEmailAlias.realAddress,
            realAddressType = sealedEmailAlias.realAddressType,
            status = sealedEmailAlias.status,
            inboundReceived = sealedEmailAlias.inboundReceived,
            inboundDelivered = sealedEmailAlias.inboundDelivered,
            outboundReceived = sealedEmailAlias.outboundReceived,
            outboundDelivered = sealedEmailAlias.outboundDelivered,
            spamCount = sealedEmailAlias.spamCount,
            virusCount = sealedEmailAlias.virusCount,
            expiresAt = sealedEmailAlias.expiresAt,
            version = sealedEmailAlias.version,
            createdAt = sealedEmailAlias.createdAt,
            updatedAt = sealedEmailAlias.updatedAt,
        )

    fun partialEntityToApi(result: PartialEmailAliasEntity): PartialEmailAlias =
        PartialEmailAlias(
            id = result.id,
            owner = result.owner,
            owners = result.owners.map { OwnerTransformer.entityToApi(it) },
            identityId = result.identityId,
            aliasAddress = result.aliasAddress,
            realAddress = result.realAddress,
            realAddressType = EmailAliasRealAddressTypeTransformer.entityToApi(result.realAddressType),
            status = EmailAliasStatusTransformer.entityToApi(result.status),
            inboundReceived = result.inboundReceived,
            inboundDelivered = result.inboundDelivered,
            outboundReceived = result.outboundReceived,
            outboundDelivered = result.outboundDelivered,
            spamCount = result.spamCount,
            virusCount = result.virusCount,
            expiresAt = result.expiresAt,
            createdAt = result.createdAt,
            updatedAt = result.updatedAt,
            version = result.version,
        )
}
