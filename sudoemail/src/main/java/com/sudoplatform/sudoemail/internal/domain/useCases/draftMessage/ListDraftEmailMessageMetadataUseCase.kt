/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageMetadataEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesRequest
import com.sudoplatform.sudologging.Logger

/**
 * Use case for listing draft email message metadata.
 *
 * This use case retrieves metadata for all draft messages across all email addresses
 * without retrieving the full message content.
 *
 * @property draftEmailMessageService [DraftEmailMessageService] Service for draft message operations.
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property logger [Logger] Logger for debugging.
 */
internal class ListDraftEmailMessageMetadataUseCase(
    private val draftEmailMessageService: DraftEmailMessageService,
    private val emailAddressService: EmailAddressService,
    private val logger: Logger,
) {
    /**
     * Executes the list draft email message metadata use case.
     *
     * @return [List] of [DraftEmailMessageMetadataEntity] containing metadata for all draft messages.
     */
    suspend fun execute(): List<DraftEmailMessageMetadataEntity> {
        logger.debug("ListDraftEmailMessageMetadataUseCase: Executing")

        val emailAddressIds = mutableListOf<String>()
        var inputNextToken: String? = null
        do {
            val (emailAddresses, nextToken) =
                emailAddressService.list(
                    ListEmailAddressesRequest(
                        nextToken = inputNextToken,
                        limit = 10,
                    ),
                )
            emailAddressIds.addAll(emailAddresses.map { it.id })
            inputNextToken = nextToken
        } while (inputNextToken != null)

        val allDrafts = mutableListOf<DraftEmailMessageMetadataEntity>()
        for (emailAddressId in emailAddressIds) {
            val drafts = draftEmailMessageService.listMetadataForEmailAddressId(emailAddressId)
            allDrafts.addAll(drafts)
        }
        return allDrafts
    }
}
