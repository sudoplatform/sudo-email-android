/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.types.BatchOperationResult
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.DeleteEmailMessageSuccessResult
import com.sudoplatform.sudoemail.types.EmailMessageOperationFailureResult
import com.sudoplatform.sudologging.Logger

/**
 * Use case for deleting email messages.
 *
 * This use case handles deleting one or more email messages by their IDs.
 *
 * @property emailMessageService [EmailMessageService] Service for email message operations.
 * @property configurationDataService [ConfigurationDataService] Service for configuration data.
 * @property logger [Logger] Logger for debugging.
 */
internal class DeleteEmailMessagesUseCase(
    private val emailMessageService: EmailMessageService,
    private val configurationDataService: ConfigurationDataService,
    private val logger: Logger,
) {
    /**
     * Executes the delete email messages use case.
     *
     * @param ids [Set] of [String] message IDs to delete.
     * @return [BatchOperationResult] The result containing successfully and unsuccessfully deleted messages.
     * @throws SudoEmailClient.EmailMessageException.LimitExceededException if too many IDs are provided.
     * @throws SudoEmailClient.EmailMessageException.InvalidArgumentException if the ID set is empty.
     */
    suspend fun execute(ids: Set<String>): BatchOperationResult<DeleteEmailMessageSuccessResult, EmailMessageOperationFailureResult> {
        logger.debug("DeleteEmailMessagesUseCase: Executing with ids: $ids")
        val config = configurationDataService.getConfigurationData()
        if (ids.size > config.deleteEmailMessagesLimit) {
            throw SudoEmailClient.EmailMessageException.LimitExceededException(
                "$StringConstants.ID_LIMIT_EXCEEDED_ERROR_MSG${config.deleteEmailMessagesLimit}",
            )
        }
        if (ids.isEmpty()) {
            throw SudoEmailClient.EmailMessageException.InvalidArgumentException(
                StringConstants.INVALID_ARGUMENT_ERROR_MSG,
            )
        }
        val result = emailMessageService.delete(ids)
        val successValues =
            result.successIds.map {
                DeleteEmailMessageSuccessResult(it)
            }
        val failureValues =
            result.failureIds.map {
                EmailMessageOperationFailureResult(it, "Failed to delete email message")
            }

        val status =
            if (result.successIds.size == ids.size) {
                BatchOperationStatus.SUCCESS
            } else if (result.failureIds.size == ids.size) {
                BatchOperationStatus.FAILURE
            } else {
                BatchOperationStatus.PARTIAL
            }
        return BatchOperationResult.createDifferent(status, successValues, failureValues)
    }

    suspend fun execute(id: String): DeleteEmailMessageSuccessResult? {
        val result = emailMessageService.delete(setOf(id))

        return if (result.successIds.contains(id)) {
            DeleteEmailMessageSuccessResult(id)
        } else {
            null
        }
    }
}
