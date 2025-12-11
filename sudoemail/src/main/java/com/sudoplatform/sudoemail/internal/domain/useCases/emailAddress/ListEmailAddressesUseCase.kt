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
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PartialEmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.UnsealedEmailAddressEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudologging.Logger

/**
 * Input for the list email addresses use case.
 *
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 */
internal data class ListEmailAddressesUseCaseInput(
    val limit: Int?,
    val nextToken: String?,
)

/**
 * Use case for listing email addresses.
 *
 * This use case retrieves and unseals (decrypts) email addresses with optional pagination.
 *
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property logger [Logger] Logger for debugging.
 */
internal class ListEmailAddressesUseCase(
    private val emailAddressService: EmailAddressService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the list email addresses use case.
     *
     * @param input [ListEmailAddressesUseCaseInput] The input parameters.
     * @return [ListAPIResultEntity] The result containing unsealed email addresses and partial results.
     */
    suspend fun execute(input: ListEmailAddressesUseCaseInput): ListAPIResultEntity<UnsealedEmailAddressEntity, PartialEmailAddressEntity> {
        logger.debug("ListEmailAddressesUseCase execute input: $input")
        val (items, nextToken) =
            emailAddressService.list(
                input =
                    ListEmailAddressesRequest(
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
                logger.warning("ListEmailAddressesUseCase failed to unseal email address ${sealedEmailAddress.id}: $e")
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
