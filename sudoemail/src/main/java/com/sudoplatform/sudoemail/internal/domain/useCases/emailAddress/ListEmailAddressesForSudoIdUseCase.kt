/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress

import com.sudoplatform.sudoemail.internal.data.common.mechanisms.EmailAddressUnsealer
import com.sudoplatform.sudoemail.internal.data.emailAddress.transformers.EmailAddressTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListAPIResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListPartialResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListSuccessResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.PartialResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesForSudoIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PartialEmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.UnsealedEmailAddressEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudologging.Logger

/**
 * Input for the list email addresses for sudo ID use case.
 *
 * @property sudoId [String] The sudo ID to filter email addresses by.
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 */
internal data class ListEmailAddressesForSudoIdUseCaseInput(
    val sudoId: String,
    val limit: Int?,
    val nextToken: String?,
)

/**
 * Use case for listing email addresses associated with a specific sudo.
 *
 * This use case retrieves and unseals (decrypts) email addresses for a given sudo ID.
 *
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property logger [Logger] Logger for debugging.
 */
internal class ListEmailAddressesForSudoIdUseCase(
    private val emailAddressService: EmailAddressService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the list email addresses for sudo ID use case.
     *
     * @param input [ListEmailAddressesForSudoIdUseCaseInput] The input parameters.
     * @return [ListAPIResultEntity] The result containing unsealed email addresses and partial results.
     */
    suspend fun execute(
        input: ListEmailAddressesForSudoIdUseCaseInput,
    ): ListAPIResultEntity<UnsealedEmailAddressEntity, PartialEmailAddressEntity> {
        logger.debug("ListEmailAddressesForSudoIdUseCase execute input: $input")
        val (items, nextToken) =
            emailAddressService.listForSudoId(
                input =
                    ListEmailAddressesForSudoIdRequest(
                        sudoId = input.sudoId,
                        limit = input.limit,
                        nextToken = input.nextToken,
                    ),
            )

        val success: MutableList<UnsealedEmailAddressEntity> = mutableListOf()
        val partials: MutableList<PartialResultEntity<PartialEmailAddressEntity>> = mutableListOf()
        val emailAddressUnsealer =
            EmailAddressUnsealer(
                this.serviceKeyManager,
            )
        for (sealedEmailAddress in items) {
            try {
                val unsealed = emailAddressUnsealer.unseal(sealedEmailAddress)
                success.add(unsealed)
            } catch (e: Exception) {
                logger.warning("ListEmailAddressesForSudoIdUseCase failed to unseal email address ${sealedEmailAddress.id}: $e")
                val partialEmailAddress =
                    EmailAddressTransformer
                        .sealedEntityToPartialEntity(sealedEmailAddress)
                val partialResult = PartialResultEntity(partialEmailAddress, e)
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
