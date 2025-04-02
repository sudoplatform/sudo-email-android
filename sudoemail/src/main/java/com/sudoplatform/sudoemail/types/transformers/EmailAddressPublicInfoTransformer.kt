/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.types.EmailAddressPublicInfo
import com.sudoplatform.sudoemail.types.EmailAddressPublicKey
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
    fun toEntity(
        emailAddressPublicInfoFragment: EmailAddressPublicInfoFragment,
    ): EmailAddressPublicInfo {
        val keyFormat: PublicKeyFormat = PublicKeyFormatTransformer.toEntity(
            emailAddressPublicInfoFragment.publicKeyDetails.emailAddressPublicKey.keyFormat,
        )
        val publicKeyDetails = EmailAddressPublicKey(
            publicKey = emailAddressPublicInfoFragment.publicKeyDetails.emailAddressPublicKey.publicKey,
            keyFormat = keyFormat,
            algorithm = emailAddressPublicInfoFragment.publicKeyDetails.emailAddressPublicKey.algorithm,
        )
        return EmailAddressPublicInfo(
            emailAddress = emailAddressPublicInfoFragment.emailAddress,
            keyId = emailAddressPublicInfoFragment.keyId,
            publicKeyDetails = publicKeyDetails,
        )
    }
}
