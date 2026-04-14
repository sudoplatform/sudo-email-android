/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.EmailMessageOperationFailureResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.CancelScheduledDraftMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DeleteDraftEmailMessagesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.GetEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.DeleteEmailMessageSuccessResultEntity
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudologging.Logger

/**
 * Input for the delete draft email messages use case.
 *
 * @property ids [List] of [String] draft message IDs to delete.
 * @property emailAddressId [String] The email address ID associated with the drafts.
 * @property emailMaskId [String?] The identifier of the email mask associated with the draft email messages, if any
 */
internal data class DeleteDraftEmailMessagesUseCaseInput(
    val ids: List<String>,
    val emailAddressId: String,
    val emailMaskId: String?,
)

/**
 * Use case for deleting draft email messages.
 *
 * This use case handles deleting one or more draft email messages from S3.
 *
 * @property draftEmailMessageService [DraftEmailMessageService] Service for draft message operations.
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property logger [Logger] Logger for debugging.
 */
internal class DeleteDraftEmailMessagesUseCase(
    private val draftEmailMessageService: DraftEmailMessageService,
    private val emailAddressService: EmailAddressService,
    private val logger: Logger,
) {
    /**
     * Executes the delete draft email messages use case.
     *
     * @param input [DeleteDraftEmailMessagesUseCaseInput] The input parameters.
     * @return [BatchOperationResultEntity] The result containing successfully and unsuccessfully deleted draft messages.
     * @throws SudoEmailClient.EmailAddressException.EmailAddressNotFoundException if the email address is not found.
     */
    suspend fun execute(
        input: DeleteDraftEmailMessagesUseCaseInput,
    ): BatchOperationResultEntity<DeleteEmailMessageSuccessResultEntity, EmailMessageOperationFailureResultEntity> {
        logger.debug("DeleteDraftEmailMessagesUseCase: Executing with input: $input")
        val (ids, emailAddressId, emailMaskId) = input
        if (emailAddressService.get(GetEmailAddressRequest(emailAddressId)) == null) {
            throw SudoEmailClient.EmailAddressException.EmailAddressNotFoundException(
                StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG,
            )
        }

        if (input.ids.isEmpty()) {
            throw SudoEmailClient.EmailMessageException.InvalidArgumentException(
                StringConstants.INVALID_ARGUMENT_ERROR_MSG,
            )
        }

        val deleteResult =
            draftEmailMessageService.delete(
                DeleteDraftEmailMessagesRequest(
                    s3Keys = ids.map { DefaultS3Client.constructS3KeyForDraftEmailMessage(emailAddressId, it, emailMaskId) }.toSet(),
                ),
            )

        for (successResult in (deleteResult.successValues ?: emptyList())) {
            try {
                draftEmailMessageService.cancelScheduledDraftMessage(
                    CancelScheduledDraftMessageRequest(
                        draftMessageKey = successResult.id,
                        emailAddressId = emailAddressId,
                        emailMaskId = input.emailMaskId,
                    ),
                )
            } catch (e: Throwable) {
                logger.debug("DeleteDraftEmailMessagesUseCase: No scheduled message to cancel for key ${successResult.id}: ${e.message}")
            }
        }

        return deleteResult
    }
}
