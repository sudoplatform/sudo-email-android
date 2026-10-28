/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.internal.data.common.mechanisms.EmailAliasUnsealer
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.emailAlias.transformers.EmailAliasTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.SealedEmailAliasEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.UnsealedEmailAliasEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.UpdateEmailAliasRequest
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudologging.Logger
import java.util.Date

/**
 * Input for the update email alias use case.
 *
 * @property id [String] The ID of the email alias to update.
 * @property metadata [String] Optional new metadata for the email alias. If the empty map is provided, this will clear the metadata.
 * @property expiresAt [Date] Optional new expiration date for the email alias. If a zero date is provided, this will clear the expiration date
 * @property realAddress [String] Optional real email address for the email alias. Only valid for external aliases in a pending state.
 */
internal data class UpdateEmailAliasUseCaseInput(
    val id: String,
    val metadata: Map<String, String>? = null,
    val expiresAt: Date? = null,
    val realAddress: String? = null,
)

/**
 * Use case for updating an email alias.
 *
 * This use case handles updating metadata and expiration date of an existing email alias.
 *
 * @property emailAliasService [EmailAliasService] Service for email alias operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property sealingService [SealingService] Service for sealing/encrypting data.
 * @property logger [Logger] Logger for debugging.
 */
internal class UpdateEmailAliasUseCase(
    private val emailAliasService: EmailAliasService,
    private val serviceKeyManager: ServiceKeyManager,
    private val sealingService: SealingService,
    private val logger: Logger,
) {
    /**
     * Executes the update email alias use case.
     *
     * @param input [UpdateEmailAliasUseCaseInput] The input parameters.
     * @return [SealedEmailAliasEntity] The updated email alias.
     * @throws SudoEmailClient.EmailAliasException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: UpdateEmailAliasUseCaseInput): UnsealedEmailAliasEntity {
        logger.debug("UpdateEmailAliasUseCase execute input: $input")
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

            val updateEmailAliasRequest =
                UpdateEmailAliasRequest(
                    id = input.id,
                    metadata = metadata,
                    clearMetadata = clearMetadata,
                    expiresAt = expiresAt,
                    clearExpiresAt = clearExpiresAt,
                    realAddress = input.realAddress,
                )

            val updatedEmailAlias = emailAliasService.update(updateEmailAliasRequest)
            val emailAliasUnsealer =
                EmailAliasUnsealer(
                    this.serviceKeyManager,
                )
            try {
                val unsealed = emailAliasUnsealer.unseal(updatedEmailAlias)
                return unsealed
            } catch (e: Exception) {
                logger.warning("UpdateEmailAliasUseCase failed to unseal email alias ${updatedEmailAlias.id}: $e")
                val partialEmailAlias =
                    EmailAliasTransformer
                        .toUnsealedEntity(updatedEmailAlias, input.metadata)
                return partialEmailAlias
            }
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAliasException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAliasException(e)
            }
        }
    }
}
