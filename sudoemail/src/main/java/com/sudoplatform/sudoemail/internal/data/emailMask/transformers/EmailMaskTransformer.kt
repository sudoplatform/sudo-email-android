/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMask.transformers

import com.sudoplatform.sudoemail.internal.data.common.transformers.OwnerTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.PartialEmailMaskEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.SealedEmailMaskEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.UnsealedEmailMaskEntity
import com.sudoplatform.sudoemail.internal.util.toDate
import com.sudoplatform.sudoemail.types.EmailMask
import com.sudoplatform.sudoemail.types.PartialEmailMask
import java.util.Date
import com.sudoplatform.sudoemail.graphql.fragment.EmailMask as EmailMaskFragment

/**
 * Transformer responsible for transforming the EmailMask GraphQL data
 * types to the entity type that is exposed to users.
 */
internal object EmailMaskTransformer {
    /**
     * Transforms a GraphQL EmailMask fragment to [EmailMaskEntity].
     *
     * @param fragment [EmailMaskFragment] The GraphQL fragment to transform.
     * @return [EmailMaskEntity] The transformed entity.
     */
    fun graphQLToSealedEntity(fragment: EmailMaskFragment): SealedEmailMaskEntity =
        SealedEmailMaskEntity(
            id = fragment.id,
            owner = fragment.owner,
            owners = fragment.owners.map { OwnerTransformer.graphQLToEntity(it) },
            identityId = fragment.identityId,
            maskAddress = fragment.maskAddress,
            realAddress = fragment.realAddress,
            realAddressType = EmailMaskRealAddressTypeTransformer.graphQLToEntity(fragment.realAddressType),
            status = EmailMaskStatusTransformer.graphQLToEntity(fragment.status),
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

    fun unsealedEntityToApi(emailMask: UnsealedEmailMaskEntity): EmailMask =
        EmailMask(
            id = emailMask.id,
            owner = emailMask.owner,
            owners = emailMask.owners.map { OwnerTransformer.entityToApi(it) },
            identityId = emailMask.identityId,
            maskAddress = emailMask.maskAddress,
            realAddress = emailMask.realAddress,
            realAddressType = EmailMaskRealAddressTypeTransformer.entityToApi(emailMask.realAddressType),
            status = EmailMaskStatusTransformer.entityToApi(emailMask.status),
            inboundReceived = emailMask.inboundReceived,
            inboundDelivered = emailMask.inboundDelivered,
            outboundReceived = emailMask.outboundReceived,
            outboundDelivered = emailMask.outboundDelivered,
            spamCount = emailMask.spamCount,
            virusCount = emailMask.virusCount,
            expiresAt = emailMask.expiresAt,
            createdAt = emailMask.createdAt,
            updatedAt = emailMask.updatedAt,
            version = emailMask.version,
            metadata = emailMask.metadata,
        )

    private fun EmailMaskFragment.Metadata.toSealedAttributeEntity(): SealedAttributeEntity =
        SealedAttributeEntity(
            algorithm = sealedAttribute.algorithm,
            keyId = sealedAttribute.keyId,
            plainTextType = sealedAttribute.plainTextType,
            base64EncodedSealedData = sealedAttribute.base64EncodedSealedData,
        )

    /**
     * Transforms a sealed EmailMaskEntity to UnsealedEmailMaskEntity.
     *
     * @param sealedEmailMask [EmailMaskEntity] The sealed email mask entity.
     * @param metadata [String] Optional unsealed metadata.
     * @return [UnsealedEmailMaskEntity] The unsealed email mask entity.
     */
    fun toUnsealedEntity(
        sealedEmailMask: EmailMaskEntity,
        metadata: Map<String, String>? = null,
    ): UnsealedEmailMaskEntity =
        UnsealedEmailMaskEntity(
            id = sealedEmailMask.id,
            owner = sealedEmailMask.owner,
            owners = sealedEmailMask.owners,
            identityId = sealedEmailMask.identityId,
            maskAddress = sealedEmailMask.maskAddress,
            realAddress = sealedEmailMask.realAddress,
            realAddressType = sealedEmailMask.realAddressType,
            status = sealedEmailMask.status,
            inboundReceived = sealedEmailMask.inboundReceived,
            inboundDelivered = sealedEmailMask.inboundDelivered,
            outboundReceived = sealedEmailMask.outboundReceived,
            outboundDelivered = sealedEmailMask.outboundDelivered,
            spamCount = sealedEmailMask.spamCount,
            virusCount = sealedEmailMask.virusCount,
            expiresAt = sealedEmailMask.expiresAt,
            version = sealedEmailMask.version,
            createdAt = sealedEmailMask.createdAt,
            updatedAt = sealedEmailMask.updatedAt,
            metadata = metadata,
        )

    /**
     * Transforms a sealed EmailMaskEntity to PartialEmailMaskEntity.
     *
     * @param sealedEmailMask [EmailMaskEntity] The sealed email mask entity.
     * @return [PartialEmailMaskEntity] The partial email mask entity.
     */
    fun toPartialEntity(sealedEmailMask: EmailMaskEntity): PartialEmailMaskEntity =
        PartialEmailMaskEntity(
            id = sealedEmailMask.id,
            owner = sealedEmailMask.owner,
            owners = sealedEmailMask.owners,
            identityId = sealedEmailMask.identityId,
            maskAddress = sealedEmailMask.maskAddress,
            realAddress = sealedEmailMask.realAddress,
            realAddressType = sealedEmailMask.realAddressType,
            status = sealedEmailMask.status,
            inboundReceived = sealedEmailMask.inboundReceived,
            inboundDelivered = sealedEmailMask.inboundDelivered,
            outboundReceived = sealedEmailMask.outboundReceived,
            outboundDelivered = sealedEmailMask.outboundDelivered,
            spamCount = sealedEmailMask.spamCount,
            virusCount = sealedEmailMask.virusCount,
            expiresAt = sealedEmailMask.expiresAt,
            version = sealedEmailMask.version,
            createdAt = sealedEmailMask.createdAt,
            updatedAt = sealedEmailMask.updatedAt,
        )

    fun partialEntityToApi(result: PartialEmailMaskEntity): PartialEmailMask =
        PartialEmailMask(
            id = result.id,
            owner = result.owner,
            owners = result.owners.map { OwnerTransformer.entityToApi(it) },
            identityId = result.identityId,
            maskAddress = result.maskAddress,
            realAddress = result.realAddress,
            realAddressType = EmailMaskRealAddressTypeTransformer.entityToApi(result.realAddressType),
            status = EmailMaskStatusTransformer.entityToApi(result.status),
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
