/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.draftMessage.transformers.DraftEmailMessageTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageWithContentEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.GetDraftEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.GetEmailAddressRequest
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.secure.types.LEGACY_BODY_CONTENT_ID
import com.sudoplatform.sudoemail.secure.types.LEGACY_KEY_EXCHANGE_CONTENT_ID
import com.sudoplatform.sudoemail.secure.types.SecureEmailAttachmentType
import com.sudoplatform.sudoemail.secure.types.SecurePackage
import com.sudoplatform.sudologging.Logger

/**
 * Input for the get draft email message use case.
 *
 * @property draftId [String] The ID of the draft message to retrieve.
 * @property emailAddressId [String] The email address ID associated with the draft.
 */
internal data class GetDraftEmailMessageUseCaseInput(
    val draftId: String,
    val emailAddressId: String,
)

/**
 * Use case for retrieving a draft email message.
 *
 * This use case handles retrieving and decrypting a draft email message from S3.
 *
 * @property draftEmailMessageService [DraftEmailMessageService] Service for draft message operations.
 * @property sealingService [SealingService] Service for unsealing/decrypting data.
 * @property emailMessageDataProcessor [EmailMessageDataProcessor] Processor for email message data.
 * @property emailCryptoService [EmailCryptoService] Service for email cryptographic operations.
 * @property logger [Logger] Logger for debugging.
 * @property emailAddressService [EmailAddressService] Optional Service for email address operations. If passed,
 * used to validate existence of email address.
 */
internal class GetDraftEmailMessageUseCase(
    private val draftEmailMessageService: DraftEmailMessageService,
    private val sealingService: SealingService,
    private val emailMessageDataProcessor: EmailMessageDataProcessor,
    private val emailCryptoService: EmailCryptoService,
    private val logger: Logger,
    private val emailAddressService: EmailAddressService?,
) {
    /**
     * Executes the get draft email message use case.
     *
     * @param input [GetDraftEmailMessageUseCaseInput] The input parameters.
     * @return [DraftEmailMessageWithContentEntity] The draft message with its content.
     * @throws SudoEmailClient.EmailAddressException.EmailAddressNotFoundException if the email address is not found.
     * @throws SudoEmailClient.EmailMessageException.EmailMessageNotFoundException if the draft message is not found.
     */
    suspend fun execute(input: GetDraftEmailMessageUseCaseInput): DraftEmailMessageWithContentEntity {
        logger.debug("GetDraftEmailMessageUseCase: Executing with input: $input")
        if (emailAddressService != null && emailAddressService.get(GetEmailAddressRequest(input.emailAddressId)) == null) {
            throw SudoEmailClient.EmailAddressException.EmailAddressNotFoundException(
                StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG,
            )
        }

        val s3Key =
            DefaultS3Client.constructS3KeyForDraftEmailMessage(
                input.emailAddressId,
                input.draftId,
            )

        val draftMessageInfo =
            draftEmailMessageService.get(
                GetDraftEmailMessageRequest(
                    s3Key = s3Key,
                ),
            )
        try {
            var unsealedRfc822Data =
                DraftEmailMessageTransformer.toDecodedAndDecryptedRfc822Data(
                    sealingService,
                    draftMessageInfo.rfc822Data,
                    draftMessageInfo.keyId,
                )
            val parsedMessage = emailMessageDataProcessor.parseInternetMessageData(unsealedRfc822Data)

            // Check if the draft is E2EE encrypted, and if so, decrypt it
            val keyAttachments =
                parsedMessage.attachments.filter {
                    it.contentId.contains(SecureEmailAttachmentType.KEY_EXCHANGE.contentId) ||
                        it.contentId.contains(LEGACY_KEY_EXCHANGE_CONTENT_ID)
                }

            if (keyAttachments.isNotEmpty()) {
                // Draft was E2EE, so decrypt it
                val bodyAttachments =
                    parsedMessage.attachments.filter {
                        it.contentId.contains(SecureEmailAttachmentType.BODY.contentId) ||
                            it.contentId.contains(LEGACY_BODY_CONTENT_ID)
                    }
                if (bodyAttachments.isEmpty()) {
                    throw SudoEmailClient.EmailMessageException.FailedException(
                        StringConstants.BODY_ATTACHMENT_NOT_FOUND_ERROR_MSG,
                    )
                }
                val securePackage = SecurePackage(keyAttachments.toSet(), bodyAttachments.first())
                unsealedRfc822Data = emailCryptoService.decrypt(securePackage)
            }

            return DraftEmailMessageWithContentEntity(
                id = input.draftId,
                emailAddressId = input.emailAddressId,
                updatedAt = draftMessageInfo.updatedAt,
                rfc822Data = unsealedRfc822Data,
            )
        } catch (e: Throwable) {
            logger.error(e.message)
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }
}
