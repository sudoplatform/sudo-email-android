/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.configuration

/**
 * Service interface for retrieving email configuration data.
 *
 * Provides operations to retrieve configuration settings and email domain information.
 */
internal interface ConfigurationDataService {
    /**
     * Retrieves the complete email configuration data.
     *
     * @return The [ConfigurationDataEntity] containing all email settings.
     */
    suspend fun getConfigurationData(): ConfigurationDataEntity

    /**
     * Retrieves the list of supported email domains.
     *
     * @return A [List] of email domain [String]s that are supported for provisioning.
     */
    suspend fun getSupportedEmailDomains(): List<String>

    /**
     * Retrieves the list of configured email domains.
     *
     * @return A [List] of email domain [String]s that have been configured.
     */
    suspend fun getConfiguredEmailDomains(): List<String>

    /**
     * Retrieves the list of email mask domains.
     *
     * @return a [List] of email mask domains that have been configured.
     */
    suspend fun getEmailMaskDomains(): List<String>
}
