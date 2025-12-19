/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ListDraftEmailMessagesOutput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.GetEmailAddressRequest
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudologging.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Use case for listing draft email messages for a specific email address.
 *
 * This use case retrieves and decrypts all draft messages associated with an email address.
 *
 * @property draftEmailMessageService [DraftEmailMessageService] Service for draft message operations.
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property sealingService [SealingService] Service for unsealing/decrypting data.
 * @property emailMessageDataProcessor [EmailMessageDataProcessor] Processor for email message data.
 * @property emailCryptoService [EmailCryptoService] Service for email cryptographic operations.
 * @property logger [Logger] Logger for debugging.
 * @property getDraftEmailMessageUseCase [GetDraftEmailMessageUseCase] Use case for retrieving a single draft message.
 */
internal class ListDraftEmailMessagesForEmailAddressIdUseCase(
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
     * Executes the list draft email messages for email address ID use case.
     *
     * @param emailAddressId [String] The email address ID to list draft messages for.
     * @param limit [Int] Optional maximum number of draft messages to return.
     * @param nextToken [String] Optional token for pagination.
     * @return [ListDraftEmailMessagesOutput] containing draft messages and optional next token.
     * @throws SudoEmailClient.EmailAddressException.EmailAddressNotFoundException if the email address is not found.
     */
    suspend fun execute(
        emailAddressId: String,
        limit: Int? = null,
        nextToken: String? = null,
    ): ListDraftEmailMessagesOutput {
        logger.debug(
            "ListDraftEmailMessagesForEmailAddressIdUseCase: Executing with emailAddressId=$emailAddressId, limit=$limit, nextToken=$nextToken",
        )

        if (emailAddressService.get(GetEmailAddressRequest(emailAddressId)) == null) {
            throw SudoEmailClient.EmailAddressException.EmailAddressNotFoundException(
                StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG,
            )
        }

        val draftMessagesMetadata =
            draftEmailMessageService.listMetadataForEmailAddressId(
                emailAddressId = emailAddressId,
                limit = limit,
                nextToken = nextToken,
            )

        val draftMessages =
            coroutineScope {
                draftMessagesMetadata.items.map { metadata ->
                    async {
                        getDraftEmailMessageUseCase.execute(
                            GetDraftEmailMessageUseCaseInput(
                                draftId = metadata.id,
                                emailAddressId = emailAddressId,
                            ),
                        )
                    }
                }
            }.awaitAll()

        return ListDraftEmailMessagesOutput(
            items = draftMessages,
            nextToken = draftMessagesMetadata.nextToken,
        )
    }
}
