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
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.SaveDraftEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.LookupEmailAddressesPublicInfoUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.LookupEmailAddressesPublicInfoUseCaseInput
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudoemail.util.EmailAddressParser
import com.sudoplatform.sudologging.Logger

/**
 * Input for the save draft email message use case.
 *
 * @property rfc822Data [ByteArray] The RFC822 formatted email message data.
 * @property symmetricKeyId [String] The ID of the symmetric key used for encryption.
 * @property s3Key [String] The S3 key where the draft will be stored.
 */
internal data class SaveDraftEmailMessageUseCaseInput(
    val rfc822Data: ByteArray,
    val symmetricKeyId: String,
    val s3Key: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SaveDraftEmailMessageUseCaseInput

        if (!rfc822Data.contentEquals(other.rfc822Data)) return false
        if (symmetricKeyId != other.symmetricKeyId) return false
        return s3Key == other.s3Key
    }

    override fun hashCode(): Int {
        var result = rfc822Data.contentHashCode()
        result = 31 * result + s3Key.hashCode()
        result = 31 * result + symmetricKeyId.hashCode()
        return result
    }
}

/**
 * Use case for saving a draft email message.
 *
 * This use case handles saving a draft email message by processing the RFC822 data,
 * encrypting it if necessary, and storing it in S3.
 *
 * @property draftEmailMessageService [DraftEmailMessageService] Service for draft message operations.
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property emailMessageDataProcessor [EmailMessageDataProcessor] Processor for email message data.
 * @property configurationDataService [ConfigurationDataService] Service for configuration data.
 * @property sealingService [SealingService] Service for data sealing operations.
 * @property logger [Logger] Logger for logging operations.
 * @property lookupEmailAddressesPublicInfoUseCase [LookupEmailAddressesPublicInfoUseCase] Use case for looking up public info of email addresses.
 */
internal class SaveDraftEmailMessageUseCase(
    private val draftEmailMessageService: DraftEmailMessageService,
    private val emailAddressService: EmailAddressService,
    private val emailMessageDataProcessor: EmailMessageDataProcessor,
    private val configurationDataService: ConfigurationDataService,
    private val sealingService: SealingService,
    private val logger: Logger,
    private val lookupEmailAddressesPublicInfoUseCase: LookupEmailAddressesPublicInfoUseCase =
        LookupEmailAddressesPublicInfoUseCase(
            emailAddressService = emailAddressService,
            logger = logger,
        ),
) {
    /**
     * Executes the use case to save a draft email message.
     *
     * @param input [SaveDraftEmailMessageUseCaseInput] The input data for saving the draft.
     * @return [String] The S3 key where the draft email message is stored.
     * @throws SudoEmailClient.EmailMessageException if an error occurs during the operation.
     */
    suspend fun execute(input: SaveDraftEmailMessageUseCaseInput): String {
        logger.debug("SaveDraftEmailMessageUseCase: executing with input: $input")
        val (rfc822Data, symmetricKeyId, s3Key) = input
        var cleanRfc822Data = rfc822Data

        try {
            val configurationData = configurationDataService.getConfigurationData()
            val messageData = emailMessageDataProcessor.parseInternetMessageData(rfc822Data)
            // Process the RFC 822 data to ensure it is valid and encrypt if necessary

            configurationData.verifyAttachmentValidity(
                messageData.attachments,
                messageData.inlineAttachments,
            )
            val domains = configurationDataService.getConfiguredEmailDomains()

            // Now check if this might be an in-network message
            val allRecipients =
                mutableListOf<String>()
                    .apply {
                        addAll(messageData.to)
                        addAll(messageData.cc)
                        addAll(messageData.bcc)
                    }.map { EmailAddressParser.removeDisplayName(it) }

            // Check if all recipient domains are ours
            val allRecipientsInternal =
                allRecipients.isNotEmpty() &&
                    allRecipients.all { recipient ->
                        domains.any { domain ->
                            recipient.contains(domain)
                        }
                    }

            if (allRecipientsInternal) {
                // It is, so let's encrypt it
                if (allRecipients.size > configurationData.encryptedEmailMessageRecipientsLimit) {
                    throw SudoEmailClient.EmailMessageException.LimitExceededException(
                        "${StringConstants.RECIPIENT_LIMIT_EXCEEDED_ERROR_MSG}${configurationData.encryptedEmailMessageRecipientsLimit}",
                    )
                }
                val allRecipientsAndSender = allRecipients.toMutableList()
                allRecipientsAndSender.add(
                    EmailAddressParser.removeDisplayName(messageData.from[0]),
                )

                val emailAddressesPublicInfo =
                    lookupEmailAddressesPublicInfoUseCase.execute(
                        LookupEmailAddressesPublicInfoUseCaseInput(
                            addresses = allRecipientsAndSender,
                            throwIfNotAllInternal = true,
                        ),
                    )

                cleanRfc822Data =
                    emailMessageDataProcessor.processMessageData(
                        messageData,
                        EncryptionStatusEntity.ENCRYPTED,
                        emailAddressesPublicInfo,
                    )
            }

            val metadataObject =
                mapOf(
                    StringConstants.DRAFT_METADATA_KEY_ID_NAME to symmetricKeyId,
                    StringConstants.DRAFT_METADATA_ALGORITHM_NAME to SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                )

            val uploadData =
                DraftEmailMessageTransformer.toEncryptedAndEncodedRfc822Data(
                    sealingService,
                    cleanRfc822Data,
                    symmetricKeyId,
                )

            draftEmailMessageService.save(
                SaveDraftEmailMessageRequest(
                    uploadData = uploadData,
                    s3Key = s3Key,
                    metadataObject = metadataObject,
                ),
            )

            return s3Key
        } catch (e: Throwable) {
            logger.error(e.message)
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }
}
