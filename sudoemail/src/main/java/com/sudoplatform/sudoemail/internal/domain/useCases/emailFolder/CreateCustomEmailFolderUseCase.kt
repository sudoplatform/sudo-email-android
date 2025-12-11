/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.mechanisms.EmailFolderUnsealer
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.CreateCustomEmailFolderRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.EmailFolderService
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.UnsealedEmailFolderEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudologging.Logger

/**
 * Input for the create custom email folder use case.
 *
 * @property emailAddressId [String] The email address ID to create the folder for.
 * @property customFolderName [String] The name of the custom folder to create.
 */
internal data class CreateCustomEmailFolderUseCaseInput(
    val emailAddressId: String,
    val customFolderName: String,
)

/**
 * Use case for creating a custom email folder.
 *
 * This use case handles creating a new custom folder for an email address.
 *
 * @property emailFolderService [EmailFolderService] Service for email folder operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property sealingService [SealingService] Service for sealing/encrypting data.
 * @property logger [Logger] Logger for debugging.
 */
internal class CreateCustomEmailFolderUseCase(
    private val emailFolderService: EmailFolderService,
    private val serviceKeyManager: ServiceKeyManager,
    private val sealingService: SealingService,
    private val logger: Logger,
) {
    /**
     * Executes the create custom email folder use case.
     *
     * @param input [CreateCustomEmailFolderUseCaseInput] The input parameters.
     * @return [UnsealedEmailFolderEntity] The created unsealed email folder.
     * @throws KeyNotFoundException if the encryption key is not found.
     * @throws SudoEmailClient.EmailFolderException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: CreateCustomEmailFolderUseCaseInput): UnsealedEmailFolderEntity {
        logger.debug("CreateCustomEmailFolderUseCase execute input: $input")
        val symmetricKeyId =
            this.serviceKeyManager.getCurrentSymmetricKeyId()
                ?: throw KeyNotFoundException(StringConstants.SYMMETRIC_KEY_NOT_FOUND_ERROR_MSG)
        try {
            val emailFolderUnsealer = EmailFolderUnsealer(this.serviceKeyManager)
            val sealedCustomFolderNameBytes =
                sealingService.sealString(
                    symmetricKeyId,
                    input.customFolderName.toByteArray(),
                )
            val sealedCustomFolderNameData = String(Base64.encode(sealedCustomFolderNameBytes))
            val sealedCustomFolderName =
                SealedAttributeInput(
                    algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                    keyId = symmetricKeyId,
                    plainTextType = "string",
                    base64EncodedSealedData = sealedCustomFolderNameData,
                )

            val createCustomEmailFolderRequest =
                CreateCustomEmailFolderRequest(
                    emailAddressId = input.emailAddressId,
                    customFolderName = sealedCustomFolderName,
                )
            val sealedEmailFolder =
                emailFolderService.createCustom(
                    createCustomEmailFolderRequest,
                )
            return emailFolderUnsealer.unseal(sealedEmailFolder)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailFolderException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailFolderException(e)
            }
        }
    }
}
