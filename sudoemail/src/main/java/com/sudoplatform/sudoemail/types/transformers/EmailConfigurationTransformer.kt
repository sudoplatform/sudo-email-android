/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.graphql.fragment.EmailConfigurationData
import com.sudoplatform.sudoemail.types.ConfigurationData

/**
 * Transformer responsible for transforming the [ConfigurationData] GraphQL data
 * types to the entity type that is exposed to users.
 */

internal object EmailConfigurationTransformer {
    /**
     * Transform the [EmailConfigurationData] GraphQL type to its entity type.
     *
     * @param emailConfigurationData [EmailConfigurationData] The GraphQL type.
     * @return The [ConfigurationData] entity type.
     */
    fun toEntity(emailConfigurationData: EmailConfigurationData): ConfigurationData =
        ConfigurationData(
            deleteEmailMessagesLimit = emailConfigurationData.deleteEmailMessagesLimit,
            updateEmailMessagesLimit = emailConfigurationData.updateEmailMessagesLimit,
            emailMessageMaxInboundMessageSize = emailConfigurationData.emailMessageMaxInboundMessageSize,
            emailMessageMaxOutboundMessageSize = emailConfigurationData.emailMessageMaxOutboundMessageSize,
            emailMessageRecipientsLimit = emailConfigurationData.emailMessageRecipientsLimit,
            encryptedEmailMessageRecipientsLimit = emailConfigurationData.encryptedEmailMessageRecipientsLimit,
            prohibitedFileExtensions = emailConfigurationData.prohibitedFileExtensions,
        )
}
