/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.EmailMessageOperationFailureResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.UpdateEmailMessagesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.UpdatedEmailMessageResultEntity
import com.sudoplatform.sudologging.Logger

/**
 * Input for the update email messages use case.
 *
 * @property ids [List] of [String] message IDs to update.
 * @property values [UpdatableValues] The values to update.
 */
internal data class UpdateEmailMessagesUseCaseInput(
    val ids: List<String>,
    val values: UpdatableValues,
) {
    /**
     * Values that can be updated on email messages.
     *
     * @property folderId [String] Optional new folder ID to move messages to.
     * @property seen [Boolean] Optional flag to mark messages as seen/unseen.
     */
    data class UpdatableValues(
        val folderId: String? = null,
        val seen: Boolean? = null,
    )
}

/**
 * Use case for updating email messages.
 *
 * This use case handles bulk updating of email message properties.
 *
 * @property emailMessageService [EmailMessageService] Service for email message operations.
 * @property configurationDataService [ConfigurationDataService] Service for configuration data.
 * @property logger [Logger] Logger for debugging.
 */
internal class UpdateEmailMessagesUseCase(
    private val emailMessageService: EmailMessageService,
    private val configurationDataService: ConfigurationDataService,
    private val logger: Logger,
) {
    /**
     * Executes the update email messages use case.
     *
     * @param input [UpdateEmailMessagesUseCaseInput] The input parameters.
     * @return [BatchOperationResultEntity] The result containing successfully and unsuccessfully updated messages.
     * @throws SudoEmailClient.EmailMessageException.LimitExceededException if too many IDs are provided.
     * @throws SudoEmailClient.EmailMessageException.InvalidArgumentException if the input is invalid.
     */
    suspend fun execute(
        input: UpdateEmailMessagesUseCaseInput,
    ): BatchOperationResultEntity<
        UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity,
        EmailMessageOperationFailureResultEntity,
    > {
        logger.debug("UpdateEmailMessagesUseCase: execute")
        val config = configurationDataService.getConfigurationData()
        val idSet = input.ids.toSet()
        if (idSet.size > config.updateEmailMessagesLimit) {
            throw SudoEmailClient.EmailMessageException.LimitExceededException(
                "$StringConstants.ID_LIMIT_EXCEEDED_ERROR_MSG${config.updateEmailMessagesLimit}",
            )
        }
        if (idSet.isEmpty()) {
            throw SudoEmailClient.EmailMessageException.InvalidArgumentException(
                StringConstants.INVALID_ARGUMENT_ERROR_MSG,
            )
        }
        val request =
            UpdateEmailMessagesRequest(
                ids = input.ids,
                values =
                    UpdateEmailMessagesRequest.UpdatableValues(
                        folderId = input.values.folderId,
                        seen = input.values.seen,
                    ),
            )
        return emailMessageService.update(request)
    }
}
