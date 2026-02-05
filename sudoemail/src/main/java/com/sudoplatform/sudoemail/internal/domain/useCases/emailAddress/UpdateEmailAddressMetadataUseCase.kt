/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress

import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.UpdateEmailAddressMetadataRequest
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudologging.Logger

/**
 * Input for the update email address metadata use case.
 *
 * @property emailAddressId [String] The ID of the email address to update.
 * @property alias [String] Optional new alias for the email address (empty string to remove).
 */
internal data class UpdateEmailAddressMetadataUseCaseInput(
    val emailAddressId: String,
    val alias: String?,
)

/**
 * Use case for updating email address metadata.
 *
 * This use case handles updating the alias of an email address.
 *
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property sealingService [SealingService] Service for sealing/encrypting data.
 * @property logger [Logger] Logger for debugging.
 */
internal class UpdateEmailAddressMetadataUseCase(
    private val emailAddressService: EmailAddressService,
    private val serviceKeyManager: ServiceKeyManager,
    private val sealingService: SealingService,
    private val logger: Logger,
) {
    /**
     * Executes the update email address metadata use case.
     *
     * @param input [UpdateEmailAddressMetadataUseCaseInput] The input parameters.
     * @return [String] The ID of the updated email address.
     * @throws KeyNotFoundException if the encryption key is not found.
     */
    suspend fun execute(input: UpdateEmailAddressMetadataUseCaseInput): String {
        logger.debug("UpdateEmailAddressMetadataUseCase execute input: $input")
        try {
            var clearAlias = false
            val alias =
                if (input.alias !== null) {
                    if (input.alias.isEmpty()) {
                        clearAlias = true
                        null
                    } else {
                        // Ensure symmetric key has been generated
                        val symmetricKeyId =
                            this.serviceKeyManager.getCurrentSymmetricKeyId()
                                ?: throw KeyNotFoundException("Symmetric key not found")
                        val sealedAliasBytes = sealingService.sealString(symmetricKeyId, input.alias.toByteArray())
                        val base64EncodedSealedData = String(Base64.encode(sealedAliasBytes), Charsets.UTF_8)
                        SealedAttributeInput(
                            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                            keyId = symmetricKeyId,
                            plainTextType = "string",
                            base64EncodedSealedData = base64EncodedSealedData,
                        )
                    }
                } else {
                    null
                }

            val updateEmailAddressMetadataRequest =
                UpdateEmailAddressMetadataRequest(
                    id = input.emailAddressId,
                    alias = alias,
                    clearAlias = clearAlias,
                )

            return emailAddressService.updateMetadata(updateEmailAddressMetadataRequest)
        } finally {
            logger.debug("UpdateEmailAddressMetadataUseCase execute complete")
        }
    }
}
