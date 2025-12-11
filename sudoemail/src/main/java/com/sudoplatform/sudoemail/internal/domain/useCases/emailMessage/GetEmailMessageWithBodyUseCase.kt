/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageWithBodyEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.GetEmailMessageRequest
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.types.LEGACY_BODY_CONTENT_ID
import com.sudoplatform.sudoemail.secure.types.LEGACY_KEY_EXCHANGE_CONTENT_ID
import com.sudoplatform.sudoemail.secure.types.SecureEmailAttachmentType
import com.sudoplatform.sudoemail.secure.types.SecurePackage
import com.sudoplatform.sudologging.Logger

/**
 * Input for the get email message with body use case.
 *
 * @property id [String] The ID of the email message.
 * @property emailAddressId [String] The email address ID associated with the message.
 */
internal data class GetEmailMessageWithBodyUseCaseInput(
    val id: String,
    val emailAddressId: String,
)

/**
 * Use case for retrieving an email message with its body content.
 *
 * This use case retrieves an email message and includes its decoded body and attachments.
 *
 * @property emailMessageService [EmailMessageService] Service for email message operations.
 * @property s3EmailClient [S3Client] Client for S3 email bucket operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property emailMessageDataProcessor [EmailMessageDataProcessor] Processor for email message data.
 * @property emailCryptoService [EmailCryptoService] Service for email cryptographic operations.
 * @property logger [Logger] Logger for debugging.
 * @property retrieveAndDecodeEmailMessageUseCase [RetrieveAndDecodeEmailMessageUseCase] Use case for retrieving and decoding email
 *  messages.
 */
internal class GetEmailMessageWithBodyUseCase(
    private val emailMessageService: EmailMessageService,
    private val s3EmailClient: S3Client,
    private val serviceKeyManager: ServiceKeyManager,
    private val emailMessageDataProcessor: EmailMessageDataProcessor,
    private val emailCryptoService: EmailCryptoService,
    private val logger: Logger,
    private val retrieveAndDecodeEmailMessageUseCase: RetrieveAndDecodeEmailMessageUseCase =
        RetrieveAndDecodeEmailMessageUseCase(
            s3EmailClient = s3EmailClient,
            serviceKeyManager = serviceKeyManager,
            logger = logger,
        ),
) {
    /**
     * Executes the get email message with body use case.
     *
     * @param input [GetEmailMessageWithBodyUseCaseInput] The input parameters.
     * @return [EmailMessageWithBodyEntity] The email message with body, or null if not found.
     * @throws SudoEmailClient.EmailMessageException.AuthenticationException if authentication fails.
     * @throws SudoEmailClient.EmailMessageException.UnsealingException if decryption fails.
     */
    suspend fun execute(input: GetEmailMessageWithBodyUseCaseInput): EmailMessageWithBodyEntity? {
        logger.debug("Getting email message with body for email message ID: ${input.id}")
        try {
            val sealedEmailMessage =
                emailMessageService.get(
                    GetEmailMessageRequest(id = input.id),
                ) ?: return null
            val decodedBytes =
                retrieveAndDecodeEmailMessageUseCase.execute(
                    sealedEmailMessage,
                )

            var parsedMessage = emailMessageDataProcessor.parseInternetMessageData(decodedBytes)
            if (sealedEmailMessage.encryptionStatus == EncryptionStatusEntity.ENCRYPTED) {
                val keyAttachments =
                    parsedMessage.attachments.filter {
                        it.contentId.contains(SecureEmailAttachmentType.KEY_EXCHANGE.contentId) ||
                            it.contentId.contains(LEGACY_KEY_EXCHANGE_CONTENT_ID)
                    }
                if (keyAttachments.isEmpty()) {
                    throw SudoEmailClient.EmailMessageException.FailedException(
                        StringConstants.KEY_ATTACHMENTS_NOT_FOUND_ERROR_MSG,
                    )
                }
                val bodyAttachment =
                    parsedMessage.attachments.filter {
                        it.contentId.contains(SecureEmailAttachmentType.BODY.contentId) ||
                            it.contentId.contains(LEGACY_BODY_CONTENT_ID)
                    }
                if (bodyAttachment.isEmpty()) {
                    throw SudoEmailClient.EmailMessageException.FailedException(
                        StringConstants.BODY_ATTACHMENT_NOT_FOUND_ERROR_MSG,
                    )
                }
                val securePackage = SecurePackage(keyAttachments.toSet(), bodyAttachment.first())
                val unencryptedMessage = emailCryptoService.decrypt(securePackage)
                parsedMessage =
                    emailMessageDataProcessor.parseInternetMessageData(unencryptedMessage)
            }
            return EmailMessageWithBodyEntity(
                id = sealedEmailMessage.id,
                body = parsedMessage.body ?: "",
                isHtml = parsedMessage.isHtml,
                attachments = parsedMessage.attachments,
                inlineAttachments = parsedMessage.inlineAttachments,
            )
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
