/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.draftMessage

import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.EmailMessageOperationFailureResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.DeleteEmailMessageSuccessResultEntity
import java.util.Date

/**
 * Request to save a draft email message.
 *
 * @property uploadData [ByteArray] The RFC822 formatted email message data to upload.
 * @property s3Key [String] The S3 key where the draft message will be stored.
 * @property metadataObject [Map] Metadata to associate with the draft message.
 */
internal data class SaveDraftEmailMessageRequest(
    val uploadData: ByteArray,
    val s3Key: String,
    val metadataObject: Map<String, String>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SaveDraftEmailMessageRequest

        if (!uploadData.contentEquals(other.uploadData)) return false
        return s3Key == other.s3Key
    }

    override fun hashCode(): Int {
        var result = uploadData.contentHashCode()
        result = 31 * result + s3Key.hashCode()
        return result
    }
}

/**
 * Request to retrieve a draft email message.
 *
 * @property s3Key [String] The S3 key of the draft message to retrieve.
 */
internal data class GetDraftEmailMessageRequest(
    val s3Key: String,
)

/**
 * Request to delete one or more draft email messages.
 *
 * @property s3Keys The [Set] of S3 keys [String] of draft messages to delete.
 */
internal data class DeleteDraftEmailMessagesRequest(
    val s3Keys: Set<String>,
)

/**
 * Request to schedule sending of a draft email message.
 *
 * @property draftMessageKey [String] The S3 key of the draft message to schedule.
 * @property emailAddressId [String] The email address ID from which to send the message.
 * @property sendAt [Date] The date and time when the message should be sent.
 * @property symmetricKey [String] The symmetric key used to encrypt the message.
 */
internal data class ScheduleSendDraftMessageRequest(
    val draftMessageKey: String,
    val emailAddressId: String,
    val sendAt: Date,
    val symmetricKey: String,
)

/**
 * Request to cancel a scheduled draft email message.
 *
 * @property draftMessageKey [String] The S3 key of the draft message to cancel.
 * @property emailAddressId [String] The email address ID associated with the scheduled message.
 */
internal data class CancelScheduledDraftMessageRequest(
    val draftMessageKey: String,
    val emailAddressId: String,
)

/**
 * Request to list scheduled draft messages for an email address ID.
 *
 * @property emailAddressId [String] The email address ID to list scheduled draft messages for.
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 * @property filter [ScheduledDraftMessageFilterInputEntity] Optional filter to apply to the results.
 */
internal data class ListScheduledDraftMessagesForEmailAddressIdInputRequest(
    val emailAddressId: String,
    val limit: Int? = null,
    val nextToken: String? = null,
    val filter: ScheduledDraftMessageFilterInputEntity? = null,
)

/**
 * Response containing a retrieved draft email message.
 *
 * @property s3Key [String] The S3 key of the draft message.
 * @property rfc822Data [ByteArray] The RFC822 formatted email message data.
 * @property keyId [String] The identifier of the key used to encrypt the message.
 * @property updatedAt [Date] The date and time when the draft was last updated.
 */
internal data class GetDraftEmailMessageResponse(
    val s3Key: String,
    val rfc822Data: ByteArray,
    val keyId: String,
    val updatedAt: Date,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GetDraftEmailMessageResponse

        if (!rfc822Data.contentEquals(other.rfc822Data)) return false
        return s3Key == other.s3Key
    }

    override fun hashCode(): Int {
        var result = rfc822Data.contentHashCode()
        result = 31 * result + s3Key.hashCode()
        return result
    }
}

/**
 * Represents a draft email message with its content.
 *
 * @property id [String] The unique identifier of the draft message.
 * @property emailAddressId [String] The email address ID associated with the draft.
 * @property rfc822Data [ByteArray] The RFC822 formatted email message data.
 * @property keyId [String] The identifier of the key used to encrypt the message.
 * @property updatedAt [Date] The date and time when the draft was last updated.
 */
internal data class ListDraftEmailMessagesWithContentItem(
    val id: String,
    val emailAddressId: String,
    val rfc822Data: ByteArray,
    val keyId: String,
    val updatedAt: Date,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ListDraftEmailMessagesWithContentItem

        if (!rfc822Data.contentEquals(other.rfc822Data)) return false
        return id + emailAddressId == other.id + other.emailAddressId
    }

    override fun hashCode(): Int {
        var result = rfc822Data.contentHashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + emailAddressId.hashCode()
        return result
    }
}

/**
 * Metadata associated with a draft email message object.
 *
 * @property keyId [String] The identifier of the key used to encrypt the message.
 * @property algorithm [String] The encryption algorithm used.
 * @property updatedAt [Date] The date and time when the draft was last updated.
 */
