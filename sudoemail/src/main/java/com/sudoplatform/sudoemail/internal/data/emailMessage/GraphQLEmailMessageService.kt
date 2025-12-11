/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.graphql.type.DeleteEmailMessagesInput
import com.sudoplatform.sudoemail.graphql.type.DeleteMessagesByFolderIdInput
import com.sudoplatform.sudoemail.graphql.type.EmailMessageUpdateValuesInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailMessagesInput
import com.sudoplatform.sudoemail.graphql.type.Rfc822HeaderInput
import com.sudoplatform.sudoemail.graphql.type.S3EmailObjectInput
import com.sudoplatform.sudoemail.graphql.type.SendEmailMessageInput
import com.sudoplatform.sudoemail.graphql.type.SendEncryptedEmailMessageInput
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailMessagesInput
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.BatchOperationResultTransformer
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.common.transformers.SortOrderTransformer
import com.sudoplatform.sudoemail.internal.data.emailMessage.transformers.EmailMessageDateRangeTransformer.toEmailMessageDateRangeInput
import com.sudoplatform.sudoemail.internal.data.emailMessage.transformers.EmailMessageTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.EmailMessageOperationFailureResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.DeleteEmailMessagesResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.DeleteMessageForFolderIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.GetEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.ListEmailMessagesForEmailAddressIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.ListEmailMessagesForEmailFolderIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.ListEmailMessagesOutput
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.ListEmailMessagesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SealedEmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SendEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SendEmailMessageResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SendEncryptedEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.UpdateEmailMessagesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.UpdatedEmailMessageResultEntity
import com.sudoplatform.sudoemail.internal.util.toDate
import com.sudoplatform.sudologging.Logger

/**
 * Implementation of [EmailMessageService] for managing email messages.
 *
 * This service handles sending, updating, deleting, and retrieving email messages
 * by interacting with the GraphQL API client.
 *
 * @property apiClient [ApiClient] The GraphQL API client for executing mutations and queries.
 * @property logger [Logger] Logger for logging debug and error messages.
 */
