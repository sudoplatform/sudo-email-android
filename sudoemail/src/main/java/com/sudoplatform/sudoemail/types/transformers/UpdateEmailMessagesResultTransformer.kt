/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.graphql.fragment.UpdateEmailMessagesResult
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailMessagesStatus
import com.sudoplatform.sudoemail.types.BatchOperationResult
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.EmailMessageOperationFailureResult
import com.sudoplatform.sudoemail.types.UpdatedEmailMessageResult.UpdatedEmailMessageSuccess

/**
 * Transformer responsible for transforming the [UpdateEmailMessagesResult] data
 * to the entity type that is exposed to users.
 */
internal object UpdateEmailMessagesResultTransformer {

    /**
     * Transform the [UpdateEmailMessagesResult] result type to its entity type.
     *
     * @param result [UpdateEmailMessagesResult] The update email messages result type.
     * @return The [BatchOperationResult<UpdatedEmailMessageSuccess, EmailMessageOperationFailureResult>] entity type.
     */
    fun toEntity(
        result: UpdateEmailMessagesResult,
    ): BatchOperationResult<UpdatedEmailMessageSuccess, EmailMessageOperationFailureResult> {
        val status: BatchOperationStatus =
            if (result.status() == UpdateEmailMessagesStatus.FAILED) {
                BatchOperationStatus.FAILURE
            } else if (result.status() == UpdateEmailMessagesStatus.PARTIAL) {
                BatchOperationStatus.PARTIAL
            } else {
                BatchOperationStatus.SUCCESS
            }

        val successMessages: List<UpdatedEmailMessageSuccess> =
            result.successMessages()
                ?.map {
                    UpdatedEmailMessageSuccess(
                        it.id(),
                        it.createdAtEpochMs().toDate(),
                        it.updatedAtEpochMs().toDate(),
                    )
                }
                ?: emptyList()
        val failureMessages: List<EmailMessageOperationFailureResult> =
            result.failedMessages()
                ?.map {
                    EmailMessageOperationFailureResult(
                        it.id(),
                        it.errorType(),
                    )
                }
                ?: emptyList()
        return BatchOperationResult.createDifferent(
            status,
            successMessages,
            failureMessages,
        )
    }
}
