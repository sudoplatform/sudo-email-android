/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.configuration.transformers

import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataEntity
import com.sudoplatform.sudoemail.types.ConfigurationData
import com.sudoplatform.sudoemail.graphql.fragment.EmailConfigurationData as EmailConfigurationDataFragment

/**
 * Transformer for converting email configuration data between GraphQL, entity, and API representations.
 */
internal object ConfigurationDataTransformer {
    /**
     * Transforms GraphQL email configuration data to entity type.
     *
     * @param configurationData [EmailConfigurationDataFragment] The GraphQL configuration data.
     * @return [ConfigurationDataEntity] The entity.
     */
    fun graphQLToEntity(configurationData: EmailConfigurationDataFragment): ConfigurationDataEntity =
        ConfigurationDataEntity(
            deleteEmailMessagesLimit = configurationData.deleteEmailMessagesLimit,
            updateEmailMessagesLimit = configurationData.updateEmailMessagesLimit,
            emailMessageMaxInboundMessageSize = configurationData.emailMessageMaxInboundMessageSize,
            emailMessageMaxOutboundMessageSize = configurationData.emailMessageMaxOutboundMessageSize,
            emailMessageRecipientsLimit = configurationData.emailMessageRecipientsLimit,
            encryptedEmailMessageRecipientsLimit = configurationData.encryptedEmailMessageRecipientsLimit,
            prohibitedFileExtensions = configurationData.prohibitedFileExtensions,
        )

    /**
     * Transforms a [ConfigurationDataEntity] to API type.
     *
     * @param configurationDataEntity [ConfigurationDataEntity] The entity to transform.
     * @return [ConfigurationData] The API type.
     */
    fun entityToApi(configurationDataEntity: ConfigurationDataEntity): ConfigurationData =
        ConfigurationData(
            deleteEmailMessagesLimit = configurationDataEntity.deleteEmailMessagesLimit,
            updateEmailMessagesLimit = configurationDataEntity.updateEmailMessagesLimit,
            emailMessageMaxInboundMessageSize = configurationDataEntity.emailMessageMaxInboundMessageSize,
            emailMessageMaxOutboundMessageSize = configurationDataEntity.emailMessageMaxOutboundMessageSize,
            emailMessageRecipientsLimit = configurationDataEntity.emailMessageRecipientsLimit,
            encryptedEmailMessageRecipientsLimit = configurationDataEntity.encryptedEmailMessageRecipientsLimit,
            prohibitedFileExtensions = configurationDataEntity.prohibitedFileExtensions,
        )
}