internal data class DraftMessageObjectMetadata(
    val keyId: String,
    val algorithm: String,
    val updatedAt: Date,
)

/**
 * Output from listing scheduled draft messages.
 *
 * @property items [List] The list of [ScheduledDraftMessageEntity]s.
 * @property nextToken [String] Optional token for retrieving the next page of results.
 */
internal data class ListScheduledDraftMessagesOutput(
    val items: List<ScheduledDraftMessageEntity>,
    val nextToken: String?,
)

/**
 * Output from listing draft email message metadata.
 *
 * @property items [List] The list of [DraftEmailMessageMetadataEntity]s.
 * @property nextToken [String] Optional token for retrieving the next page of results.
 */
internal data class ListDraftEmailMessageMetadataOutput(
    val items: List<DraftEmailMessageMetadataEntity>,
    val nextToken: String?,
)

/**
 * Output object containing the result of listing draft email messages with content.
 *
 * @property items [List] List of draft email messages with content.
 * @property nextToken [String] Token to retrieve the next page of results, or null if no more pages.
 */
internal data class ListDraftEmailMessagesOutput(
    val items: List<DraftEmailMessageWithContentEntity>,
    val nextToken: String?,
)

/**
 * Service interface for managing draft email messages.
 *
 * Provides operations to save, retrieve, delete, schedule, and list draft email messages.
 */
internal interface DraftEmailMessageService {
    /**
     * Saves a draft email message.
     *
     * @param input [SaveDraftEmailMessageRequest] The request parameters to save the draft email message.
     * @return [String] The key of the saved draft email message.
     */
    suspend fun save(input: SaveDraftEmailMessageRequest): String

    /**
     * Retrieves a draft email message.
     *
     * @param input [GetDraftEmailMessageRequest] The request parameters to get the draft email message.
     * @return [GetDraftEmailMessageResponse] The retrieved draft email data, id, keyId and updatedAt.
     */
    suspend fun get(input: GetDraftEmailMessageRequest): GetDraftEmailMessageResponse

    /**
     * Deletes draft email messages.
     * @param input [DeleteDraftEmailMessagesRequest] The request parameters to delete the draft email messages.
     * @return [BatchOperationResultEntity] The result of the delete operation.
     */
    suspend fun delete(
        input: DeleteDraftEmailMessagesRequest,
    ): BatchOperationResultEntity<DeleteEmailMessageSuccessResultEntity, EmailMessageOperationFailureResultEntity>

    /**
     * Lists draft email message metadata for a given email address ID.
     *
     * @param emailAddressId [String] The email address ID to list draft email message metadata.
     * @param limit [Int] Optional limit for the number of results to return.
     * @param nextToken [String] Optional token for pagination.
     * @return [ListDraftEmailMessageMetadataOutput] The result containing the list of draft metadata and next token. When next token is not null additional records are available.
     */
    suspend fun listMetadataForEmailAddressId(
        emailAddressId: String,
        limit: Int? = null,
        nextToken: String? = null,
    ): ListDraftEmailMessageMetadataOutput

    /**
     * Retrieves draft message object metadata for a given S3 key.
     *
     * @param s3Key [String] The S3 key of the draft message.
     * @return The [DraftMessageObjectMetadata] associated with the S3 key.
     */
    suspend fun getObjectMetadata(s3Key: String): DraftMessageObjectMetadata

    /**
     * Schedules sending of a draft message.
     *
     * @param input [ScheduleSendDraftMessageRequest] The request parameters to schedule sending the draft message.
     * @return The [ScheduledDraftMessageEntity].
     */
    suspend fun scheduleSend(input: ScheduleSendDraftMessageRequest): ScheduledDraftMessageEntity

    /**
     * Cancels a scheduled draft message.
     *
     * @param input [CancelScheduledDraftMessageRequest] The request parameters to cancel the scheduled draft message.
     * @return [String] The ID of the canceled scheduled draft message.
     */
    suspend fun cancelScheduledDraftMessage(input: CancelScheduledDraftMessageRequest): String

    /**
     * Lists scheduled draft messages for a given email address ID.
     *
     * @param input [ListScheduledDraftMessagesForEmailAddressIdInputRequest] The request parameters to list scheduled draft messages.
     * @return [ListScheduledDraftMessagesOutput] The result containing the list of scheduled draft messages and next token.
     */
    suspend fun listScheduledDraftMessagesForEmailAddressId(
        input: ListScheduledDraftMessagesForEmailAddressIdInputRequest,
    ): ListScheduledDraftMessagesOutput
}
