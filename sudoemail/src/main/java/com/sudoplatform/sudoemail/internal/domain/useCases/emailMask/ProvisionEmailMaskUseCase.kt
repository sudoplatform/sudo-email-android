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
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.emailMask.transformers.EmailMaskTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.ProvisionEmailMaskRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.SealedEmailMaskEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.UnsealedEmailMaskEntity
import com.sudoplatform.sudoemail.keys.KeyPair
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudologging.Logger

/**
 * Input for the provision email mask use case.
 *
 * @property maskAddress [String] The email mask address to provision.
 * @property realAddress [String] The real email address to map to the mask.
 * @property ownershipProofToken [String] The token proving ownership of the sudo.
 * @property metadata [Map<String, String>] Optional metadata for the email mask.
 * @property expiresAt [java.util.Date] Optional expiration date for the email mask.
 * @property keyId [String] Optional key ID to use for encryption.
 */
internal data class ProvisionEmailMaskUseCaseInput(
    val maskAddress: String,
    val realAddress: String,
    val ownershipProofToken: String,
    val metadata: Map<String, String>? = null,
    val expiresAt: java.util.Date? = null,
    val keyId: String? = null,
)

/**
 * Use case for provisioning a new email mask.
 *
 * This use case handles creating a new email mask with optional metadata encryption and expiration.
 *
 * @property emailMaskService [EmailMaskService] Service for email mask operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property sealingService [SealingService] Service for sealing/encrypting data.
 * @property logger [Logger] Logger for debugging.
 */
internal class ProvisionEmailMaskUseCase(
    private val emailMaskService: EmailMaskService,
    private val serviceKeyManager: ServiceKeyManager,
    private val sealingService: SealingService,
    private val logger: Logger,
) {
    /**
     * Executes the provision email mask use case.
     *
     * @param input [ProvisionEmailMaskUseCaseInput] The input parameters.
     * @return [SealedEmailMaskEntity] The provisioned email mask.
     * @throws SudoEmailClient.EmailMaskException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: ProvisionEmailMaskUseCaseInput): UnsealedEmailMaskEntity {
        logger.debug("ProvisionEmailMaskUseCase execute input: $input")
        try {
            val keyPair: KeyPair =
                if (input.keyId != null) {
                    val id = input.keyId
                    this.serviceKeyManager.getKeyPairWithId(id) ?: throw KeyNotFoundException(
                        StringConstants.PUBLIC_KEY_NOT_FOUND_ERROR_MSG,
                    )
                } else {
                    this.serviceKeyManager.generateKeyPair()
                }

            val metadata =
                if (input.metadata != null && input.metadata.isNotEmpty()) {
                    // Ensure symmetric key has been generated
                    var symmetricKeyId = this.serviceKeyManager.getCurrentSymmetricKeyId()
                    if (symmetricKeyId == null) {
                        symmetricKeyId = this.serviceKeyManager.generateNewCurrentSymmetricKey()
                    }

                    // Convert Map<String, String> to JSON string
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
                } else {
                    null
                }

            var expiresAt =
                if (input.expiresAt != null && input.expiresAt.time == 0L) {
                    null
                } else {
                    input.expiresAt
                }

            val provisionEmailMaskRequest =
                ProvisionEmailMaskRequest(
                    maskAddress = input.maskAddress,
                    realAddress = input.realAddress,
                    ownershipProofToken = input.ownershipProofToken,
                    metadata = metadata,
                    expiresAt = expiresAt,
                    keyPair = keyPair,
                )

            val provisionedEmailMask = emailMaskService.provision(provisionEmailMaskRequest)
            return EmailMaskTransformer.toUnsealedEntity(provisionedEmailMask, input.metadata)
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
