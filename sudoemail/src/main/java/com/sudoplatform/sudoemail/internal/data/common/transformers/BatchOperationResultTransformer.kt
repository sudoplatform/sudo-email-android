/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.common.transformers

import com.sudoplatform.sudoemail.graphql.fragment.BlockAddressesResult
import com.sudoplatform.sudoemail.graphql.fragment.UnblockAddressesResult
import com.sudoplatform.sudoemail.graphql.fragment.UpdateEmailMessagesResult
import com.sudoplatform.sudoemail.graphql.type.BlockEmailAddressesBulkUpdateStatus
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailMessagesStatus
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.EmailMessageOperationFailureResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.DeleteEmailMessageSuccessResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.UpdatedEmailMessageResultEntity
import com.sudoplatform.sudoemail.internal.util.toDate
import com.sudoplatform.sudoemail.types.BatchOperationResult
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.DeleteEmailMessageSuccessResult
import com.sudoplatform.sudoemail.types.EmailMessageOperationFailureResult
import com.sudoplatform.sudoemail.types.UpdatedEmailMessageResult

/**
 * Transformer responsible for transforming the BatchOperationResult data
 * to the entity type that is exposed to users.
 */
internal object BatchOperationResultTransformer {
    /**
     * Transform the [UpdateEmailMessagesResult] result type to its entity type.
     *
     * @param result [UpdateEmailMessagesResult] The update email messages result type.
     * @return The [BatchOperationResult<UpdatedEmailMessageSuccess, EmailMessageOperationFailureResult>] entity type.
     */
    fun graphQLToEntity(
        result: UpdateEmailMessagesResult,
    ): BatchOperationResultEntity<
        UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity,
        EmailMessageOperationFailureResultEntity,
    > {
        val status: BatchOperationStatusEntity =
            when (result.status) {
                UpdateEmailMessagesStatus.SUCCESS -> BatchOperationStatusEntity.SUCCESS
                UpdateEmailMessagesStatus.FAILED -> BatchOperationStatusEntity.FAILURE
                else -> BatchOperationStatusEntity.PARTIAL
            }

        val successMessages: List<UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity> =
            result.successMessages
                ?.map {
                    UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity(
                        it.id,
                        it.createdAtEpochMs.toDate(),
                        it.updatedAtEpochMs.toDate(),
                    )
                }
                ?: emptyList()
        val failureMessages: List<EmailMessageOperationFailureResultEntity> =
            result.failedMessages
                ?.map {
                    EmailMessageOperationFailureResultEntity(
                        it.id,
                        it.errorType,
                    )
                }
                ?: emptyList()
        return BatchOperationResultEntity(
            status,
            successMessages,
            failureMessages,
        )
    }

    /**
     * Transforms a GraphQL block addresses result to entity type.
     *
     * @param result [BlockAddressesResult] The GraphQL block addresses result.
     * @return [BatchOperationResultEntity] The entity containing success and failure addresses.
     */
    fun graphQLToEntity(result: BlockAddressesResult): BatchOperationResultEntity<String, String> {
        val status: BatchOperationStatusEntity =
            when (result.status) {
                BlockEmailAddressesBulkUpdateStatus.SUCCESS -> BatchOperationStatusEntity.SUCCESS
                BlockEmailAddressesBulkUpdateStatus.FAILED -> BatchOperationStatusEntity.FAILURE
                else -> BatchOperationStatusEntity.PARTIAL
            }
        return BatchOperationResultEntity(
            status,
            result.successAddresses,
            result.failedAddresses,
        )
    }

    /**
     * Transforms a GraphQL unblock addresses result to entity type.
     *
     * @param result [UnblockAddressesResult] The GraphQL unblock addresses result.
     * @return [BatchOperationResultEntity] The entity containing success and failure addresses.
     */
    fun graphQLToEntity(result: UnblockAddressesResult): BatchOperationResultEntity<String, String> {
        val status: BatchOperationStatusEntity =
            when (result.status) {
                BlockEmailAddressesBulkUpdateStatus.SUCCESS -> BatchOperationStatusEntity.SUCCESS
                BlockEmailAddressesBulkUpdateStatus.FAILED -> BatchOperationStatusEntity.FAILURE
                else -> BatchOperationStatusEntity.PARTIAL
            }
        return BatchOperationResultEntity(
            status,
            result.successAddresses,
            result.failedAddresses,
        )
    }

    /**
     * Transforms a batch update email messages entity result to API type.
     *
     * @param entity [BatchOperationResultEntity] The entity containing updated message results.
     * @return [BatchOperationResult] The API result type.
     */
    fun batchUpdateEmailMessagesEntityToApi(
        entity: BatchOperationResultEntity<
            UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity,
            EmailMessageOperationFailureResultEntity,
        >,
    ): BatchOperationResult<UpdatedEmailMessageResult.UpdatedEmailMessageSuccess, EmailMessageOperationFailureResult> {
        val status: BatchOperationStatus =
            when (entity.status) {
                BatchOperationStatusEntity.SUCCESS -> BatchOperationStatus.SUCCESS
                BatchOperationStatusEntity.FAILURE -> BatchOperationStatus.FAILURE
                BatchOperationStatusEntity.PARTIAL -> BatchOperationStatus.PARTIAL
            }

        val successMessages: List<UpdatedEmailMessageResult.UpdatedEmailMessageSuccess> =
            entity.successValues
                ?.map {
                    UpdatedEmailMessageResult.UpdatedEmailMessageSuccess(
                        it.id,
                        it.createdAt,
                        it.updatedAt,
                    )
                } ?: emptyList()
        val failureMessages: List<EmailMessageOperationFailureResult> =
            entity.failureValues
                ?.map {
                    EmailMessageOperationFailureResult(
                        it.id,
                        it.errorType,
                    )
                } ?: emptyList()
        return BatchOperationResult.createDifferent(
            status,
            successMessages,
            failureMessages,
        )
    }

    /**
     * Transforms a batch delete draft messages entity result to API type.
     *
     * @param entity [BatchOperationResultEntity] The entity containing deleted draft message results.
     * @return [BatchOperationResult] The API result type.
     */
    fun batchDeleteDraftMessagesEntityToApi(
        entity: BatchOperationResultEntity<
            DeleteEmailMessageSuccessResultEntity,
            EmailMessageOperationFailureResultEntity,
        >,
    ): BatchOperationResult<
        DeleteEmailMessageSuccessResult,
        EmailMessageOperationFailureResult,
    > {
        val status: BatchOperationStatus =
            when (entity.status) {
                BatchOperationStatusEntity.SUCCESS -> BatchOperationStatus.SUCCESS
                BatchOperationStatusEntity.FAILURE -> BatchOperationStatus.FAILURE
                BatchOperationStatusEntity.PARTIAL -> BatchOperationStatus.PARTIAL
            }

        val successMessages: List<DeleteEmailMessageSuccessResult> =
            entity.successValues
                ?.map {
                    DeleteEmailMessageSuccessResult(
                        it.id,
                    )
                } ?: emptyList()
        val failureMessages: List<EmailMessageOperationFailureResult> =
            entity.failureValues
                ?.map {
                    EmailMessageOperationFailureResult(
                        it.id,
                        it.errorType,
                    )
                } ?: emptyList()
        return BatchOperationResult.createDifferent(
            status,
            successMessages,
            failureMessages,
        )
    }
}
