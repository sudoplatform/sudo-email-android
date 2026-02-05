/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.configuration

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.configuration.transformers.ConfigurationDataTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataEntity
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudologging.Logger

/**
 * Implementation of [ConfigurationDataService] for retrieving configuration data.
 *
 * This service handles fetching configuration data by interacting with the GraphQL API client.
 *
 * @property apiClient [ApiClient] The GraphQL API client for executing queries.
 * @property logger [Logger] Logger for logging debug and error messages.
 */
internal class GraphQLConfigurationDataService(
    val apiClient: ApiClient,
    val logger: Logger,
) : ConfigurationDataService {
    override suspend fun getConfigurationData(): ConfigurationDataEntity {
        try {
            val queryResponse = apiClient.getEmailConfigQuery()

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailConfigurationError(queryResponse.errors.first())
            }

            val queryResult =
                queryResponse.data?.getEmailConfig?.emailConfigurationData
                    ?: throw SudoEmailClient.EmailConfigurationException.FailedException()
            return ConfigurationDataTransformer.graphQLToEntity(queryResult)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailConfigurationException(e)
        }
    }

    override suspend fun getConfiguredEmailDomains(): List<String> {
        try {
            val queryResponse = apiClient.getConfiguredEmailDomainsQuery()

            if (queryResponse.hasErrors()) {
                logger.error("error = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailDomainError(queryResponse.errors.first())
            }
            return queryResponse.data?.getConfiguredEmailDomains?.domains ?: emptyList()
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAddressException(e)
            }
        }
    }

    override suspend fun getSupportedEmailDomains(): List<String> {
        try {
            val queryResponse = apiClient.getSupportedEmailDomainsQuery()

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailDomainError(queryResponse.errors.first())
            }
            return queryResponse.data?.getEmailDomains?.domains ?: emptyList()
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAddressException(e)
            }
        }
    }

    override suspend fun getEmailMaskDomains(): List<String> {
        try {
            val queryResponse = apiClient.getEmailMaskDomainsQuery()

            if (queryResponse.hasErrors()) {
                logger.error("error = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailDomainError(queryResponse.errors.first())
            }
            return queryResponse.data?.getEmailMaskDomains?.domains ?: emptyList()
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAddressException(e)
            }
        }
    }
}
