/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageRfc822DataEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.GetEmailMessageRequest
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudologging.Logger

/**
 * Input for the get email message RFC822 data use case.
 *
 * @property id [String] The ID of the email message.
 * @property emailAddressId [String] The email address ID associated with the message.
 */
internal data class GetEmailMessageRfc822DataUseCaseInput(
    val id: String,
    val emailAddressId: String,
)

/**
 * Use case for retrieving the RFC822 data of an email message.
 *
 * This use case retrieves and decrypts the raw RFC822 formatted data of an email message.
 *
 * @property emailMessageService [EmailMessageService] Service for email message operations.
 * @property s3EmailClient [S3Client] Client for S3 email bucket operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property logger [Logger] Logger for debugging.
 * @property retrieveAndDecodeEmailMessageUseCase [RetrieveAndDecodeEmailMessageUseCase] Use case for retrieving and decoding email
 *  messages.
 */
internal class GetEmailMessageRfc822DataUseCase(
    private val emailMessageService: EmailMessageService,
    private val s3EmailClient: S3Client,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
    private val retrieveAndDecodeEmailMessageUseCase: RetrieveAndDecodeEmailMessageUseCase =
        RetrieveAndDecodeEmailMessageUseCase(
            s3EmailClient = s3EmailClient,
            serviceKeyManager = serviceKeyManager,
            logger = logger,
        ),
) {
    /**
     * Executes the get email message RFC822 data use case.
     *
     * @param input [GetEmailMessageRfc822DataUseCaseInput] The input parameters.
     * @return [EmailMessageRfc822DataEntity] The RFC822 data entity, or null if not found.
     * @throws SudoEmailClient.EmailMessageException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: GetEmailMessageRfc822DataUseCaseInput): EmailMessageRfc822DataEntity? {
        logger.debug("Getting email message RFC822 data for email message ID: ${input.id}")
        try {
            val sealedEmailMessage =
                emailMessageService.get(
                    GetEmailMessageRequest(id = input.id),
                ) ?: return null
            val decodedBytes =
                retrieveAndDecodeEmailMessageUseCase.execute(
                    sealedEmailMessage,
                )

            return EmailMessageRfc822DataEntity(input.id, decodedBytes)
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
