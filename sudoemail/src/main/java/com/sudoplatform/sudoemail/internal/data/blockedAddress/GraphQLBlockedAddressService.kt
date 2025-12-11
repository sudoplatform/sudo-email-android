/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.blockedAddress

import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.graphql.type.BlockEmailAddressesInput
import com.sudoplatform.sudoemail.graphql.type.BlockedEmailAddressInput
import com.sudoplatform.sudoemail.graphql.type.GetEmailAddressBlocklistInput
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.graphql.type.UnblockEmailAddressesInput
import com.sudoplatform.sudoemail.internal.data.blockedAddress.transformers.BlockedAddressActionTransformer
import com.sudoplatform.sudoemail.internal.data.blockedAddress.transformers.BlockedAddressHashAlgorithmTransformer
import com.sudoplatform.sudoemail.internal.data.blockedAddress.transformers.BlockedAddressTransformer
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.BatchOperationResultTransformer
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockEmailAddressesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.GetEmailAddressBlocklistRequest
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.UnblockEmailAddressesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudologging.Logger

/**
 * Implementation of [BlockedAddressService] for managing blocked email addresses.
 *
 * This service handles blocking, unblocking, and retrieving blocked email addresses
 * by interacting with the GraphQL API client.
 *
 * @property apiClient [ApiClient] The GraphQL API client for executing mutations and queries.
 * @property logger [Logger] Logger for logging debug and error messages.
 */
internal class GraphQLBlockedAddressService(
    val apiClient: ApiClient,
    val logger: Logger,
) : BlockedAddressService {
    override suspend fun blockEmailAddresses(request: BlockEmailAddressesRequest): BatchOperationResultEntity<String, String> {
        logger.debug("GraphQLBlockedAddressService.blockEmailAddresses: $request")

        try {
            val blockedEmailAddressInputs =
                request.blockedAddresses.map {
                    BlockedEmailAddressInput(
                        hashAlgorithm = BlockedAddressHashAlgorithmTransformer.entityToGraphQL(it.hashAlgorithm),
                        hashedBlockedValue = it.hashedBlockedValue,
                        sealedValue =
                            SealedAttributeInput(
                                keyId = it.sealedValue.keyId,
                                algorithm = it.sealedValue.algorithm,
                                plainTextType = it.sealedValue.plainTextType,
                                base64EncodedSealedData = it.sealedValue.base64EncodedSealedData,
                            ),
                        action =
                            Optional.presentIfNotNull(
                                BlockedAddressActionTransformer.entityToGraphQL(it.action),
                            ),
                    )
                }

            val mutationInput =
                BlockEmailAddressesInput(
                    owner = request.owner,
                    blockedAddresses = blockedEmailAddressInputs,
                    emailAddressId = Optional.presentIfNotNull(request.emailAddressId),
                )
            val mutationResponse =
                apiClient.blockEmailAddressesMutation(
                    mutationInput,
                )

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailBlocklistError(mutationResponse.errors.first())
            }

            val result =
                mutationResponse.data?.blockEmailAddresses?.blockAddressesResult
                    ?: throw SudoEmailClient.EmailBlocklistException.FailedException(StringConstants.UNKNOWN_ERROR_MSG)
            return BatchOperationResultTransformer.graphQLToEntity(result)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailBlocklistException(e)
        }
    }

    override suspend fun unblockEmailAddresses(request: UnblockEmailAddressesRequest): BatchOperationResultEntity<String, String> {
        logger.debug("GraphQLBlockedAddressService.unblockEmailAddresses: $request")

        try {
            val mutationInput =
                UnblockEmailAddressesInput(
                    owner = request.owner,
                    unblockedAddresses = request.hashedBlockedValues,
                )
            val mutationResponse =
                apiClient.unblockEmailAddressesMutation(
                    mutationInput,
                )

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailBlocklistError(mutationResponse.errors.first())
            }

            val result =
                mutationResponse.data?.unblockEmailAddresses?.unblockAddressesResult
                    ?: throw SudoEmailClient.EmailBlocklistException.FailedException(StringConstants.UNKNOWN_ERROR_MSG)
            return BatchOperationResultTransformer.graphQLToEntity(result)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailBlocklistException(e)
        }
    }

    override suspend fun getEmailAddressBlocklist(request: GetEmailAddressBlocklistRequest): List<BlockedAddressEntity> {
        logger.debug("GraphQLBlockedAddressService.getEmailAddressBlocklist: $request")

        try {
            val queryInput =
                GetEmailAddressBlocklistInput(
                    owner = request.owner,
                )
            val queryResponse =
                apiClient.getEmailAddressBlocklistQuery(
                    queryInput,
                )

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailBlocklistError(queryResponse.errors.first())
            }

            val blockedAddresses =
                queryResponse.data
                    ?.getEmailAddressBlocklist
                    ?.getEmailAddressBlocklistResponse
                    ?.blockedAddresses
            return blockedAddresses?.map { BlockedAddressTransformer.graphQLToEntity(it) } ?: emptyList()
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailBlocklistException(e)
        }
    }
}
