/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.draftMessage

import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.graphql.type.CancelScheduledDraftMessageInput
import com.sudoplatform.sudoemail.graphql.type.ListScheduledDraftMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.graphql.type.ScheduleSendDraftMessageInput
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageFilterInput
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.draftMessage.transformers.ScheduledDraftMessageFilterTransformer
import com.sudoplatform.sudoemail.internal.data.draftMessage.transformers.ScheduledDraftMessageTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.EmailMessageOperationFailureResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.CancelScheduledDraftMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DeleteDraftEmailMessagesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageMetadataEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftMessageObjectMetadata
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.GetDraftEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.GetDraftEmailMessageResponse
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ListDraftEmailMessageMetadataOutput
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ListDraftEmailMessagesWithContentItem
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ListScheduledDraftMessagesForEmailAddressIdInputRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ListScheduledDraftMessagesOutput
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.SaveDraftEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduleSendDraftMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.DeleteEmailMessageSuccessResultEntity
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudologging.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Implementation of [DraftEmailMessageService] that uses S3 for storage and GraphQL API for scheduling.
 *
 * This service handles saving, retrieving, deleting, and listing draft email messages,
 * as well as scheduling and canceling scheduled sends for draft messages.
 *
 * @property s3EmailClient [S3Client] The S3 client for storing and retrieving draft email messages.
 * @property apiClient [ApiClient] The GraphQL API client for scheduling operations.
 * @property logger [Logger] Logger for logging debug and error messages.
 */
