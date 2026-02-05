/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ListDraftEmailMessageMetadataOutput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.GetEmailAddressRequest
import com.sudoplatform.sudologging.Logger

/**
 * Use case for listing draft email message metadata for a specific email address.
 *
 * This use case retrieves metadata for all draft messages associated with an email address
 * without retrieving the full message content.
 *
 * @property draftEmailMessageService [DraftEmailMessageService] Service for draft message operations.
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property logger [Logger] Logger for debugging.
 */
internal class ListDraftEmailMessageMetadataForEmailAddressIdUseCase(
    private val draftEmailMessageService: DraftEmailMessageService,
    private val emailAddressService: EmailAddressService,
    private val logger: Logger,
) {
    /**
     * Executes the list draft email message metadata for email address ID use case.
     *
     * @param emailAddressId [String] The email address ID to list draft message metadata for.
     * @param limit [Int] Optional limit for the number of results to return.
     * @param nextToken [String] Optional token for pagination.
     * @return [ListDraftEmailMessageMetadataOutput] The result containing the list of draft metadata and next token. . When next token is not null additional records are available.
     * @throws SudoEmailClient.EmailAddressException.EmailAddressNotFoundException if the email address is not found.
     */
    suspend fun execute(
        emailAddressId: String,
        limit: Int? = null,
        nextToken: String? = null,
    ): ListDraftEmailMessageMetadataOutput {
        logger.debug(
            "ListDraftEmailMessageMetadataForEmailAddressIdUseCase: Executing with emailAddressId: $emailAddressId, limit: $limit, nextToken: $nextToken",
        )

        if (emailAddressService.get(GetEmailAddressRequest(emailAddressId)) == null) {
            throw SudoEmailClient.EmailAddressException.EmailAddressNotFoundException(
                StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG,
            )
        }

        return draftEmailMessageService.listMetadataForEmailAddressId(emailAddressId, limit, nextToken)
    }
}