internal class GraphQLEmailMessageService(
    val apiClient: ApiClient,
    val logger: Logger,
) : EmailMessageService {
    override suspend fun send(input: SendEmailMessageRequest): SendEmailMessageResultEntity {
        logger.debug("Sending email message: $input")
        try {
            val s3EmailObject =
                S3EmailObjectInput(
                    key = input.s3ObjectKey,
                    region = input.region,
                    bucket = input.transientBucket,
                )
            val mutationInput =
                SendEmailMessageInput(
                    emailAddressId = input.emailAddressId,
                    message = s3EmailObject,
                )
            val mutationResponse =
                apiClient.sendEmailMessageMutation(
                    mutationInput,
                )

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailMessageError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.sendEmailMessageV2
            result?.let {
                return SendEmailMessageResultEntity(
                    it.sendEmailMessageResult.id,
                    it.sendEmailMessageResult.createdAtEpochMs.toDate(),
                )
            }
            throw SudoEmailClient.EmailMessageException.FailedException(StringConstants.NO_EMAIL_ID_ERROR_MSG)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }

    override suspend fun sendEncrypted(input: SendEncryptedEmailMessageRequest): SendEmailMessageResultEntity {
        logger.debug("Sending encrypted email message: $input")
        val (
            emailAddressId,
            s3ObjectKey,
            region,
            transientBucket,
            emailMessageHeader,
            attachments,
            inlineAttachments,
            replyingMessageId,
            forwardingMessageId,
        ) = input
        try {
            val s3EmailObjectInput =
                S3EmailObjectInput(
                    key = s3ObjectKey,
                    region = region,
                    bucket = transientBucket,
                )

            val inReplyToHeaderValue =
                if (replyingMessageId != null) {
                    Optional.presentIfNotNull(replyingMessageId)
                } else {
                    Optional.Absent
                }
            val referencesHeaderValue =
                if (forwardingMessageId != null) {
                    Optional.presentIfNotNull(listOf(forwardingMessageId))
                } else {
                    Optional.Absent
                }
            val rfc822HeaderInput =
                Rfc822HeaderInput(
                    from = emailMessageHeader.from.toString(),
                    to = emailMessageHeader.to.map { it.toString() },
                    cc = emailMessageHeader.cc.map { it.toString() },
                    bcc = emailMessageHeader.bcc.map { it.toString() },
                    replyTo = emailMessageHeader.replyTo.map { it.toString() },
                    subject = Optional.presentIfNotNull(emailMessageHeader.subject),
                    hasAttachments = Optional.presentIfNotNull(attachments.isNotEmpty() || inlineAttachments.isNotEmpty()),
                    inReplyTo = inReplyToHeaderValue,
                    references = referencesHeaderValue,
                )

            val mutationInput =
                SendEncryptedEmailMessageInput(
                    emailAddressId = emailAddressId,
                    message = s3EmailObjectInput,
                    rfc822Header = rfc822HeaderInput,
                )
            val mutationResponse =
                apiClient.sendEncryptedEmailMessageMutation(
                    mutationInput,
                )

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailMessageError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.sendEncryptedEmailMessage
            result?.let {
                return SendEmailMessageResultEntity(
                    it.sendEmailMessageResult.id,
                    it.sendEmailMessageResult.createdAtEpochMs.toDate(),
                )
            }
            throw SudoEmailClient.EmailMessageException.FailedException(StringConstants.NO_EMAIL_ID_ERROR_MSG)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }

    override suspend fun update(
        input: UpdateEmailMessagesRequest,
    ): BatchOperationResultEntity<
        UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity,
        EmailMessageOperationFailureResultEntity,
    > {
        logger.debug("Updating email messages: $input")
        try {
            val updateValuesInput =
                EmailMessageUpdateValuesInput(
                    folderId = Optional.presentIfNotNull(input.values.folderId),
                    seen = Optional.presentIfNotNull(input.values.seen),
                )
            val mutationInput =
                UpdateEmailMessagesInput(
                    messageIds = input.ids,
                    values = updateValuesInput,
                )
            val mutationResponse =
                apiClient.updateEmailMessagesMutation(
                    mutationInput,
                )

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailMessageError(mutationResponse.errors.first())
            }

            val result =
                mutationResponse.data?.updateEmailMessagesV2?.updateEmailMessagesResult
                    ?: throw SudoEmailClient.EmailMessageException.FailedException(StringConstants.NO_EMAIL_ID_ERROR_MSG)
            return BatchOperationResultTransformer.graphQLToEntity(result)
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

    override suspend fun delete(ids: Set<String>): DeleteEmailMessagesResultEntity {
        logger.debug("Deleting email messages: $ids")
        try {
            val mutationInput =
                DeleteEmailMessagesInput(
                    messageIds = ids.toList(),
                )
            val mutationResponse =
                apiClient.deleteEmailMessagesMutation(
                    mutationInput,
                )

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailMessageError(mutationResponse.errors.first())
            }

            val failureIds =
                mutationResponse.data?.deleteEmailMessages
                    ?: throw SudoEmailClient.EmailMessageException.FailedException(StringConstants.NO_EMAIL_ID_ERROR_MSG)
            val successIds = ids.filter { !failureIds.contains(it) } as MutableList<String>
            return DeleteEmailMessagesResultEntity(successIds, failureIds)
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

    override suspend fun get(input: GetEmailMessageRequest): SealedEmailMessageEntity? {
        try {
            val queryResponse =
                apiClient.getEmailMessageQuery(
                    input.id,
                )

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailMessageError(queryResponse.errors.first())
            }
            val result = queryResponse.data?.getEmailMessage?.sealedEmailMessage
            result?.let {
                return EmailMessageTransformer.graphQLToSealedEntity(result)
            }
            return null
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

    override suspend fun list(input: ListEmailMessagesRequest): ListEmailMessagesOutput {
        logger.debug("Listing email messages: $input")
        try {
            val queryInput =
                ListEmailMessagesInput(
                    limit = Optional.presentIfNotNull(input.limit),
                    nextToken = Optional.presentIfNotNull(input.nextToken),
                    specifiedDateRange = Optional.presentIfNotNull(input.dateRange.toEmailMessageDateRangeInput()),
                    sortOrder = Optional.presentIfNotNull(SortOrderTransformer.entityToGraphQL(input.sortOrder)),
                    includeDeletedMessages = Optional.presentIfNotNull(input.includeDeletedMessages),
                )
            val queryResponse =
                apiClient.listEmailMessagesQuery(
                    queryInput,
                )

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailMessageError(queryResponse.errors.first())
            }

            val queryResult = queryResponse.data?.listEmailMessages
            val sealedEmailMessages = queryResult?.items ?: emptyList()
            val newNextToken = queryResult?.nextToken

            return ListEmailMessagesOutput(
                items =
                    sealedEmailMessages.map {
                        EmailMessageTransformer.graphQLToSealedEntity(
                            it.sealedEmailMessage,
                        )
                    },
                nextToken = newNextToken,
            )
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

    override suspend fun listForEmailAddressId(input: ListEmailMessagesForEmailAddressIdRequest): ListEmailMessagesOutput {
        logger.debug("Listing email messages for email address ID: $input")
        try {
            val queryInput =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = input.emailAddressId,
                    limit = Optional.presentIfNotNull(input.limit),
                    nextToken = Optional.presentIfNotNull(input.nextToken),
                    specifiedDateRange = Optional.presentIfNotNull(input.dateRange.toEmailMessageDateRangeInput()),
                    sortOrder = Optional.presentIfNotNull(SortOrderTransformer.entityToGraphQL(input.sortOrder)),
                    includeDeletedMessages = Optional.presentIfNotNull(input.includeDeletedMessages),
                )
            val queryResponse =
                apiClient.listEmailMessagesForEmailAddressIdQuery(
                    queryInput,
                )

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailMessageError(queryResponse.errors.first())
            }

            val queryResult = queryResponse.data?.listEmailMessagesForEmailAddressId
            val sealedEmailMessages = queryResult?.items ?: emptyList()
            val newNextToken = queryResult?.nextToken

            return ListEmailMessagesOutput(
                items =
                    sealedEmailMessages.map {
                        EmailMessageTransformer.graphQLToSealedEntity(
                            it.sealedEmailMessage,
                        )
                    },
                nextToken = newNextToken,
            )
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

    override suspend fun listForEmailFolderId(input: ListEmailMessagesForEmailFolderIdRequest): ListEmailMessagesOutput {
        logger.debug("Listing email messages for email folder ID: $input")
        try {
            val queryInput =
                ListEmailMessagesForEmailFolderIdInput(
                    folderId = input.emailFolderId,
                    limit = Optional.presentIfNotNull(input.limit),
                    nextToken = Optional.presentIfNotNull(input.nextToken),
                    specifiedDateRange = Optional.presentIfNotNull(input.dateRange.toEmailMessageDateRangeInput()),
                    sortOrder = Optional.presentIfNotNull(SortOrderTransformer.entityToGraphQL(input.sortOrder)),
                    includeDeletedMessages = Optional.presentIfNotNull(input.includeDeletedMessages),
                )
            val queryResponse =
                apiClient.listEmailMessagesForEmailFolderIdQuery(
                    queryInput,
                )

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailMessageError(queryResponse.errors.first())
            }

            val queryResult = queryResponse.data?.listEmailMessagesForEmailFolderId
            val sealedEmailMessages = queryResult?.items ?: emptyList()
            val newNextToken = queryResult?.nextToken

            return ListEmailMessagesOutput(
                items =
                    sealedEmailMessages.map {
                        EmailMessageTransformer.graphQLToSealedEntity(
                            it.sealedEmailMessage,
                        )
                    },
                nextToken = newNextToken,
            )
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

    override suspend fun deleteForFolderId(input: DeleteMessageForFolderIdRequest): String {
        logger.debug("Deleting email messages by folder ID: $input")

        val mutationInput =
            DeleteMessagesByFolderIdInput(
                folderId = input.emailFolderId,
                emailAddressId = input.emailAddressId,
                hardDelete = Optional.presentIfNotNull(input.hardDelete),
            )

        val mutationResponse =
            apiClient.deleteEmailMessagesByFolderIdMutation(
                mutationInput,
            )

        if (mutationResponse.hasErrors()) {
            logger.error("errors = ${mutationResponse.errors}")
            throw ErrorTransformer.interpretEmailFolderError(mutationResponse.errors.first())
        }
        return mutationResponse.data.deleteMessagesByFolderId
    }
}
