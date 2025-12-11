/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailAddress

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.util.Base64
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.graphql.type.CheckEmailAddressAvailabilityInput
import com.sudoplatform.sudoemail.graphql.type.DeprovisionEmailAddressInput
import com.sudoplatform.sudoemail.graphql.type.EmailAddressMetadataUpdateValuesInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailAddressesForSudoIdInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailAddressesInput
import com.sudoplatform.sudoemail.graphql.type.LookupEmailAddressesPublicInfoInput
import com.sudoplatform.sudoemail.graphql.type.ProvisionEmailAddressInput
import com.sudoplatform.sudoemail.graphql.type.ProvisionEmailAddressPublicKeyInput
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailAddressMetadataInput
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.emailAddress.transformers.EmailAddressPublicInfoTransformer
import com.sudoplatform.sudoemail.internal.data.emailAddress.transformers.EmailAddressTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.CheckEmailAddressAvailabilityRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.DeprovisionEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressPublicInfoEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.GetEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesForSudoIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesOutput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.LookupEmailAddressesPublicInfoRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ProvisionEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.SealedEmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.UpdateEmailAddressMetadataRequest
import com.sudoplatform.sudologging.Logger

/**
 * Implementation of [EmailAddressService] for managing email addresses.
 *
 * This service handles checking availability, provisioning, deprovisioning,
 * updating metadata, retrieving, listing, and looking up public info of email addresses
 * by interacting with the GraphQL API client.
 *
 * @property apiClient [ApiClient] The GraphQL API client for executing mutations and queries.
 * @property logger [Logger] Logger for logging debug and error messages.
 */
