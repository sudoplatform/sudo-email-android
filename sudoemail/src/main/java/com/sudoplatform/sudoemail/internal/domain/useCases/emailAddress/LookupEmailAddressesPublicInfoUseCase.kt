/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressPublicInfoEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.LookupEmailAddressesPublicInfoRequest
import com.sudoplatform.sudologging.Logger

internal data class LookupEmailAddressesPublicInfoUseCaseInput(
    val addresses: List<String>,
    val throwIfNotAllInternal: Boolean = false,
)

internal class LookupEmailAddressesPublicInfoUseCase(
    private val emailAddressService: EmailAddressService,
    private val logger: Logger,
) {
    suspend fun execute(input: LookupEmailAddressesPublicInfoUseCaseInput): List<EmailAddressPublicInfoEntity> {
        logger.debug("LookupEmailAddressesPublicInfoUseCase: executing with input: $input")
        val safeInput =
            LookupEmailAddressesPublicInfoUseCaseInput(
                input.addresses.map { it.lowercase() },
                input.throwIfNotAllInternal,
            )
        val lookupPublicInfoRequest =
            LookupEmailAddressesPublicInfoRequest(
                emailAddresses = safeInput.addresses,
            )
        val emailAddressesPublicInfo = emailAddressService.lookupPublicInfo(lookupPublicInfoRequest)

        if (safeInput.throwIfNotAllInternal &&
            !safeInput.addresses.all { recipient ->
                emailAddressesPublicInfo.any { info -> info.emailAddress == recipient }
            }
        ) {
            throw SudoEmailClient.EmailMessageException.InNetworkAddressNotFoundException(
                StringConstants.IN_NETWORK_EMAIL_ADDRESSES_NOT_FOUND_ERROR_MSG,
            )
        }

        return emailAddressesPublicInfo
    }
}
