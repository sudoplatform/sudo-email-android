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
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.util.EmailAddressParser
import com.sudoplatform.sudoemail.util.StringHasher
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.exceptions.SudoUserException

/**
 * Input for the unblock email addresses use case.
 *
 * @property addresses [List] of [String] email addresses to unblock.
 */
internal data class UnblockEmailAddressesUseCaseInput(
    val addresses: List<String>,
)

/**
 * Use case for unblocking email addresses.
 *
 * This use case handles unblocking one or more email addresses by computing their
 * hashed values and removing them from the blocklist.
 *
 * @property blockedAddressService [BlockedAddressService] Service for blocked address operations.
 * @property sudoUserClient [SudoUserClient] Client for user operations.
 * @property logger [Logger] Logger for debugging.
 */
internal class UnblockEmailAddressesUseCase(
    private val blockedAddressService: BlockedAddressService,
    private val sudoUserClient: SudoUserClient,
    private val logger: Logger,
    private val unblockEmailAddressesByHashedValueUseCase: UnblockEmailAddressesByHashedValueUseCase =
        UnblockEmailAddressesByHashedValueUseCase(
            blockedAddressService,
            sudoUserClient,
            logger,
        ),
) {
    /**
     * Executes the unblock email addresses use case.
     *
     * @param input [UnblockEmailAddressesUseCaseInput] The input parameters.
     * @return [BatchOperationResultEntity] The result containing successfully and unsuccessfully unblocked addresses.
     * @throws SudoEmailClient.EmailBlocklistException if the operation fails.
     * @throws SudoUserException.NotSignedInException if the user is not signed in.
     */
    suspend fun execute(input: UnblockEmailAddressesUseCaseInput): BatchOperationResultEntity<String, String> {
        logger.debug("UnblockEmailAddressesUseCase.execute: $input")

        val (addresses) = input

        if (addresses.isEmpty()) {
            throw SudoEmailClient.EmailBlocklistException.InvalidInputException(
                StringConstants.ADDRESS_BLOCKLIST_EMPTY_MSG,
            )
        }

        val owner =
            this.sudoUserClient.getSubject()
                ?: throw SudoUserException.NotSignedInException()

        try {
            val normalizedAddresses = HashSet<String>()
            val hashedToOriginalAddressMap = mutableMapOf<String, String>()
            for (address in addresses) {
                if (!EmailAddressParser.validate(address)) {
                    throw SudoEmailClient.EmailBlocklistException.InvalidInputException(
                        StringConstants.INVALID_EMAIL_ADDRESS_MSG,
                    )
                }
                val normalized = EmailAddressParser.normalize(address)
                if (!normalizedAddresses.add(normalized)) {
                    throw SudoEmailClient.EmailBlocklistException.InvalidInputException(
                        StringConstants.ADDRESS_BLOCKLIST_DUPLICATE_MSG,
                    )
                }
                val hashedBlockedValue = StringHasher.hashString("$owner|$normalized")
                hashedToOriginalAddressMap[hashedBlockedValue] = address
            }
            val input =
                UnblockEmailAddressesByHashedValueUseCaseInput(
                    hashedValues = hashedToOriginalAddressMap.keys.toList(),
                )
            val result = unblockEmailAddressesByHashedValueUseCase.execute(input)

            val successResult =
                result.successValues?.mapNotNull {
                    hashedToOriginalAddressMap[it]
                } ?: emptyList()
            val failureResult =
                result.failureValues?.mapNotNull {
                    hashedToOriginalAddressMap[it]
                } ?: emptyList()
            return BatchOperationResultEntity(
                status = result.status,
                successValues = successResult,
                failureValues = failureResult,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailBlocklistException(e)
        }
    }
}
