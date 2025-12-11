/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.blockedAddress

import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.blockedAddress.transformers.BlockedAddressActionTransformer
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.GetEmailAddressBlocklistRequest
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.UnsealedBlockedAddress
import com.sudoplatform.sudoemail.types.UnsealedBlockedAddressStatus
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.exceptions.SudoUserException

/**
 * Use case for retrieving the email address blocklist.
 *
 * This use case retrieves all blocked email addresses, unseals (decrypts) them,
 * and returns them as unsealed blocked addresses.
 *
 * @property blockedAddressService [BlockedAddressService] Service for blocked address operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property sudoUserClient [SudoUserClient] Client for user operations.
 * @property sealingService [SealingService] Service for unsealing/decrypting data.
 * @property logger [Logger] Logger for debugging.
 */
internal class GetEmailAddressBlocklistUseCase(
    private val blockedAddressService: BlockedAddressService,
    private val serviceKeyManager: ServiceKeyManager,
    private val sudoUserClient: SudoUserClient,
    private val sealingService: SealingService,
    private val logger: Logger,
) {
    /**
     * Executes the get email address blocklist use case.
     *
     * @return [List] of [UnsealedBlockedAddress] containing all blocked addresses.
     * @throws SudoUserException.NotSignedInException if the user is not signed in.
     */
    suspend fun execute(): List<UnsealedBlockedAddress> {
        logger.debug("GetEmailAddressBlocklistUseCase.execute")
        val owner =
            this.sudoUserClient.getSubject()
                ?: throw SudoUserException.NotSignedInException()

        try {
            val sealedBlockedAddresses =
                this.blockedAddressService.getEmailAddressBlocklist(
                    GetEmailAddressBlocklistRequest(
                        owner = owner,
                    ),
                )

            return sealedBlockedAddresses.map { sealedBlockedAddress ->
                val hashedBlockedValue = sealedBlockedAddress.hashedBlockedValue
                var unsealedAddress = ""
                try {
                    if (
                        serviceKeyManager.symmetricKeyExists(
                            sealedBlockedAddress.sealedValue.keyId,
                        )
                    ) {
                        val unsealedAddress =
                            sealingService
                                .unsealString(
                                    sealedBlockedAddress.sealedValue.keyId,
                                    Base64.decode(sealedBlockedAddress.sealedValue.base64EncodedSealedData),
                                ).decodeToString()
                        UnsealedBlockedAddress(
                            address = unsealedAddress,
                            hashedBlockedValue = hashedBlockedValue,
                            action = BlockedAddressActionTransformer.entityToApi(sealedBlockedAddress.action),
                            status = UnsealedBlockedAddressStatus.Completed,
                            emailAddressId = sealedBlockedAddress.emailAddressId,
                        )
                    } else {
                        throw KeyNotFoundException(
                            StringConstants.SYMMETRIC_KEY_NOT_FOUND_ERROR_MSG,
                        )
                    }
                } catch (e: Throwable) {
                    logger.error("error unsealing blocked address for hash $hashedBlockedValue: $e")
                    UnsealedBlockedAddress(
                        address = unsealedAddress,
                        hashedBlockedValue = hashedBlockedValue,
                        action = BlockedAddressActionTransformer.entityToApi(sealedBlockedAddress.action),
                        status =
                            UnsealedBlockedAddressStatus.Failed(
                                cause =
                                    SudoEmailClient.EmailBlocklistException.FailedException(
                                        e.message,
                                        e,
                                    ),
                            ),
                        emailAddressId = sealedBlockedAddress.emailAddressId,
                    )
                }
            }
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailBlocklistException(e)
        }
    }
}
