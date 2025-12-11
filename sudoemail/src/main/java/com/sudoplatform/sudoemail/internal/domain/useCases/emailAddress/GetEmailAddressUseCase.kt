/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress

import com.sudoplatform.sudoemail.internal.data.common.mechanisms.EmailAddressUnsealer
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.GetEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.UnsealedEmailAddressEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudologging.Logger

/**
 * Input for the get email address use case.
 *
 * @property emailAddressId [String] The ID of the email address to retrieve.
 */
internal data class GetEmailAddressUseCaseInput(
    val emailAddressId: String,
)

/**
 * Use case for retrieving an email address.
 *
 * This use case retrieves and unseals (decrypts) an email address by its ID.
 *
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property logger [Logger] Logger for debugging.
 */
internal class GetEmailAddressUseCase(
    private val emailAddressService: EmailAddressService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the get email address use case.
     *
     * @param input [GetEmailAddressUseCaseInput] The input parameters.
     * @return [UnsealedEmailAddressEntity] The unsealed email address, or null if not found.
     */
    suspend fun execute(input: GetEmailAddressUseCaseInput): UnsealedEmailAddressEntity? {
        logger.debug("GetEmailAddressUseCase execute input: $input")

        val sealedEmailAddress =
            emailAddressService.get(
                GetEmailAddressRequest(
                    id = input.emailAddressId,
                ),
            )

        if (sealedEmailAddress == null) {
            return sealedEmailAddress
        }

        val emailAddressUnsealer =
            EmailAddressUnsealer(
                this.serviceKeyManager,
            )
        return emailAddressUnsealer.unseal(sealedEmailAddress)
    }
}
