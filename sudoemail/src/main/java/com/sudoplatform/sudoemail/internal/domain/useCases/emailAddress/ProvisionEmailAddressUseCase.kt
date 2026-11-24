/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.emailAddress.transformers.EmailAddressTransformer
import com.sudoplatform.sudoemail.internal.data.emailFolder.transformers.EmailFolderTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ProvisionEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.UnsealedEmailAddressEntity
import com.sudoplatform.sudoemail.keys.ProvisioningKeyProvider
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudoemail.util.isDefinitiveRejection
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudologging.Logger

/**
 * Input for the provision email address use case.
 *
 * @property emailAddress [String] The email address to provision.
 * @property ownershipProofToken [String] The token proving ownership of the sudo.
 * @property alias [String] Optional alias for the email address.
 * @property keyId [String] Optional key ID to use for the email address.
 */
internal data class ProvisionEmailAddressUseCaseInput(
    val emailAddress: String,
    val ownershipProofToken: String,
    val alias: String? = null,
    val keyId: String? = null,
)

/**
 * Use case for provisioning a new email address.
 *
 * This use case handles creating a new email address with encryption keys and optional alias.
 * The key pair is chosen by [keyProvider], and a key pair generated for the request is removed
 * again if the service definitely rejects it.
 *
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property sealingService [SealingService] Service for sealing/encrypting data.
 * @property logger [Logger] Logger for debugging.
 * @property keyProvider [ProvisioningKeyProvider] Chooses the key pair to provision with, and
 *  removes it if the provision is rejected.
 */
internal class ProvisionEmailAddressUseCase(
    private val emailAddressService: EmailAddressService,
    private val serviceKeyManager: ServiceKeyManager,
    private val sealingService: SealingService,
    private val logger: Logger,
    private val keyProvider: ProvisioningKeyProvider,
) {
    /**
     * Executes the provision email address use case.
     *
     * @param input [ProvisionEmailAddressUseCaseInput] The input parameters.
     * @return [UnsealedEmailAddressEntity] The provisioned email address.
     * @throws KeyNotFoundException if the encryption key is not found.
     * @throws SudoEmailClient.EmailAddressException.InvalidArgumentException if a key pair is
     *  supplied but the client doesn't allow caller managed keys.
     * @throws SudoEmailClient.EmailAddressException.SingletonKeyPairNotFoundException if the
     *  client enforces a singleton key pair that this device does not have.
     * @throws SudoEmailClient.EmailAddressException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: ProvisionEmailAddressUseCaseInput): UnsealedEmailAddressEntity {
        logger.debug("ProvisionEmailAddressUseCase execute input: $input")
        try {
            // Ensure symmetric key has been generated
            var symmetricKeyId = this.serviceKeyManager.getCurrentSymmetricKeyId()
            if (symmetricKeyId == null) {
                symmetricKeyId = this.serviceKeyManager.generateNewCurrentSymmetricKey()
            }

            val alias =
                if (input.alias !== null) {
                    val sealedAliasBytes = sealingService.sealString(symmetricKeyId, input.alias.toByteArray())
                    val base64EncodedSealedData = String(Base64.encode(sealedAliasBytes), Charsets.UTF_8)
                    SealedAttributeInput(
                        algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                        keyId = symmetricKeyId,
                        plainTextType = "string",
                        base64EncodedSealedData = base64EncodedSealedData,
                    )
                } else {
                    null
                }

            val key = keyProvider.key(input.keyId)

            val provisionEmailAddressRequest =
                ProvisionEmailAddressRequest(
                    emailAddress = input.emailAddress,
                    ownershipProofToken = input.ownershipProofToken,
                    alias = alias,
                    keyPair = key.keyPair,
                )
            val provisionedEmailAddress =
                try {
                    emailAddressService.provision(provisionEmailAddressRequest)
                } catch (e: Throwable) {
                    if (e.isDefinitiveRejection) {
                        keyProvider.discardRejectedKey(key)
                    }
                    throw e
                }

            val folders =
                provisionedEmailAddress.folders.map { folder ->
                    // Since this email address has just been provisioned there will not be a custom folder
                    // so we don't need to check that here
                    EmailFolderTransformer.toUnsealedEntity(folder)
                }
            return EmailAddressTransformer.toUnsealedEntity(provisionedEmailAddress, folders, input.alias)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAddressException(e)
            }
        }
    }
}
