/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.mechanisms.EmailFolderUnsealer
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.DeleteCustomEmailFolderRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.EmailFolderService
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.UnsealedEmailFolderEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudologging.Logger

/**
 * Input for the delete custom email folder use case.
 *
 * @property emailFolderId [String] The ID of the folder to delete.
 * @property emailAddressId [String] The email address ID that owns the folder.
 */
internal data class DeleteCustomEmailFolderUseCaseInput(
    val emailFolderId: String,
    val emailAddressId: String,
)

/**
 * Use case for deleting a custom email folder.
 *
 * This use case handles deleting an existing custom email folder.
 *
 * @property emailFolderService [EmailFolderService] Service for email folder operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property logger [Logger] Logger for debugging.
 */
internal class DeleteCustomEmailFolderUseCase(
    private val emailFolderService: EmailFolderService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the delete custom email folder use case.
     *
     * @param input [DeleteCustomEmailFolderUseCaseInput] The input parameters.
     * @return [UnsealedEmailFolderEntity] The deleted unsealed email folder, or null if not found.
     * @throws SudoEmailClient.EmailFolderException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: DeleteCustomEmailFolderUseCaseInput): UnsealedEmailFolderEntity? {
        logger.debug("DeleteCustomEmailFolderUseCase execute input: $input")
        try {
            val emailFolderUnsealer = EmailFolderUnsealer(this.serviceKeyManager)
            val deleteCustomEmailFolderRequest =
                DeleteCustomEmailFolderRequest(
                    emailFolderId = input.emailFolderId,
                    emailAddressId = input.emailAddressId,
                )
            val sealedEmailFolder = emailFolderService.deleteCustom(deleteCustomEmailFolderRequest)
            return sealedEmailFolder?.let { emailFolderUnsealer.unseal(it) }
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
