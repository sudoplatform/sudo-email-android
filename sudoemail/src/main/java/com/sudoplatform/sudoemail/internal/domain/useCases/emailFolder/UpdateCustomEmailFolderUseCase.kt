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
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.EmailFolderService
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.UnsealedEmailFolderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.UpdateCustomEmailFolderRequest
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudologging.Logger

/**
 * Input for the update custom email folder use case.
 *
 * @property emailAddressId [String] The email address ID that owns the folder.
 * @property emailFolderId [String] The ID of the folder to update.
 * @property customFolderName [String] Optional new name for the custom folder.
 */
internal data class UpdateCustomEmailFolderUseCaseInput(
    val emailAddressId: String,
    val emailFolderId: String,
    val customFolderName: String?,
)

/**
 * Use case for updating a custom email folder.
 *
 * This use case handles updating the name of an existing custom email folder.
 *
 * @property emailFolderService [EmailFolderService] Service for email folder operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property sealingService [SealingService] Service for sealing/encrypting data.
 * @property logger [Logger] Logger for debugging.
 */
internal class UpdateCustomEmailFolderUseCase(
    private val emailFolderService: EmailFolderService,
    private val serviceKeyManager: ServiceKeyManager,
    private val sealingService: SealingService,
    private val logger: Logger,
) {
    /**
     * Executes the update custom email folder use case.
     *
     * @param input [UpdateCustomEmailFolderUseCaseInput] The input parameters.
     * @return [UnsealedEmailFolderEntity] The updated unsealed email folder.
     * @throws KeyNotFoundException if the encryption key is not found.
     * @throws SudoEmailClient.EmailFolderException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: UpdateCustomEmailFolderUseCaseInput): UnsealedEmailFolderEntity {
        logger.debug("UpdateCustomEmailFolderUseCase execute input: $input")
        val symmetricKeyId =
            this.serviceKeyManager.getCurrentSymmetricKeyId()
                ?: throw KeyNotFoundException(StringConstants.SYMMETRIC_KEY_NOT_FOUND_ERROR_MSG)
        try {
            val emailFolderUnsealer = EmailFolderUnsealer(this.serviceKeyManager)
            var sealedFolderName: SealedAttributeInput? = null
            if (input.customFolderName != null) {
                val sealedCustomFolderNameBytes =
                    sealingService.sealString(
                        symmetricKeyId,
                        input.customFolderName.toByteArray(),
                    )
                val sealedCustomFolderNameData = String(Base64.encode(sealedCustomFolderNameBytes))
                sealedFolderName =
                    SealedAttributeInput(
                        algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                        keyId = symmetricKeyId,
                        plainTextType = "string",
                        base64EncodedSealedData = sealedCustomFolderNameData,
                    )
            }

            val updateCustomEmailFolderRequest =
                UpdateCustomEmailFolderRequest(
                    emailAddressId = input.emailAddressId,
                    emailFolderId = input.emailFolderId,
                    customFolderName = sealedFolderName,
                )
            val sealedEmailFolder =
                emailFolderService.updateCustom(
                    updateCustomEmailFolderRequest,
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
