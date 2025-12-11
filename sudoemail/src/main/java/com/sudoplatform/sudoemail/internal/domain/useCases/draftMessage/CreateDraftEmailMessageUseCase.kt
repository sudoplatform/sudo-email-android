/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.GetEmailAddressRequest
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudologging.Logger
import java.util.UUID

/**
 * Input for the create draft email message use case.
 *
 * @property emailAddressId [String] The email address ID to associate with the draft.
 * @property rfc822Data [ByteArray] The RFC822 formatted email message data.
 */
internal data class CreateDraftEmailMessageUseCaseInput(
    val emailAddressId: String,
    val rfc822Data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CreateDraftEmailMessageUseCaseInput

        if (!rfc822Data.contentEquals(other.rfc822Data)) return false
        return emailAddressId == other.emailAddressId
    }

    override fun hashCode(): Int {
        var result = rfc822Data.contentHashCode()
        result = 31 * result + emailAddressId.hashCode()
        return result
    }
}

/**
 * Use case for creating a draft email message.
 *
 * This use case handles creating a new draft email message by encrypting and storing
 * the RFC822 data in S3.
 *
 * @property draftEmailMessageService [DraftEmailMessageService] Service for draft message operations.
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property configurationDataService [ConfigurationDataService] Service for configuration data.
 * @property emailMessageDataProcessor [EmailMessageDataProcessor] Processor for email message data.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property sealingService [SealingService] Service for sealing/encrypting data.
 * @property logger [Logger] Logger for debugging.
 * @property saveDraftEmailMessageUseCase [SaveDraftEmailMessageUseCase] Use case for saving draft email messages.
 */
internal class CreateDraftEmailMessageUseCase(
    private val draftEmailMessageService: DraftEmailMessageService,
    private val emailAddressService: EmailAddressService,
    private val configurationDataService: ConfigurationDataService,
    private val emailMessageDataProcessor: EmailMessageDataProcessor,
    private val serviceKeyManager: ServiceKeyManager,
    private val sealingService: SealingService,
    private val logger: Logger,
    private val saveDraftEmailMessageUseCase: SaveDraftEmailMessageUseCase =
        SaveDraftEmailMessageUseCase(
            draftEmailMessageService = draftEmailMessageService,
            emailAddressService = emailAddressService,
            emailMessageDataProcessor = emailMessageDataProcessor,
            configurationDataService = configurationDataService,
            sealingService = sealingService,
            logger = logger,
        ),
) {
    /**
     * Executes the create draft email message use case.
     *
     * @param input [CreateDraftEmailMessageUseCaseInput] The input parameters.
     * @return [String] The ID of the created draft message.
     * @throws SudoEmailClient.EmailAddressException.EmailAddressNotFoundException if the email address is not found.
     * @throws KeyNotFoundException if the encryption key is not found.
     */
    suspend fun execute(input: CreateDraftEmailMessageUseCaseInput): String {
        logger.debug("CreateDraftEmailMessageUseCase execute input: ${input.emailAddressId}")
        if (emailAddressService.get(GetEmailAddressRequest(input.emailAddressId)) == null) {
            throw SudoEmailClient.EmailAddressException.EmailAddressNotFoundException(
                StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG,
            )
        }
        val symmetricKeyId =
            this.serviceKeyManager.getCurrentSymmetricKeyId()
                ?: throw KeyNotFoundException(StringConstants.SYMMETRIC_KEY_NOT_FOUND_ERROR_MSG)

        val draftId = UUID.randomUUID().toString()

        val s3Key =
            DefaultS3Client.constructS3KeyForDraftEmailMessage(
                input.emailAddressId,
                draftId,
            )

        saveDraftEmailMessageUseCase.execute(
            SaveDraftEmailMessageUseCaseInput(
                rfc822Data = input.rfc822Data,
                symmetricKeyId = symmetricKeyId,
                s3Key = s3Key,
            ),
        )
        return draftId
    }
}
