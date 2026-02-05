/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMask

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.internal.data.common.mechanisms.EmailMaskUnsealer
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.emailMask.transformers.EmailMaskTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.SealedEmailMaskEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.UnsealedEmailMaskEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.UpdateEmailMaskRequest
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudologging.Logger
import java.util.Date

/**
 * Input for the update email mask use case.
 *
 * @property id [String] The ID of the email mask to update.
 * @property metadata [String] Optional new metadata for the email mask. If the empty map is provided, this will clear the metadata.
 * @property expiresAt [Date] Optional new expiration date for the email mask. If a zero date is provided, this will clear the expiration date
 */
internal data class UpdateEmailMaskUseCaseInput(
    val id: String,
    val metadata: Map<String, String>? = null,
    val expiresAt: Date? = null,
)

/**
 * Use case for updating an email mask.
 *
 * This use case handles updating metadata and expiration date of an existing email mask.
 *
 * @property emailMaskService [EmailMaskService] Service for email mask operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property sealingService [SealingService] Service for sealing/encrypting data.
 * @property logger [Logger] Logger for debugging.
 */
internal class UpdateEmailMaskUseCase(
    private val emailMaskService: EmailMaskService,
    private val serviceKeyManager: ServiceKeyManager,
    private val sealingService: SealingService,
    private val logger: Logger,
) {
    /**
     * Executes the update email mask use case.
     *
     * @param input [UpdateEmailMaskUseCaseInput] The input parameters.
     * @return [SealedEmailMaskEntity] The updated email mask.
     * @throws SudoEmailClient.EmailMaskException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: UpdateEmailMaskUseCaseInput): UnsealedEmailMaskEntity {
        logger.debug("UpdateEmailMaskUseCase execute input: $input")
        try {
            var clearMetadata = false
            val metadata =
                if (input.metadata != null) {
                    if (input.metadata.isEmpty()) {
                        clearMetadata = true
                        null
                    } else {
                        // Ensure symmetric key has been generated
                        var symmetricKeyId = this.serviceKeyManager.getCurrentSymmetricKeyId()
                        if (symmetricKeyId == null) {
                            symmetricKeyId = this.serviceKeyManager.generateNewCurrentSymmetricKey()
                        }

                        val jsonString =
                            input.metadata.entries.joinToString(
                                separator = ",",
                                prefix = "{",
                                postfix = "}",
                            ) { (key, value) ->
                                "\"$key\":\"$value\""
                            }
                        val sealedMetadataBytes = sealingService.sealString(symmetricKeyId, jsonString.toByteArray())
                        val base64EncodedSealedData = String(Base64.encode(sealedMetadataBytes), Charsets.UTF_8)
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
            var clearExpiresAt = false
            val expiresAt =
                if (input.expiresAt == null) {
                    input.expiresAt
                } else {
                    if (input.expiresAt.time == 0L) {
                        clearExpiresAt = true
                        null
                    } else {
                        input.expiresAt
                    }
                }

            val updateEmailMaskRequest =
                UpdateEmailMaskRequest(
                    id = input.id,
                    metadata = metadata,
                    clearMetadata = clearMetadata,
                    expiresAt = expiresAt,
                    clearExpiresAt = clearExpiresAt,
                )

            val updatedEmailMask = emailMaskService.update(updateEmailMaskRequest)
            val emailMaskUnsealer =
                EmailMaskUnsealer(
                    this.serviceKeyManager,
                )
            try {
                val unsealed = emailMaskUnsealer.unseal(updatedEmailMask)
                return unsealed
            } catch (e: Exception) {
                logger.warning("UpdateEmailMaskUseCase failed to unseal email mask ${updatedEmailMask.id}: $e")
                val partialEmailMask =
                    EmailMaskTransformer
                        .toUnsealedEntity(updatedEmailMask, input.metadata)
                return partialEmailMask
            }
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMaskException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailMaskException(e)
            }
        }
    }
}
