/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.EmailMessageOperationFailureResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SortOrderEntity

/**
 * Request to send an out-network email message.
 *
 * @property emailAddressId [String] The ID of the email address from which to send the message.
 * @property s3ObjectKey [String] The S3 object key where the message is stored.
 * @property region [String] The AWS region where the S3 bucket is located.
 * @property transientBucket [String] The transient S3 bucket name.
 */
internal data class SendEmailMessageRequest(
    val emailAddressId: String,
    val s3ObjectKey: String,
    val region: String,
    val transientBucket: String,
)

/**
 * Request to send an in-network encrypted email message.
 *
 * @property emailAddressId [String] The ID of the email address from which to send the message.
 * @property s3ObjectKey [String] The S3 object key where the message is stored.
 * @property region [String] The AWS region where the S3 bucket is located.
 * @property transientBucket [String] The transient S3 bucket name.
 * @property emailMessageHeader [InternetMessageFormatHeaderEntity] The email message header in Internet Message Format.
 * @property attachments [List] The list of file attachments.
 * @property inlineAttachments [List] The list of inline attachments.
 * @property replyingMessageId [String] Optional ID of the message being replied to.
 * @property forwardingMessageId [String] Optional ID of the message being forwarded.
 */
internal data class SendEncryptedEmailMessageRequest(
    val emailAddressId: String,
    val s3ObjectKey: String,
    val region: String,
    val transientBucket: String,
    val emailMessageHeader: InternetMessageFormatHeaderEntity,
    val attachments: List<EmailAttachmentEntity>,
    val inlineAttachments: List<EmailAttachmentEntity>,
    val replyingMessageId: String?,
    val forwardingMessageId: String?,
)

/**
 * Request to update one or more email messages.
 *
 * @property ids The [List] of email message IDs [String] to update.
 * @property values [UpdatableValues] The values to update.
 */
internal data class UpdateEmailMessagesRequest(
    val ids: List<String>,
    val values: UpdatableValues,
) {
    /**
     * Values that can be updated on an email message.
     *
     * @property folderId [String] Optional new folder ID to move the message to.
     * @property seen [Boolean] Optional flag to mark the message as seen/unseen.
     */
    data class UpdatableValues(
        val folderId: String? = null,
        val seen: Boolean? = null,
    )
}

/**
 * Request to retrieve an email message by ID.
 *
 * @property id [String] The unique identifier of the email message.
 */
internal data class GetEmailMessageRequest(
    val id: String,
)

/**
 * Request to list email messages.
 *
 * @property dateRange [EmailMessageDateRangeEntity] Optional date range to filter messages.
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 * @property sortOrder [SortOrderEntity] The order to sort results.
 * @property includeDeletedMessages [Boolean] Whether to include deleted messages in results.
 */
internal data class ListEmailMessagesRequest(
    val dateRange: EmailMessageDateRangeEntity?,
    val limit: Int?,
    val nextToken: String?,
    val sortOrder: SortOrderEntity,
    val includeDeletedMessages: Boolean,
)

/**
 * Request to list email messages for a specific email address ID.
 *
 * @property emailAddressId [String] The email address ID to filter messages.
 * @property dateRange [EmailMessageDateRangeEntity] Optional date range to filter messages.
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 * @property sortOrder [SortOrderEntity] The order to sort results.
 * @property includeDeletedMessages [Boolean] Whether to include deleted messages in results.
 */
internal data class ListEmailMessagesForEmailAddressIdRequest(
    val emailAddressId: String,
    val dateRange: EmailMessageDateRangeEntity?,
    val limit: Int?,
    val nextToken: String?,
    val sortOrder: SortOrderEntity,
    val includeDeletedMessages: Boolean,
)

/**
 * Request to list email messages for a specific email folder ID.
 *
 * @property emailFolderId [String] The email folder ID to filter messages.
 * @property dateRange [EmailMessageDateRangeEntity] Optional date range to filter messages.
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 * @property sortOrder [SortOrderEntity] The order to sort results.
 * @property includeDeletedMessages [Boolean] Whether to include deleted messages in results.
 */
