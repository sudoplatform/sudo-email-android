/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailAddress.transformers

import com.sudoplatform.sudoemail.internal.data.common.transformers.PublicKeyFormatTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressPublicInfoEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressPublicKeyEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PublicKeyFormatEntity
import com.sudoplatform.sudoemail.types.EmailAddressPublicInfo
import com.sudoplatform.sudoemail.types.PublicKeyFormat
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressPublicInfo as EmailAddressPublicInfoFragment

/**
 * Transformer responsible for transforming [EmailAddressPublicInfo] GraphQL data
 * types to the entity type that is exposed to users.
 */
internal object EmailAddressPublicInfoTransformer {
    /**
     * Transform the [EmailAddressPublicInfoFragment] GraphQL type to its entity type.
     *
     * @param emailAddressPublicInfoFragment [EmailAddressPublicInfoFragment] The GraphQL type.
     * @return The [EmailAddressPublicInfo] entity type.
     */
    fun graphQLToEntity(emailAddressPublicInfoFragment: EmailAddressPublicInfoFragment): EmailAddressPublicInfoEntity {
        val keyFormat: PublicKeyFormatEntity =
            PublicKeyFormatTransformer.toEntity(
                emailAddressPublicInfoFragment.publicKeyDetails.emailAddressPublicKey.keyFormat,
            )
        val publicKeyDetails =
            EmailAddressPublicKeyEntity(
                publicKey = emailAddressPublicInfoFragment.publicKeyDetails.emailAddressPublicKey.publicKey,
                keyFormat = keyFormat,
                algorithm = emailAddressPublicInfoFragment.publicKeyDetails.emailAddressPublicKey.algorithm,
            )
        return EmailAddressPublicInfoEntity(
            emailAddress = emailAddressPublicInfoFragment.emailAddress,
            keyId = emailAddressPublicInfoFragment.keyId,
            publicKeyDetails = publicKeyDetails,
        )
    }

    /**
     * Transforms an [EmailAddressPublicInfoEntity] to API type.
     *
     * @param emailAddressPublicInfoEntity [EmailAddressPublicInfoEntity] The entity to transform.
     * @return [EmailAddressPublicInfo] The API type.
     */
    fun entityToApi(emailAddressPublicInfoEntity: EmailAddressPublicInfoEntity): EmailAddressPublicInfo {
        val keyFormat: PublicKeyFormat =
            when (emailAddressPublicInfoEntity.publicKeyDetails.keyFormat) {
                PublicKeyFormatEntity.RSA_PUBLIC_KEY -> PublicKeyFormat.RSA_PUBLIC_KEY
                PublicKeyFormatEntity.SPKI -> PublicKeyFormat.SPKI
            }
        val publicKeyDetails =
            com.sudoplatform.sudoemail.types.EmailAddressPublicKey(
                publicKey = emailAddressPublicInfoEntity.publicKeyDetails.publicKey,
                keyFormat = keyFormat,
                algorithm = emailAddressPublicInfoEntity.publicKeyDetails.algorithm,
            )
        return EmailAddressPublicInfo(
            emailAddress = emailAddressPublicInfoEntity.emailAddress,
            keyId = emailAddressPublicInfoEntity.keyId,
            publicKeyDetails = publicKeyDetails,
        )
    }
}