internal class GraphQLEmailAddressService(
    val apiClient: ApiClient,
    val logger: Logger,
) : EmailAddressService {
    override suspend fun checkAvailability(input: CheckEmailAddressAvailabilityRequest): List<String> {
        try {
            val queryInput =
                CheckEmailAddressAvailabilityInput(
                    localParts = input.localParts,
                    domains = Optional.presentIfNotNull(input.domains),
                )
            val queryResponse =
                apiClient.checkEmailAddressAvailabilityQuery(
                    queryInput,
                )

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailAddressError(queryResponse.errors.first())
            }
            return queryResponse.data?.checkEmailAddressAvailability?.addresses ?: return emptyList()
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAddressException(e)
            }
        }
    }

    override suspend fun provision(input: ProvisionEmailAddressRequest): SealedEmailAddressEntity {
        logger.debug("provisionEmailAddress input: $input")
        val keyInput =
            ProvisionEmailAddressPublicKeyInput(
                keyId = input.keyPair.keyId,
                publicKey = Base64.encodeAsString(*input.keyPair.publicKey),
                algorithm = "RSAEncryptionOAEPAESCBC",
            )
        val mutationInput =
            ProvisionEmailAddressInput(
                emailAddress = input.emailAddress,
                ownershipProofTokens = listOf(input.ownershipProofToken),
                key = keyInput,
                alias = Optional.presentIfNotNull(input.alias),
            )

        try {
            val mutationResponse =
                apiClient.provisionEmailAddressMutation(
                    mutationInput,
                )

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailAddressError(mutationResponse.errors.first())
            }

            val result =
                mutationResponse.data?.provisionEmailAddress?.emailAddress
            result?.let {
                return EmailAddressTransformer.graphQLToSealedEntity(result)
            }
            logger.error("no email address returned")
            throw SudoEmailClient.EmailAddressException.ProvisionFailedException(StringConstants.NO_EMAIL_ADDRESS_ERROR_MSG)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAddressException(e)
            }
        }
    }

    override suspend fun deprovision(input: DeprovisionEmailAddressRequest): SealedEmailAddressEntity {
        try {
            val mutationInput =
                DeprovisionEmailAddressInput(
                    emailAddressId = input.emailAddressId,
                )
            val mutationResponse =
                apiClient.deprovisionEmailAddressMutation(
                    mutationInput,
                )

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailAddressError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.deprovisionEmailAddress?.emailAddressWithoutFolders
            result?.let {
                return EmailAddressTransformer.graphQLToSealedEntity(result)
            }
            throw SudoEmailClient.EmailAddressException.DeprovisionFailedException(
                StringConstants.NO_EMAIL_ADDRESS_ERROR_MSG,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAddressException(e)
            }
        }
    }

    override suspend fun updateMetadata(input: UpdateEmailAddressMetadataRequest): String {
        try {
            val updateValuesInput =
                EmailAddressMetadataUpdateValuesInput(
                    alias = Optional.presentIfNotNull(input.alias),
                )
            val mutationInput =
                UpdateEmailAddressMetadataInput(
                    id = input.id,
                    values = updateValuesInput,
                )
            val mutationResponse =
                apiClient.updateEmailAddressMetadataMutation(
                    mutationInput,
                )

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors}")
                throw ErrorTransformer.interpretEmailAddressError(mutationResponse.errors.first())
            }

            val result = mutationResponse.data?.updateEmailAddressMetadata
            return result ?: throw SudoEmailClient.EmailAddressException.UpdateFailedException(
                StringConstants.NO_EMAIL_ADDRESS_ERROR_MSG,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAddressException(e)
            }
        }
    }

    override suspend fun get(input: GetEmailAddressRequest): SealedEmailAddressEntity? {
        val queryResponse =
            apiClient.getEmailAddressQuery(
                id = input.id,
            )

        if (queryResponse.hasErrors()) {
            logger.error("errors = ${queryResponse.errors}")
            throw ErrorTransformer.interpretEmailAddressError(queryResponse.errors.first())
        }

        val result = queryResponse.data?.getEmailAddress?.emailAddress ?: return null
        return EmailAddressTransformer.graphQLToSealedEntity(result)
    }

    override suspend fun list(input: ListEmailAddressesRequest): ListEmailAddressesOutput {
        try {
            val queryInput =
                ListEmailAddressesInput(
                    limit = Optional.presentIfNotNull(input.limit),
                    nextToken = Optional.presentIfNotNull(input.nextToken),
                )
            val queryResponse =
                apiClient.listEmailAddressesQuery(
                    queryInput,
                )

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailAddressError(queryResponse.errors.first())
            }

            val queryResult = queryResponse.data?.listEmailAddresses
            val sealedEmailAddresses = queryResult?.items ?: emptyList()
            val newNextToken = queryResult?.nextToken

            return ListEmailAddressesOutput(
                items =
                    sealedEmailAddresses.map {
                        EmailAddressTransformer.graphQLToSealedEntity(it.emailAddress)
                    },
                nextToken = newNextToken,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAddressException(e)
            }
        }
    }

    override suspend fun listForSudoId(input: ListEmailAddressesForSudoIdRequest): ListEmailAddressesOutput {
        try {
            val queryInput =
                ListEmailAddressesForSudoIdInput(
                    sudoId = input.sudoId,
                    limit = Optional.presentIfNotNull(input.limit),
                    nextToken = Optional.presentIfNotNull(input.nextToken),
                )
            val queryResponse =
                apiClient.listEmailAddressesForSudoIdQuery(
                    queryInput,
                )

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailAddressError(queryResponse.errors.first())
            }

            val queryResult = queryResponse.data?.listEmailAddressesForSudoId
            val sealedEmailAddresses = queryResult?.items ?: emptyList()
            val newNextToken = queryResult?.nextToken

            return ListEmailAddressesOutput(
                items =
                    sealedEmailAddresses.map {
                        EmailAddressTransformer.graphQLToSealedEntity(it.emailAddress)
                    },
                nextToken = newNextToken,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAddressException(e)
            }
        }
    }

    override suspend fun lookupPublicInfo(input: LookupEmailAddressesPublicInfoRequest): List<EmailAddressPublicInfoEntity> {
        try {
            val queryInput =
                LookupEmailAddressesPublicInfoInput(
                    emailAddresses = input.emailAddresses,
                )
            val queryResponse =
                apiClient.lookupEmailAddressesPublicInfoQuery(
                    queryInput,
                )

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailAddressError(queryResponse.errors.first())
            }

            val queryResult = queryResponse.data?.lookupEmailAddressesPublicInfo
            val emailAddressesPublicInfo = queryResult?.items ?: emptyList()

            return emailAddressesPublicInfo.map { publicInfo ->
                EmailAddressPublicInfoTransformer.graphQLToEntity(
                    publicInfo.emailAddressPublicInfo,
                )
            }
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailAddressException(e)
            }
        }
    }
}