internal data class ListEmailMessagesForEmailFolderIdRequest(
    val emailFolderId: String,
    val dateRange: EmailMessageDateRangeEntity?,
    val limit: Int?,
    val nextToken: String?,
    val sortOrder: SortOrderEntity,
    val includeDeletedMessages: Boolean,
)

/**
 * Request to delete email messages by folder ID.
 *
 * @property emailAddressId [String] The email address ID associated with the folder.
 * @property emailFolderId [String] The email folder ID from which to delete messages.
 * @property hardDelete [Boolean] Optional flag to permanently delete messages (true) or move to trash (false/null).
 */
internal data class DeleteMessageForFolderIdRequest(
    val emailAddressId: String,
    val emailFolderId: String,
    val hardDelete: Boolean?,
)

/**
 * Output from listing email messages.
 *
 * @property items The [List] of [SealedEmailMessageEntity]s.
 * @property nextToken [String] Optional token for retrieving the next page of results.
 */
internal data class ListEmailMessagesOutput(
    val items: List<SealedEmailMessageEntity>,
    val nextToken: String?,
)

/**
 * Service interface for managing email messages.
 *
 * Provides operations to send, update, delete, retrieve, and list email messages.
 */
internal interface EmailMessageService {
    /**
     * Sends an out-network email message.
     *
     * @param input [SendEmailMessageRequest] The details of the email message to send.
     * @return [SendEmailMessageResultEntity] The result of the send operation.
     */
    suspend fun send(input: SendEmailMessageRequest): SendEmailMessageResultEntity

    /**
     * Sends an in-network encrypted email message.
     *
     * @param input [SendEncryptedEmailMessageRequest] The details of the email message to send.
     * @return [SendEmailMessageResultEntity] The result of the send operation.
     */
    suspend fun sendEncrypted(input: SendEncryptedEmailMessageRequest): SendEmailMessageResultEntity

    /**
     * Updates one or more email messages.
     *
     * @param input [UpdateEmailMessagesRequest] The details of the email messages to update.
     * @return [BatchOperationResultEntity] The result of the update operation.
     */
    suspend fun update(
        input: UpdateEmailMessagesRequest,
    ): BatchOperationResultEntity<
        UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity,
        EmailMessageOperationFailureResultEntity,
    >

    /**
     * Deletes one or more email messages.
     *
     * @param ids The [Set] IDs [String] of the email messages to delete.
     * @return [DeleteEmailMessagesResultEntity] The result of the delete operation.
     */
    suspend fun delete(ids: Set<String>): DeleteEmailMessagesResultEntity

    /**
     * Retrieves an email message by ID.
     *
     * @param input [GetEmailMessageRequest] The details of the email message to retrieve.
     * @return [SealedEmailMessageEntity] The retrieved email message or null if not found.
     */
    suspend fun get(input: GetEmailMessageRequest): SealedEmailMessageEntity?

    /**
     * Lists email messages.
     *
     * @param input [ListEmailMessagesRequest] The details of the email messages to list.
     * @return [ListEmailMessagesOutput] The list of email messages and pagination token.
     */
    suspend fun list(input: ListEmailMessagesRequest): ListEmailMessagesOutput

    /**
     * Lists email messages for a specific email address ID.
     *
     * @param input [ListEmailMessagesForEmailAddressIdRequest] The details of the email messages to list.
     * @return [ListEmailMessagesOutput] The list of email messages and pagination token.
     */
    suspend fun listForEmailAddressId(input: ListEmailMessagesForEmailAddressIdRequest): ListEmailMessagesOutput

    /**
     * Lists email messages for a specific email folder ID.
     *
     * @param input [ListEmailMessagesForEmailFolderIdRequest] The details of the email messages to list.
     * @return [ListEmailMessagesOutput] The list of email messages and pagination token.
     */
    suspend fun listForEmailFolderId(input: ListEmailMessagesForEmailFolderIdRequest): ListEmailMessagesOutput

    /**
     * Deletes email messages by folder ID.
     *
     * @param input [DeleteMessageForFolderIdRequest] The details of the email messages to delete.
     * @return [String] The ID of the folder.
     */
    suspend fun deleteForFolderId(input: DeleteMessageForFolderIdRequest): String
}
