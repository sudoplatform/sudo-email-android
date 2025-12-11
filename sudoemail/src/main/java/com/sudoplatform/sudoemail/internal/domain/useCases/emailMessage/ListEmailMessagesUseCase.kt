/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.emailMessage.transformers.EmailMessageTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListAPIResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListPartialResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListSuccessResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.PartialResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SortOrderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageDateRangeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.ListEmailMessagesForEmailAddressIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.ListEmailMessagesForEmailFolderIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.ListEmailMessagesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.PartialEmailMessageEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudologging.Logger

/**
 * Input for the list email messages use case.
 *
 * @property emailAddressId [String] Optional email address ID to filter messages by.
 * @property emailFolderId [String] Optional email folder ID to filter messages by.
 * @property dateRange [EmailMessageDateRangeEntity] Optional date range to filter messages.
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 * @property sortOrder [SortOrderEntity] The order to sort results.
 * @property includeDeletedMessages [Boolean] Whether to include deleted messages in results.
 */
internal data class ListEmailMessagesUseCaseInput(
    val emailAddressId: String? = null,
    val emailFolderId: String? = null,
    val dateRange: EmailMessageDateRangeEntity?,
    val limit: Int?,
    val nextToken: String?,
    val sortOrder: SortOrderEntity,
    val includeDeletedMessages: Boolean,
) {
    init {
        require(emailAddressId == null || emailFolderId == null) {
            "Cannot specify both emailAddressId and emailFolderId"
        }
    }
}

/**
 * Use case for listing email messages.
 *
 * This use case retrieves and unseals (decrypts) email messages with optional filtering.
 *
 * @property emailMessageService [EmailMessageService] Service for email message operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property logger [Logger] Logger for debugging.
 */
internal class ListEmailMessagesUseCase(
    private val emailMessageService: EmailMessageService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the list email messages use case.
     *
     * @param input [ListEmailMessagesUseCaseInput] The input parameters.
     * @return [ListAPIResultEntity] The result containing unsealed email messages and partial results.
     */
    suspend fun execute(input: ListEmailMessagesUseCaseInput): ListAPIResultEntity<EmailMessageEntity, PartialEmailMessageEntity> {
        logger.debug("ListEmailMessagesUseCase execute input: $input")
        try {
            val (items, nextToken) =
                when {
                    input.emailAddressId != null -> {
                        emailMessageService.listForEmailAddressId(
                            input =
                                ListEmailMessagesForEmailAddressIdRequest(
                                    emailAddressId = input.emailAddressId,
                                    dateRange = input.dateRange,
                                    limit = input.limit,
                                    nextToken = input.nextToken,
                                    sortOrder = input.sortOrder,
                                    includeDeletedMessages = input.includeDeletedMessages,
                                ),
                        )
                    }
                    input.emailFolderId != null -> {
                        emailMessageService.listForEmailFolderId(
                            input =
                                ListEmailMessagesForEmailFolderIdRequest(
                                    emailFolderId = input.emailFolderId,
                                    dateRange = input.dateRange,
                                    limit = input.limit,
                                    nextToken = input.nextToken,
                                    sortOrder = input.sortOrder,
                                    includeDeletedMessages = input.includeDeletedMessages,
                                ),
                        )
                    }
                    else -> {
                        emailMessageService.list(
                            input =
                                ListEmailMessagesRequest(
                                    dateRange = input.dateRange,
                                    limit = input.limit,
                                    nextToken = input.nextToken,
                                    sortOrder = input.sortOrder,
                                    includeDeletedMessages = input.includeDeletedMessages,
                                ),
                        )
                    }
                }

            val success: MutableList<EmailMessageEntity> = mutableListOf()
            val partials: MutableList<PartialResultEntity<PartialEmailMessageEntity>> = mutableListOf()
            for (sealedEmailMessage in items) {
                try {
                    val unsealedEmailMessage = EmailMessageTransformer.sealedEntityToUnsealedEntity(serviceKeyManager, sealedEmailMessage)

                    success.add(unsealedEmailMessage)
                } catch (e: Exception) {
                    val partialEmailMessage =
                        EmailMessageTransformer.sealedEntityToPartialEntity(
                            sealedEmailMessage,
                        )
                    val partialResult = PartialResultEntity(partialEmailMessage, e)
                    partials.add(partialResult)
                }
            }
            if (partials.isNotEmpty()) {
                val listPartialResult =
                    ListPartialResultEntity(success, partials, nextToken)
                return ListAPIResultEntity.Partial(listPartialResult)
            }
            val listSuccessResult = ListSuccessResultEntity(success, nextToken)
            return ListAPIResultEntity.Success(listSuccessResult)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMessageException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailMessageException(e)
            }
        }
    }
}
