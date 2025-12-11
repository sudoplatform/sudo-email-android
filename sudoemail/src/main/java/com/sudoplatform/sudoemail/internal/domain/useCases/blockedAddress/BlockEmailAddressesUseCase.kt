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
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockEmailAddressRequestItem
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockEmailAddressesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedAddressHashAlgorithmEntity
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SymmetricKeyEncryptionAlgorithmEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.BlockedEmailAddressAction
import com.sudoplatform.sudoemail.types.BlockedEmailAddressLevel
import com.sudoplatform.sudoemail.util.EmailAddressParser
import com.sudoplatform.sudoemail.util.StringHasher
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.exceptions.SudoUserException

/**
 * Input for the block email addresses use case.
 *
 * @property addresses [List] of [String] email addresses to block.
 * @property action [BlockedEmailAddressAction] The action to take on blocked addresses.
 * @property emailAddressId [String] Optional email address ID to scope the block to.
 * @property level [BlockedEmailAddressLevel] The level at which to block (address or domain).
 */
internal data class BlockEmailAddressesUseCaseInput(
    val addresses: List<String>,
    val action: BlockedEmailAddressAction,
    val emailAddressId: String?,
    val level: BlockedEmailAddressLevel,
)

/**
 * Use case for blocking email addresses.
 *
 * This use case handles blocking one or more email addresses by hashing, encrypting,
 * and storing them in the blocklist.
 *
 * @property blockedAddressService [BlockedAddressService] Service for blocked address operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property sudoUserClient [SudoUserClient] Client for user operations.
 * @property sealingService [SealingService] Service for sealing/encrypting data.
 * @property logger [Logger] Logger for debugging.
 */
internal class BlockEmailAddressesUseCase(
    private val blockedAddressService: BlockedAddressService,
    private val serviceKeyManager: ServiceKeyManager,
    private val sudoUserClient: SudoUserClient,
    private val sealingService: SealingService,
    private val logger: Logger,
) {
    /**
     * Executes the block email addresses use case.
     *
     * @param input [BlockEmailAddressesUseCaseInput] The input parameters.
     * @return [BatchOperationResultEntity] The result containing successfully and unsuccessfully blocked addresses.
     * @throws SudoEmailClient.EmailBlocklistException if the operation fails.
     * @throws KeyNotFoundException if the encryption key is not found.
     * @throws SudoUserException.NotSignedInException if the user is not signed in.
     */
    suspend fun execute(input: BlockEmailAddressesUseCaseInput): BatchOperationResultEntity<String, String> {
        logger.debug("BlockEmailAddressesUseCase.execute: $input")
        val (addresses, action, emailAddressId, level) = input
        if (addresses.isEmpty()) {
            throw SudoEmailClient.EmailBlocklistException.InvalidInputException(
                StringConstants.ADDRESS_BLOCKLIST_EMPTY_MSG,
            )
        }

        val symmetricKeyId =
            this.serviceKeyManager.getCurrentSymmetricKeyId()
                ?: throw KeyNotFoundException(StringConstants.SYMMETRIC_KEY_NOT_FOUND_ERROR_MSG)

        val owner =
            this.sudoUserClient.getSubject()
                ?: throw SudoUserException.NotSignedInException()

        try {
            val normalizedAddresses = HashSet<String>()
            val hashedToOriginalAddressMap = mutableMapOf<String, String>()
            val blockedAddresses = mutableListOf<BlockEmailAddressRequestItem>()
            val prefix = emailAddressId ?: owner
            val algorithm = SymmetricKeyEncryptionAlgorithmEntity.AES_CBC_PKCS7PADDING.toString()

            addresses.forEach { address ->
                if (EmailAddressParser.validate(address)) {
                    val normalized = EmailAddressParser.normalize(address)
                    val stringToHash =
                        when (level) {
                            BlockedEmailAddressLevel.ADDRESS -> {
                                normalized
                            }

                            BlockedEmailAddressLevel.DOMAIN -> {
                                EmailAddressParser.getDomain(normalized)
                            }
                        }
                    if (normalizedAddresses.add(stringToHash)) {
                        val sealedString =
                            sealingService.sealString(
                                symmetricKeyId,
                                stringToHash.toByteArray(),
                            )
                        val hashedBlockedValue = StringHasher.hashString("$prefix|$stringToHash")
                        hashedToOriginalAddressMap[hashedBlockedValue] = address
                        blockedAddresses.add(
                            BlockEmailAddressRequestItem(
                                sealedValue =
                                    SealedAttributeEntity(
                                        keyId = symmetricKeyId,
                                        algorithm = algorithm,
                                        plainTextType = "string",
                                        base64EncodedSealedData = String(Base64.encode(sealedString)),
                                    ),
                                hashedBlockedValue = hashedBlockedValue,
                                hashAlgorithm = BlockedAddressHashAlgorithmEntity.SHA256,
                                action = BlockedAddressActionTransformer.apiToEntity(action),
                            ),
                        )
                    }
                } else {
                    throw SudoEmailClient.EmailBlocklistException.InvalidInputException(
                        "Invalid email address: $address",
                    )
                }
            }

            val result =
                blockedAddressService.blockEmailAddresses(
                    BlockEmailAddressesRequest(
                        owner = owner,
                        blockedAddresses = blockedAddresses,
                        emailAddressId = emailAddressId,
                    ),
                )

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
