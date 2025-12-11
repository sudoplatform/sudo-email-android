/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.emailMessage.transformers.EmailMessageTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.GetEmailMessageRequest
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudologging.Logger

/**
 * Input for the get email message use case.
 *
 * @property id [String] The ID of the email message to retrieve.
 */
internal data class GetEmailMessageUseCaseInput(
    val id: String,
)

/**
 * Use case for retrieving an email message.
 *
 * This use case retrieves and unseals (decrypts) an email message by its ID.
 *
 * @property emailMessageService [EmailMessageService] Service for email message operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property logger [Logger] Logger for debugging.
 */
internal class GetEmailMessageUseCase(
    private val emailMessageService: EmailMessageService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the get email message use case.
     *
     * @param input [GetEmailMessageUseCaseInput] The input parameters.
     * @return [EmailMessageEntity] The unsealed email message, or null if not found.
     */
    suspend fun execute(input: GetEmailMessageUseCaseInput): EmailMessageEntity? {
        logger.debug("GetEmailMessageUseCase: executing with input $input")
        try {
            val emailMessage =
                emailMessageService.get(
                    GetEmailMessageRequest(id = input.id),
                ) ?: return null
            return EmailMessageTransformer.sealedEntityToUnsealedEntity(serviceKeyManager, emailMessage)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMessageException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailMessageException(e)
            }
        }
    }
}
