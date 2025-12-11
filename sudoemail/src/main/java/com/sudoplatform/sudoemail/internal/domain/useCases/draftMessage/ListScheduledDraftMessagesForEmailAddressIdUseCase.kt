/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.draftMessage.transformers.ScheduledDraftMessageFilterTransformer
import com.sudoplatform.sudoemail.internal.data.draftMessage.transformers.ScheduledDraftMessageTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ListScheduledDraftMessagesForEmailAddressIdInputRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.GetEmailAddressRequest
import com.sudoplatform.sudoemail.types.ListOutput
import com.sudoplatform.sudoemail.types.ScheduledDraftMessage
import com.sudoplatform.sudoemail.types.inputs.ScheduledDraftMessageFilterInput
import com.sudoplatform.sudologging.Logger

/**
 * Input for the list scheduled draft messages for email address ID use case.
 *
 * @property emailAddressId [String] The email address ID to list scheduled drafts for.
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 * @property filter [ScheduledDraftMessageFilterInput] Optional filter to apply to results.
 */
internal data class ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
    val emailAddressId: String,
    val limit: Int?,
    val nextToken: String?,
    val filter: ScheduledDraftMessageFilterInput?,
)

/**
 * Use case for listing scheduled draft messages for a specific email address.
 *
 * This use case retrieves scheduled draft messages associated with an email address.
 *
 * @property draftEmailMessageService [DraftEmailMessageService] Service for draft message operations.
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property logger [Logger] Logger for debugging.
 */
internal class ListScheduledDraftMessagesForEmailAddressIdUseCase(
    private val draftEmailMessageService: DraftEmailMessageService,
    private val emailAddressService: EmailAddressService,
    private val logger: Logger,
) {
    /**
     * Executes the list scheduled draft messages for email address ID use case.
     *
     * @param input [ListScheduledDraftMessagesForEmailAddressIdUseCaseInput] The input parameters.
     * @return [ListOutput] containing scheduled draft messages and pagination info.
     * @throws SudoEmailClient.EmailAddressException.EmailAddressNotFoundException if the email address is not found.
     */
    suspend fun execute(input: ListScheduledDraftMessagesForEmailAddressIdUseCaseInput): ListOutput<ScheduledDraftMessage> {
        logger.debug("ListScheduledDraftMessagesForEmailAddressIdUseCase execute input: $input")

        if (emailAddressService.get(GetEmailAddressRequest(input.emailAddressId)) == null) {
            throw SudoEmailClient.EmailAddressException.EmailAddressNotFoundException(
                StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG,
            )
        }

        try {
            val (result, nextToken) =
                draftEmailMessageService.listScheduledDraftMessagesForEmailAddressId(
                    ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                        emailAddressId = input.emailAddressId,
                        limit = input.limit,
                        nextToken = input.nextToken,
                        filter = input.filter?.let { ScheduledDraftMessageFilterTransformer.apiToEntity(it) },
                    ),
                )

            return ListOutput(
                items = result.map { ScheduledDraftMessageTransformer.entityToApi(it) },
                nextToken = nextToken,
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