internal class GraphQLS3DraftEmailMessageService(
    private val s3EmailClient: S3Client,
    private val apiClient: ApiClient,
    private val logger: Logger,
) : DraftEmailMessageService {
    override suspend fun save(input: SaveDraftEmailMessageRequest): String {
        logger.debug("GraphQLS3DraftEmailMessageService: saveDraftEmailMessage ${input.s3Key}")

        try {
            s3EmailClient.upload(input.uploadData, input.s3Key, input.metadataObject)
            return input.s3Key
        } catch (e: Throwable) {
            logger.error(e.message)
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }

    override suspend fun get(input: GetDraftEmailMessageRequest): GetDraftEmailMessageResponse {
        logger.debug("GraphQLS3DraftEmailMessageService: getDraftEmailMessage $input")

        try {
            val (keyId, _, updatedAt) = getObjectMetadata(input.s3Key)
            val sealedRfc822Data = s3EmailClient.download(input.s3Key)

            return GetDraftEmailMessageResponse(
                s3Key = input.s3Key,
                rfc822Data = sealedRfc822Data,
                keyId = keyId,
                updatedAt = updatedAt,
            )
        } catch (e: Throwable) {
            logger.error(e.message)
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }

    override suspend fun delete(
        input: DeleteDraftEmailMessagesRequest,
    ): BatchOperationResultEntity<DeleteEmailMessageSuccessResultEntity, EmailMessageOperationFailureResultEntity> {
        logger.debug("GraphQLS3DraftEmailMessageService: deleteDraftEmailMessage $input")

        val (s3Keys) = input
        val successIds: MutableList<String> = mutableListOf()
        val failureIds: MutableList<EmailMessageOperationFailureResultEntity> = mutableListOf()
        for (s3Key in s3Keys) {
            try {
                s3EmailClient.delete(s3Key)
                successIds.add(s3Key)
            } catch (e: Throwable) {
                logger.error("Failed to delete draft email message with s3Key $s3Key: ${e.message}")
                failureIds.add(
                    EmailMessageOperationFailureResultEntity(
                        id = s3Key,
                        errorType = e.message ?: StringConstants.UNKNOWN_ERROR_MSG,
                    ),
                )
            }
        }
        val status =
            if (s3Keys.isEmpty() || s3Keys.size == successIds.size) {
                BatchOperationStatusEntity.SUCCESS
            } else if (s3Keys.size == failureIds.size) {
                BatchOperationStatusEntity.FAILURE
            } else {
                BatchOperationStatusEntity.PARTIAL
            }
        val successValues = successIds.map { DeleteEmailMessageSuccessResultEntity(it) }
        return BatchOperationResultEntity(
            status = status,
            successValues = successValues,
            failureValues = failureIds,
        )
    }

    override suspend fun listMetadataForEmailAddressId(
        emailAddressId: String,
        limit: Int?,
        nextToken: String?,
    ): ListDraftEmailMessageMetadataOutput {
        logger.debug(
            "GraphQLS3DraftEmailMessageService: listDraftEmailMessageMetadataForEmailAddressId $emailAddressId, limit: $limit, nextToken: $nextToken",
        )

        try {
            val s3Key = DefaultS3Client.constructS3KeyForDraftEmailMessage(emailAddressId)
            val result = s3EmailClient.list(s3Key, limit, nextToken)

            val items =
                result.items.map {
                    DraftEmailMessageMetadataEntity(
                        it.key.substringAfterLast("/"),
                        emailAddressId,
                        it.lastModified,
                    )
                }

            return ListDraftEmailMessageMetadataOutput(
                items = items,
                nextToken = result.nextToken,
            )
        } catch (e: Throwable) {
            logger.error(e.message)
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }

    override suspend fun getObjectMetadata(s3Key: String): DraftMessageObjectMetadata {
        logger.debug("GraphQLS3DraftEmailMessageService: getDraftMessageObjectMetadata for s3Key: $s3Key")

        try {
            val metadata = s3EmailClient.getObjectMetadata(s3Key)
            val keyId: String? = metadata.userMetadata[StringConstants.DRAFT_METADATA_KEY_ID_NAME]
            if (keyId === null) {
                throw SudoEmailClient.EmailMessageException.UnsealingException(
                    StringConstants.S3_KEY_ID_ERROR_MSG,
                )
            }
            val algorithm =
                metadata.userMetadata[StringConstants.DRAFT_METADATA_ALGORITHM_NAME]
                    ?: throw SudoEmailClient.EmailMessageException.UnsealingException(
                        StringConstants.S3_ALGORITHM_ERROR_MSG,
                    )
            val updatedAt = metadata.lastModified
            return DraftMessageObjectMetadata(
                keyId,
                algorithm,
                updatedAt,
            )
        } catch (e: Throwable) {
            logger.error(e.message)
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }

    override suspend fun scheduleSend(input: ScheduleSendDraftMessageRequest): ScheduledDraftMessageEntity {
        logger.debug("GraphQLS3DraftEmailMessageService: scheduleSendDraftMessage $input")
        try {
            val mutationInput =
                ScheduleSendDraftMessageInput(
                    draftMessageKey = input.draftMessageKey,
                    emailAddressId = input.emailAddressId,
                    sendAtEpochMs = input.sendAt.time.toDouble(),
                    symmetricKey = input.symmetricKey,
                )
            val mutationResponse =
                apiClient.scheduleSendDraftMessageMutation(
                    mutationInput,
                )

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailMessageError(mutationResponse.errors.first())
            }

            val result =
                mutationResponse.data?.scheduleSendDraftMessage?.scheduledDraftMessage
                    ?: throw SudoEmailClient.EmailMessageException.FailedException()
            return ScheduledDraftMessageTransformer.graphQLToEntity(result)
        } catch (e: Throwable) {
            logger.error(e.message)
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }

    override suspend fun cancelScheduledDraftMessage(input: CancelScheduledDraftMessageRequest): String {
        logger.debug("GraphQLS3DraftEmailMessageService: cancelScheduledDraftMessage $input")
        try {
            val mutationInput =
                CancelScheduledDraftMessageInput(
                    draftMessageKey = input.draftMessageKey,
                    emailAddressId = input.emailAddressId,
                )
            val mutationResponse =
                apiClient.cancelScheduledDraftMessageMutation(
                    mutationInput,
                )

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailMessageError(mutationResponse.errors.first())
            }

            val result =
                mutationResponse.data?.cancelScheduledDraftMessage
                    ?: throw SudoEmailClient.EmailMessageException.FailedException()
            return result.substringAfterLast("/")
        } catch (e: Throwable) {
            logger.error(e.message)
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }

    override suspend fun listScheduledDraftMessagesForEmailAddressId(
        input: ListScheduledDraftMessagesForEmailAddressIdInputRequest,
    ): ListScheduledDraftMessagesOutput {
        logger.debug("GraphQLS3DraftEmailMessageService: listScheduledDraftMessagesForEmailAddressId $input")

        try {
            val filter: Optional<ScheduledDraftMessageFilterInput> =
                if (input.filter != null) {
                    Optional.presentIfNotNull(
                        ScheduledDraftMessageFilterTransformer.entityToGraphQl(input.filter),
                    )
                } else {
                    Optional.absent()
                }
            val queryInput =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = input.emailAddressId,
                    limit = Optional.presentIfNotNull(input.limit),
                    nextToken = Optional.presentIfNotNull(input.nextToken),
                    filter = filter,
                )

            val queryResponse =
                apiClient.listScheduledDraftMessagesForEmailAddressIdQuery(
                    queryInput,
                )

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailMessageError(queryResponse.errors.first())
            }

            val queryResult = queryResponse.data.listScheduledDraftMessagesForEmailAddressId
            return ListScheduledDraftMessagesOutput(
                items =
                    queryResult.items.map {
                        ScheduledDraftMessageTransformer.graphQLToEntity(it.scheduledDraftMessage)
                    },
                nextToken = queryResult.nextToken,
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
