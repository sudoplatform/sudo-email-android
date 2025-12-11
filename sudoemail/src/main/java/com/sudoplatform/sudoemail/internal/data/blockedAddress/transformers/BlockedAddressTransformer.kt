/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.blockedAddress.transformers

import com.sudoplatform.sudoemail.graphql.fragment.GetEmailAddressBlocklistResponse
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity

/**
 * Transformer for converting blocked address data between GraphQL and entity representations.
 */
internal object BlockedAddressTransformer {
    /**
     * Transforms a GraphQL blocked address to a blocked address entity.
     *
     * @param graphQL [GetEmailAddressBlocklistResponse.BlockedAddress] The GraphQL blocked address.
     * @return The [BlockedAddressEntity].
     */
    fun graphQLToEntity(graphQL: GetEmailAddressBlocklistResponse.BlockedAddress): BlockedAddressEntity =
        BlockedAddressEntity(
            hashedBlockedValue = graphQL.hashedBlockedValue,
            sealedValue = graphQL.sealedValue.toSealedAttributeEntity(),
            action = BlockedAddressActionTransformer.graphQLToEntity(graphQL.action),
            emailAddressId = graphQL.emailAddressId,
        )

    private fun GetEmailAddressBlocklistResponse.SealedValue.toSealedAttributeEntity(): SealedAttributeEntity =
        SealedAttributeEntity(
            keyId = sealedAttribute.keyId,
            algorithm = sealedAttribute.algorithm,
            plainTextType = sealedAttribute.plainTextType,
            base64EncodedSealedData = sealedAttribute.base64EncodedSealedData,
        )
}
