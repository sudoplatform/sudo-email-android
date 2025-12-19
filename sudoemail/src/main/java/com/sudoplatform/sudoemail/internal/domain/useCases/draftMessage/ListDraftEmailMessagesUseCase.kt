/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageWithContentEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesRequest
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudologging.Logger
import kotlinx.coroutines.coroutineScope

/**
 * Use case for listing all draft email messages.
 *
 * This use case retrieves and decrypts all draft messages across all email addresses.
 *
 * @property draftEmailMessageService [DraftEmailMessageService] Service for draft message operations.
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property sealingService [SealingService] Service for unsealing/decrypting data.
 * @property emailMessageDataProcessor [EmailMessageDataProcessor] Processor for email message data.
 * @property emailCryptoService [EmailCryptoService] Service for email cryptographic operations.
 * @property logger [Logger] Logger for debugging.
 * @property getDraftEmailMessageUseCase [GetDraftEmailMessageUseCase] Use case for retrieving a single draft message.
 */
internal class ListDraftEmailMessagesUseCase(
    private val draftEmailMessageService: DraftEmailMessageService,
    private val emailAddressService: EmailAddressService,
    private val sealingService: SealingService,
    private val emailMessageDataProcessor: EmailMessageDataProcessor,
    private val emailCryptoService: EmailCryptoService,
    private val logger: Logger,
    private val getDraftEmailMessageUseCase: GetDraftEmailMessageUseCase =
        GetDraftEmailMessageUseCase(
            draftEmailMessageService = draftEmailMessageService,
            sealingService = sealingService,
            emailMessageDataProcessor = emailMessageDataProcessor,
            emailCryptoService = emailCryptoService,
            logger = logger,
            emailAddressService = emailAddressService,
        ),
) {
    /**
     * Executes the list draft email messages use case.
     *
     * @return [List] of [DraftEmailMessageWithContentEntity] containing all draft messages.
     */
    suspend fun execute(): List<DraftEmailMessageWithContentEntity> {
        logger.debug("ListDraftEmailMessagesUseCase: Executing")

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

        return coroutineScope {
            emailAddressIds
                .map { emailAddressId ->
                    draftEmailMessageService.listMetadataForEmailAddressId(emailAddressId, 1000).items
                }.flatten()
                .map { metadata ->
                    getDraftEmailMessageUseCase.execute(
                        GetDraftEmailMessageUseCaseInput(
                            draftId = metadata.id,
                            emailAddressId = metadata.emailAddressId,
                        ),
                    )
                }
        }
    }
}
