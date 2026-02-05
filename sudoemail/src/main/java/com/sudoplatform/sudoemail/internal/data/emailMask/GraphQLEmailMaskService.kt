/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMask

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.util.Base64
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.graphql.type.DeprovisionEmailMaskInput
import com.sudoplatform.sudoemail.graphql.type.DisableEmailMaskInput
import com.sudoplatform.sudoemail.graphql.type.EnableEmailMaskInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailMasksForOwnerInput
import com.sudoplatform.sudoemail.graphql.type.ProvisionEmailMaskInput
import com.sudoplatform.sudoemail.graphql.type.ProvisionEmailMaskPublicKeyInput
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailMaskInput
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.common.transformers.PublicKeyFormatTransformer
import com.sudoplatform.sudoemail.internal.data.emailMask.transformers.EmailMaskFilterTransformer
import com.sudoplatform.sudoemail.internal.data.emailMask.transformers.EmailMaskTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.DeprovisionEmailMaskRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.DisableEmailMaskRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EnableEmailMaskRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.ListEmailMasksForOwnerRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.ListEmailMasksOutput
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.ProvisionEmailMaskRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.SealedEmailMaskEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.UpdateEmailMaskRequest
import com.sudoplatform.sudologging.Logger

/**
 * Implementation of [EmailMaskService] for managing email masks.
 *
 * This service handles checking availability, provisioning, deprovisioning,
 * updating metadata, retrieving, listing, and looking up public info of email addresses
 * by interacting with the GraphQL API client.
 *
 * @property apiClient [ApiClient] The GraphQL API client for executing mutations and queries.
 * @property logger [Logger] Logger for logging debug and error messages.
 */
internal class GraphQLEmailMaskService(
    val apiClient: ApiClient,
    val logger: Logger,
) : EmailMaskService {
    override suspend fun provision(input: ProvisionEmailMaskRequest): SealedEmailMaskEntity {
        logger.debug("provisionEmailMask input: $input")

        try {
            val keyInput =
                ProvisionEmailMaskPublicKeyInput(
                    keyId = input.keyPair.keyId,
                    publicKey = Base64.encodeAsString(*input.keyPair.publicKey),
                    algorithm = "RSAEncryptionOAEPAESCBC",
                    keyFormat = PublicKeyFormatTransformer.toGraphQLType(input.keyPair.getKeyFormat()),
                )
            val mutationInput =
                ProvisionEmailMaskInput(
                    maskAddress = input.maskAddress,
                    realAddress = input.realAddress,
                    ownershipProofTokens = listOf(input.ownershipProofToken),
                    metadata = Optional.presentIfNotNull(input.metadata),
                    expiresAtEpochSec = Optional.presentIfNotNull(input.expiresAt?.let { (it.time / 1000).toInt() }),
                    key = keyInput,
                )

            val mutationResponse = apiClient.provisionEmailMaskMutation(mutationInput)

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailMaskError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.provisionEmailMask?.emailMask
            result?.let {
                return EmailMaskTransformer.graphQLToSealedEntity(result)
            }
            logger.error("no email mask returned")
            throw SudoEmailClient.EmailMaskException.ProvisionFailedException("No email mask returned")
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMaskException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailMaskException(e)
            }
        }
    }

    override suspend fun deprovision(input: DeprovisionEmailMaskRequest): SealedEmailMaskEntity {
        try {
            val mutationInput =
                DeprovisionEmailMaskInput(
                    emailMaskId = input.emailMaskId,
                )
            val mutationResponse = apiClient.deprovisionEmailMaskMutation(mutationInput)

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailMaskError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.deprovisionEmailMask?.emailMask
            result?.let {
                return EmailMaskTransformer.graphQLToSealedEntity(result)
            }
            throw SudoEmailClient.EmailMaskException.DeprovisionFailedException("No email mask returned")
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMaskException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailMaskException(e)
            }
        }
    }

    override suspend fun update(input: UpdateEmailMaskRequest): SealedEmailMaskEntity {
        try {
            val metadata =
                if (input.metadata === null &&
                    input.clearMetadata
                ) {
                    Optional.Present(null)
                } else {
                    Optional.presentIfNotNull(input.metadata)
                }
            val expiresAtEpochSec =
                if (input.expiresAt == null &&
                    input.clearExpiresAt
                ) {
                    Optional.Present(null)
                } else {
                    Optional.presentIfNotNull(input.expiresAt?.let { (it.time / 1000).toInt() })
                }
            val mutationInput =
                UpdateEmailMaskInput(
                    id = input.id,
                    metadata = metadata,
                    expiresAtEpochSec = expiresAtEpochSec,
                )
            val mutationResponse = apiClient.updateEmailMaskMutation(mutationInput)

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailMaskError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.updateEmailMask?.emailMask
            result?.let {
                return EmailMaskTransformer.graphQLToSealedEntity(result)
            }
            throw SudoEmailClient.EmailMaskException.UpdateFailedException("No email mask returned")
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMaskException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailMaskException(e)
            }
        }
    }

    override suspend fun enable(input: EnableEmailMaskRequest): SealedEmailMaskEntity {
        try {
            val mutationInput =
                EnableEmailMaskInput(
                    emailMaskId = input.emailMaskId,
                )
            val mutationResponse = apiClient.enableEmailMaskMutation(mutationInput)

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailMaskError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.enableEmailMask?.emailMask
            result?.let {
                return EmailMaskTransformer.graphQLToSealedEntity(result)
            }
            throw SudoEmailClient.EmailMaskException.UpdateFailedException("No email mask returned")
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMaskException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailMaskException(e)
            }
        }
    }

    override suspend fun disable(input: DisableEmailMaskRequest): SealedEmailMaskEntity {
        try {
            val mutationInput =
                DisableEmailMaskInput(
                    emailMaskId = input.emailMaskId,
                )
            val mutationResponse = apiClient.disableEmailMaskMutation(mutationInput)

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailMaskError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.disableEmailMask?.emailMask
            result?.let {
                return EmailMaskTransformer.graphQLToSealedEntity(result)
            }
            throw SudoEmailClient.EmailMaskException.UpdateFailedException("No email mask returned")
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMaskException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailMaskException(e)
            }
        }
    }

    override suspend fun listForOwner(input: ListEmailMasksForOwnerRequest): ListEmailMasksOutput {
        try {
            val queryInput =
                ListEmailMasksForOwnerInput(
                    filter = Optional.presentIfNotNull(input.filter?.let { EmailMaskFilterTransformer.entityToGraphQl(it) }),
                    limit = Optional.presentIfNotNull(input.limit),
                    nextToken = Optional.presentIfNotNull(input.nextToken),
                )
            val queryResponse = apiClient.listEmailMasksForOwnerQuery(queryInput)

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailMaskError(queryResponse.errors.first())
            }

            val queryResult = queryResponse.data?.listEmailMasksForOwner
            val emailMasks = queryResult?.items ?: emptyList()
            val newNextToken = queryResult?.nextToken

            return ListEmailMasksOutput(
                items = emailMasks.map { EmailMaskTransformer.graphQLToSealedEntity(it.emailMask) },
                nextToken = newNextToken,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMaskException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailMaskException(e)
            }
        }
    }
}
