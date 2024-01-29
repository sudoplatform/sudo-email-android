/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.types.EmailAddressPublicInfo
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
        return EmailAddressPublicInfo(
            emailAddress = emailAddressPublicInfoFragment.emailAddress(),
            publicKey = emailAddressPublicInfoFragment.publicKey(),
        )
    }
}
