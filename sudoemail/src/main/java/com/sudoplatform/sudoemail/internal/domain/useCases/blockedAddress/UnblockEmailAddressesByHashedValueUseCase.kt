/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.blockedAddress

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.UnblockEmailAddressesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.exceptions.SudoUserException

/**
 * Input for the unblock email addresses by hashed value use case.
 *
 * @property hashedValues [List] of [String] hashed values of email addresses to unblock.
 */
internal data class UnblockEmailAddressesByHashedValueUseCaseInput(
    val hashedValues: List<String>,
)

/**
 * Use case for unblocking email addresses by their hashed values.
 *
 * This use case handles unblocking email addresses when only the hashed values are known,
 * without needing to know the original email addresses.
 *
 * @property blockedAddressService [BlockedAddressService] Service for blocked address operations.
 * @property sudoUserClient [SudoUserClient] Client for user operations.
 * @property logger [Logger] Logger for debugging.
 */
internal class UnblockEmailAddressesByHashedValueUseCase(
    private val blockedAddressService: BlockedAddressService,
    private val sudoUserClient: SudoUserClient,
    private val logger: Logger,
) {
    /**
     * Executes the unblock email addresses by hashed value use case.
     *
     * @param input [UnblockEmailAddressesByHashedValueUseCaseInput] The input parameters.
     * @return [BatchOperationResultEntity] The result containing successfully and unsuccessfully unblocked hashed values.
     * @throws SudoEmailClient.EmailBlocklistException if the operation fails.
     * @throws SudoUserException.NotSignedInException if the user is not signed in.
     */
    suspend fun execute(input: UnblockEmailAddressesByHashedValueUseCaseInput): BatchOperationResultEntity<String, String> {
        logger.debug("UnblockEmailAddressesByHashedValueUseCase.execute: $input")

        val (hashedAddresses) = input

        if (hashedAddresses.isEmpty()) {
            throw SudoEmailClient.EmailBlocklistException.InvalidInputException(
                StringConstants.ADDRESS_BLOCKLIST_EMPTY_MSG,
            )
        }

        val owner =
            this.sudoUserClient.getSubject()
                ?: throw SudoUserException.NotSignedInException()

        try {
            val request =
                UnblockEmailAddressesRequest(
                    owner = owner,
                    hashedBlockedValues = hashedAddresses,
                )
            return blockedAddressService.unblockEmailAddresses(request)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailBlocklistException(e)
        }
    }
}
