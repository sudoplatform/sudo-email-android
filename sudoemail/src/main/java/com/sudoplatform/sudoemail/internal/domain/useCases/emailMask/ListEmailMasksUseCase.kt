/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMask

import com.sudoplatform.sudoemail.internal.data.common.mechanisms.EmailMaskUnsealer
import com.sudoplatform.sudoemail.internal.data.emailMask.transformers.EmailMaskFilterTransformer
import com.sudoplatform.sudoemail.internal.data.emailMask.transformers.EmailMaskTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListAPIResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListPartialResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListSuccessResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.PartialResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskFilter
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.ListEmailMasksForOwnerRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.PartialEmailMaskEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.UnsealedEmailMaskEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.EmailMaskFilterInput
import com.sudoplatform.sudologging.Logger

/**
 * Input for the list email masks use case.
 *
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 * @property filter [EmailMaskFilter] Optional filter criteria for the email masks.
 */
internal data class ListEmailMasksUseCaseInput(
    val limit: Int?,
    val nextToken: String?,
    val filter: EmailMaskFilterInput?,
)

/**
 * Use case for listing email masks for an owner.
 *
 * This use case retrieves sealed email masks with optional pagination and filtering.
 * Unsealing is handled at a higher level.
 *
 * @property emailMaskService [EmailMaskService] Service for email mask operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property logger [Logger] Logger for debugging.
 */
internal class ListEmailMasksUseCase(
    private val emailMaskService: EmailMaskService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the list email masks use case.
     *
     * @param input [ListEmailMasksUseCaseInput] The input parameters.
     * @return [ListAPIResultEntity] The result containing sealed email masks.
     */
    suspend fun execute(input: ListEmailMasksUseCaseInput): ListAPIResultEntity<UnsealedEmailMaskEntity, PartialEmailMaskEntity> {
        logger.debug("ListEmailMasksUseCase execute input: $input")
        val (items, nextToken) =
            emailMaskService.listForOwner(
                input =
                    ListEmailMasksForOwnerRequest(
                        limit = input.limit,
                        nextToken = input.nextToken,
                        filter = input.filter?.let { EmailMaskFilterTransformer.apiToEntity(it) },
                    ),
            )

        val success: MutableList<UnsealedEmailMaskEntity> = mutableListOf()
        val partials: MutableList<PartialResultEntity<PartialEmailMaskEntity>> = mutableListOf()
        val emailMaskUnsealer =
            EmailMaskUnsealer(
                this.serviceKeyManager,
            )
        for (sealedEmailMask in items) {
            try {
                val unsealed = emailMaskUnsealer.unseal(sealedEmailMask)
                success.add(unsealed)
            } catch (e: Exception) {
                logger.warning("ListEmailMasksForOwnerUseCase failed to unseal email mask ${sealedEmailMask.id}: $e")
                val partialEmailMask =
                    EmailMaskTransformer
                        .toPartialEntity(sealedEmailMask)
                val partialResult = PartialResultEntity(partialEmailMask, e)
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
    }
}
