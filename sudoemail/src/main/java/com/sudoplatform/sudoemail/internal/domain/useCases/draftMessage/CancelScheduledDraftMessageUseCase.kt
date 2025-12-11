/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.CancelScheduledDraftMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.GetEmailAddressRequest
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient

/**
 * Input for the cancel scheduled draft message use case.
 *
 * @property draftId [String] The ID of the scheduled draft message to cancel.
 * @property emailAddressId [String] The email address ID associated with the draft.
 */
internal data class CancelScheduledDraftMessageUseCaseInput(
    val draftId: String,
    val emailAddressId: String,
)

/**
 * Use case for canceling a scheduled draft email message.
 *
 * This use case cancels a previously scheduled draft message so it will not be sent.
 *
 * @property draftEmailMessageService [DraftEmailMessageService] Service for draft message operations.
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property sudoUserClient [SudoUserClient] Client for user operations.
 * @property logger [Logger] Logger for debugging.
 */
internal class CancelScheduledDraftMessageUseCase(
    private val draftEmailMessageService: DraftEmailMessageService,
    private val emailAddressService: EmailAddressService,
    private val sudoUserClient: SudoUserClient,
    private val logger: Logger,
) {
    /**
     * Executes the cancel scheduled draft message use case.
     *
     * @param input [CancelScheduledDraftMessageUseCaseInput] The input parameters.
     * @return [String] The ID of the canceled scheduled draft message.
     * @throws SudoEmailClient.EmailAddressException.EmailAddressNotFoundException if the email address is not found.
     */
    suspend fun execute(input: CancelScheduledDraftMessageUseCaseInput): String {
        logger.debug("CancelScheduledDraftMessageUseCase execute input: $input")
        if (emailAddressService.get(GetEmailAddressRequest(input.emailAddressId)) == null) {
            throw SudoEmailClient.EmailAddressException.EmailAddressNotFoundException(
                StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG,
            )
        }

        try {
            val s3Key = DefaultS3Client.constructS3KeyForDraftEmailMessage(input.emailAddressId, input.draftId)

            return draftEmailMessageService.cancelScheduledDraftMessage(
                CancelScheduledDraftMessageRequest(
                    draftMessageKey = "${sudoUserClient.getCredentialsProvider().identityId}/$s3Key",
                    emailAddressId = input.emailAddressId,
                ),
            )
        } catch (e: Throwable) {
            when (e) {
                is SudoEmailClient.EmailMessageException ->
                    throw e
                else -> {
                    logger.error("unexpected error $e")
                    throw ErrorTransformer.interpretEmailMessageException(e)
                }
            }
        }
    }
}
