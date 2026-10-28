/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailAlias

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
import com.sudoplatform.sudoemail.graphql.type.VerifyExternalEmailAddressInput
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.common.transformers.PublicKeyFormatTransformer
import com.sudoplatform.sudoemail.internal.data.emailAlias.transformers.EmailAliasFilterTransformer
import com.sudoplatform.sudoemail.internal.data.emailAlias.transformers.EmailAliasTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.DeprovisionEmailAliasRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.DisableEmailAliasRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EnableEmailAliasRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.ListEmailAliasesForOwnerRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.ListEmailAliasesOutput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.ProvisionEmailAliasRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.SealedEmailAliasEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.UpdateEmailAliasRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.VerifyExternalEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.VerifyExternalEmailAddressResultEntity
import com.sudoplatform.sudologging.Logger

/**
 * Implementation of [EmailAliasService] for managing email aliases.
 *
 * This service handles checking availability, provisioning, deprovisioning,
 * updating metadata, retrieving, listing, and looking up public info of email addresses
 * by interacting with the GraphQL API client.
 *
 * @property apiClient [ApiClient] The GraphQL API client for executing mutations and queries.
 * @property logger [Logger] Logger for logging debug and error messages.
 */
internal class GraphQLEmailAliasService(
    val apiClient: ApiClient,
    val logger: Logger,
) : EmailAliasService {
    override suspend fun provision(input: ProvisionEmailAliasRequest): SealedEmailAliasEntity {
        logger.debug("provisionEmailAlias input: $input")

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
                    maskAddress = input.aliasAddress,
                    realAddress = input.realAddress,
                    ownershipProofTokens = listOf(input.ownershipProofToken),
                    metadata = Optional.presentIfNotNull(input.metadata),
                    expiresAtEpochSec = Optional.presentIfNotNull(input.expiresAt?.let { (it.time / 1000).toInt() }),
                    key = keyInput,
                )

            val mutationResponse = apiClient.provisionEmailMaskMutation(mutationInput)

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailAliasError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.provisionEmailMask?.emailMask
            result?.let {
                return EmailAliasTransformer.graphQLToSealedEntity(result)
            }
            logger.error("no email alias returned")
            throw SudoEmailClient.EmailAliasException.ProvisionFailedException("No email alias returned")
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAliasException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAliasException(e)
            }
        }
    }

    override suspend fun deprovision(input: DeprovisionEmailAliasRequest): SealedEmailAliasEntity {
        try {
            val mutationInput =
                DeprovisionEmailMaskInput(
                    emailMaskId = input.emailAliasId,
                )
            val mutationResponse = apiClient.deprovisionEmailMaskMutation(mutationInput)

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailAliasError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.deprovisionEmailMask?.emailMask
            result?.let {
                return EmailAliasTransformer.graphQLToSealedEntity(result)
            }
            throw SudoEmailClient.EmailAliasException.DeprovisionFailedException("No email alias returned")
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAliasException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAliasException(e)
            }
        }
    }

    override suspend fun update(input: UpdateEmailAliasRequest): SealedEmailAliasEntity {
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
                    realAddress = Optional.presentIfNotNull(input.realAddress),
                )
            val mutationResponse = apiClient.updateEmailMaskMutation(mutationInput)

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailAliasError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.updateEmailMask?.emailMask
            result?.let {
                return EmailAliasTransformer.graphQLToSealedEntity(result)
            }
            throw SudoEmailClient.EmailAliasException.UpdateFailedException("No email alias returned")
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAliasException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAliasException(e)
            }
        }
    }

    override suspend fun enable(input: EnableEmailAliasRequest): SealedEmailAliasEntity {
        try {
            val mutationInput =
                EnableEmailMaskInput(
                    emailMaskId = input.emailAliasId,
                )
            val mutationResponse = apiClient.enableEmailMaskMutation(mutationInput)

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailAliasError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.enableEmailMask?.emailMask
            result?.let {
                return EmailAliasTransformer.graphQLToSealedEntity(result)
            }
            throw SudoEmailClient.EmailAliasException.UpdateFailedException("No email alias returned")
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAliasException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAliasException(e)
            }
        }
    }

    override suspend fun disable(input: DisableEmailAliasRequest): SealedEmailAliasEntity {
        try {
            val mutationInput =
                DisableEmailMaskInput(
                    emailMaskId = input.emailAliasId,
                )
            val mutationResponse = apiClient.disableEmailMaskMutation(mutationInput)

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailAliasError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.disableEmailMask?.emailMask
            result?.let {
                return EmailAliasTransformer.graphQLToSealedEntity(result)
            }
            throw SudoEmailClient.EmailAliasException.UpdateFailedException("No email alias returned")
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAliasException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAliasException(e)
            }
        }
    }

    override suspend fun listForOwner(input: ListEmailAliasesForOwnerRequest): ListEmailAliasesOutput {
        try {
            val queryInput =
                ListEmailMasksForOwnerInput(
                    filter = Optional.presentIfNotNull(input.filter?.let { EmailAliasFilterTransformer.entityToGraphQl(it) }),
                    limit = Optional.presentIfNotNull(input.limit),
                    nextToken = Optional.presentIfNotNull(input.nextToken),
                )
            val queryResponse = apiClient.listEmailMasksForOwnerQuery(queryInput)

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailAliasError(queryResponse.errors.first())
            }

            val queryResult = queryResponse.data?.listEmailMasksForOwner
            val emailAliases = queryResult?.items ?: emptyList()
            val newNextToken = queryResult?.nextToken

            return ListEmailAliasesOutput(
                items = emailAliases.map { EmailAliasTransformer.graphQLToSealedEntity(it.emailMask) },
                nextToken = newNextToken,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAliasException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAliasException(e)
            }
        }
    }

    override suspend fun verifyExternalEmailAddress(input: VerifyExternalEmailAddressRequest): VerifyExternalEmailAddressResultEntity {
        try {
            val mutationInput =
                VerifyExternalEmailAddressInput(
                    emailAddress = input.emailAddress,
                    emailMaskId = input.emailAliasId,
                    verificationCode = Optional.presentIfNotNull(input.verificationCode),
                )
            val mutationResponse = apiClient.verifyExternalAddressMutation(mutationInput)

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailAliasError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.verifyExternalEmailAddress?.verifyExternalEmailAddressResult
            // Handle nil response (verification email sent case)
            if (result == null) {
                return VerifyExternalEmailAddressResultEntity(
                    isVerified = false,
                    reason = "Verification email sent",
                )
            }

            return VerifyExternalEmailAddressResultEntity(
                isVerified = result.isVerified,
                reason = result.reason,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAliasException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAliasException(e)
            }
        }
    }
}
