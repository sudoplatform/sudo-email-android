/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.configuration.transformers

import com.sudoplatform.sudoemail.types.EmailDomain
import org.json.JSONObject
import com.sudoplatform.sudoemail.graphql.fragment.EmailDomain as EmailDomainFragment

/**
 * Transformer for converting email domain data between GraphQL and API representations.
 */
internal object EmailDomainTransformer {
    /**
     * Transforms a GraphQL [EmailDomainFragment] to the public API [EmailDomain] type.
     *
     * @param graphQLDomain [EmailDomainFragment] The GraphQL email domain fragment.
     * @return [EmailDomain] The public API type.
     */
    fun graphQLToApi(graphQLDomain: EmailDomainFragment): EmailDomain {
        val metadataMap = mutableMapOf<String, String>()
        val jsonObject = JSONObject(graphQLDomain.metadata)
        jsonObject.keys().forEach { key ->
            metadataMap[key] = jsonObject.getString(key)
        }
        return EmailDomain(
            domain = graphQLDomain.domain,
            isMaskDomain = graphQLDomain.isMaskDomain,
            metadata = metadataMap,
        )
    }
}
