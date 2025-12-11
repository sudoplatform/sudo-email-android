/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduleSendDraftMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.GetEmailAddressRequest
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import java.util.Date

/**
 * Input for the schedule send draft message use case.
 *
 * @property id [String] The ID of the draft message to schedule.
 * @property emailAddressId [String] The email address ID associated with the draft.
 * @property sendAt [Date] The date and time to send the message.
 */
internal data class ScheduleSendDraftMessageUseCaseInput(
    val id: String,
    val emailAddressId: String,
    val sendAt: Date,
)

/**
 * Use case for scheduling a draft email message to be sent.
 *
 * This use case schedules a draft message to be sent at a specified future time.
 *
 * @property draftEmailMessageService [DraftEmailMessageService] Service for draft message operations.
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property sudoUserClient [SudoUserClient] Client for user operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property logger [Logger] Logger for debugging.
 */
internal class ScheduleSendDraftMessageUseCase(
    private val draftEmailMessageService: DraftEmailMessageService,
    private val emailAddressService: EmailAddressService,
    private val sudoUserClient: SudoUserClient,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the schedule send draft message use case.
     *
     * @param input [ScheduleSendDraftMessageUseCaseInput] The input parameters.
     * @return [ScheduledDraftMessageEntity] The scheduled draft message entity.
     * @throws SudoEmailClient.EmailAddressException.EmailAddressNotFoundException if the email address is not found.
     * @throws SudoEmailClient.EmailMessageException.InvalidArgumentException if the send time is in the past.
     * @throws KeyNotFoundException if the encryption key is not found.
     */
    suspend fun execute(input: ScheduleSendDraftMessageUseCaseInput): ScheduledDraftMessageEntity {
        logger.debug("ScheduleSendDraftMessageUseCase execute input: $input")
        if (emailAddressService.get(GetEmailAddressRequest(input.emailAddressId)) == null) {
            throw SudoEmailClient.EmailAddressException.EmailAddressNotFoundException(
                StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG,
            )
        }

        if (!input.sendAt.after(Date())) {
            throw SudoEmailClient.EmailMessageException.InvalidArgumentException("sendAt must be in the future")
        }

        try {
            val s3Key = DefaultS3Client.constructS3KeyForDraftEmailMessage(input.emailAddressId, input.id)

            val (keyId) = draftEmailMessageService.getObjectMetadata(s3Key)

            val symmetricKeyData = serviceKeyManager.getSymmetricKeyData(keyId)

            if (symmetricKeyData === null) {
                throw KeyNotFoundException("Could not find symmetric key $keyId")
            }

            return draftEmailMessageService.scheduleSend(
                ScheduleSendDraftMessageRequest(
                    draftMessageKey = "${sudoUserClient.getCredentialsProvider().identityId}/$s3Key",
                    emailAddressId = input.emailAddressId,
                    sendAt = input.sendAt,
                    symmetricKey = symmetricKeyData.encodeBase64String(),
                ),
            )
        } catch (e: Throwable) {
            when (e) {
                is SudoEmailClient.EmailMessageException ->
                    throw e
                is KeyNotFoundException ->
                    throw e
                else -> {
                    logger.error("unexpected error $e")
                    throw ErrorTransformer.interpretEmailMessageException(e)
                }
            }
        }
    }
}
