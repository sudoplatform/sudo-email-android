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
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.emailAlias.transformers.EmailAliasTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.ProvisionEmailAliasRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.SealedEmailAliasEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.UnsealedEmailAliasEntity
import com.sudoplatform.sudoemail.keys.KeyPair
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudologging.Logger

/**
 * Input for the provision email alias use case.
 *
 * @property aliasAddress [String] The email alias address to provision.
 * @property realAddress [String] The real email address to map to the alias.
 * @property ownershipProofToken [String] The token proving ownership of the sudo.
 * @property metadata [Map<String, String>] Optional metadata for the email alias.
 * @property expiresAt [java.util.Date] Optional expiration date for the email alias.
 * @property keyId [String] Optional key ID to use for encryption.
 */
internal data class ProvisionEmailAliasUseCaseInput(
    val aliasAddress: String,
    val realAddress: String,
    val ownershipProofToken: String,
    val metadata: Map<String, String>? = null,
    val expiresAt: java.util.Date? = null,
    val keyId: String? = null,
)

/**
 * Use case for provisioning a new email alias.
 *
 * This use case handles creating a new email alias with optional metadata encryption and expiration.
 *
 * @property emailAliasService [EmailAliasService] Service for email alias operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property sealingService [SealingService] Service for sealing/encrypting data.
 * @property logger [Logger] Logger for debugging.
 */
internal class ProvisionEmailAliasUseCase(
    private val emailAliasService: EmailAliasService,
    private val serviceKeyManager: ServiceKeyManager,
    private val sealingService: SealingService,
    private val logger: Logger,
) {
    /**
     * Executes the provision email alias use case.
     *
     * @param input [ProvisionEmailAliasUseCaseInput] The input parameters.
     * @return [SealedEmailAliasEntity] The provisioned email alias.
     * @throws SudoEmailClient.EmailAliasException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: ProvisionEmailAliasUseCaseInput): UnsealedEmailAliasEntity {
        logger.debug("ProvisionEmailAliasUseCase execute input: $input")
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

            val provisionEmailAliasRequest =
                ProvisionEmailAliasRequest(
                    aliasAddress = input.aliasAddress,
                    realAddress = input.realAddress,
                    ownershipProofToken = input.ownershipProofToken,
                    metadata = metadata,
                    expiresAt = expiresAt,
                    keyPair = keyPair,
                )

            val provisionedEmailAlias = emailAliasService.provision(provisionEmailAliasRequest)
            return EmailAliasTransformer.toUnsealedEntity(provisionedEmailAlias, input.metadata)
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
