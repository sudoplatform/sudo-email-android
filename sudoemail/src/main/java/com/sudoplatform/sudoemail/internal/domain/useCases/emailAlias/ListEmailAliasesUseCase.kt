/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias

import com.sudoplatform.sudoemail.internal.data.common.mechanisms.EmailAliasUnsealer
import com.sudoplatform.sudoemail.internal.data.emailAlias.transformers.EmailAliasFilterTransformer
import com.sudoplatform.sudoemail.internal.data.emailAlias.transformers.EmailAliasTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListAPIResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListPartialResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListSuccessResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.PartialResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasFilter
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.ListEmailAliasesForOwnerRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.PartialEmailAliasEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.UnsealedEmailAliasEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.EmailAliasFilterInput
import com.sudoplatform.sudologging.Logger

/**
 * Input for the list email aliases use case.
 *
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 * @property filter [EmailAliasFilter] Optional filter criteria for the email aliases.
 */
internal data class ListEmailAliasesUseCaseInput(
    val limit: Int?,
    val nextToken: String?,
    val filter: EmailAliasFilterInput?,
)

/**
 * Use case for listing email aliases for an owner.
 *
 * This use case retrieves sealed email aliases with optional pagination and filtering.
 * Unsealing is handled at a higher level.
 *
 * @property emailAliasService [EmailAliasService] Service for email alias operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property logger [Logger] Logger for debugging.
 */
internal class ListEmailAliasesUseCase(
    private val emailAliasService: EmailAliasService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the list email aliases use case.
     *
     * @param input [ListEmailAliasesUseCaseInput] The input parameters.
     * @return [ListAPIResultEntity] The result containing sealed email aliases.
     */
    suspend fun execute(input: ListEmailAliasesUseCaseInput): ListAPIResultEntity<UnsealedEmailAliasEntity, PartialEmailAliasEntity> {
        logger.debug("ListEmailAliasesUseCase execute input: $input")
        val (items, nextToken) =
            emailAliasService.listForOwner(
                input =
                    ListEmailAliasesForOwnerRequest(
                        limit = input.limit,
                        nextToken = input.nextToken,
                        filter = input.filter?.let { EmailAliasFilterTransformer.apiToEntity(it) },
                    ),
            )

        val success: MutableList<UnsealedEmailAliasEntity> = mutableListOf()
        val partials: MutableList<PartialResultEntity<PartialEmailAliasEntity>> = mutableListOf()
        val emailAliasUnsealer =
            EmailAliasUnsealer(
                this.serviceKeyManager,
            )
        for (sealedEmailAlias in items) {
            try {
                val unsealed = emailAliasUnsealer.unseal(sealedEmailAlias)
                success.add(unsealed)
            } catch (e: Exception) {
                logger.warning("ListEmailAliasesForOwnerUseCase failed to unseal email alias ${sealedEmailAlias.id}: $e")
                val partialEmailAlias =
                    EmailAliasTransformer
                        .toPartialEntity(sealedEmailAlias)
                val partialResult = PartialResultEntity(partialEmailAlias, e)
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
