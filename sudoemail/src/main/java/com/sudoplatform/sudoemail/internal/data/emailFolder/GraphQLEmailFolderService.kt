/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailFolder

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.graphql.type.CreateCustomEmailFolderInput
import com.sudoplatform.sudoemail.graphql.type.CustomEmailFolderUpdateValuesInput
import com.sudoplatform.sudoemail.graphql.type.DeleteCustomEmailFolderInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailFoldersForEmailAddressIdInput
import com.sudoplatform.sudoemail.graphql.type.UpdateCustomEmailFolderInput
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.emailFolder.transformers.EmailFolderTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.CreateCustomEmailFolderRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.DeleteCustomEmailFolderRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.EmailFolderService
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.ListEmailFoldersForEmailAddressIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.ListEmailFoldersOutput
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.SealedEmailFolderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.UpdateCustomEmailFolderRequest
import com.sudoplatform.sudologging.Logger

/**
 * Implementation of [EmailFolderService] for managing email folders.
 *
 * This service handles listing, creating, deleting, and updating email folders
 * by interacting with the GraphQL API client.
 *
 * @property apiClient [ApiClient] The GraphQL API client for executing mutations and queries.
 * @property logger [Logger] Logger for logging debug and error messages.
 */
internal class GraphQLEmailFolderService(
    val apiClient: ApiClient,
    val logger: Logger,
) : EmailFolderService {
    override suspend fun listForEmailAddressId(input: ListEmailFoldersForEmailAddressIdRequest): ListEmailFoldersOutput {
        try {
            val queryInput =
                ListEmailFoldersForEmailAddressIdInput(
                    emailAddressId = input.emailAddressId,
                    limit = Optional.presentIfNotNull(input.limit),
                    nextToken = Optional.presentIfNotNull(input.nextToken),
                )
            val queryResponse =
                apiClient.listEmailFoldersForEmailAddressIdQuery(
                    queryInput,
                )

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors}")
                throw ErrorTransformer.interpretEmailFolderError(queryResponse.errors.first())
            }

            val queryResult = queryResponse.data?.listEmailFoldersForEmailAddressId
            val sealedEmailFolders = queryResult?.items ?: emptyList()
            val nextToken = queryResult?.nextToken

            return ListEmailFoldersOutput(
                items = sealedEmailFolders.map { EmailFolderTransformer.graphQLToSealedEntity(it.emailFolder) },
                nextToken = nextToken,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailFolderException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailFolderException(e)
            }
        }
    }

    override suspend fun createCustom(input: CreateCustomEmailFolderRequest): SealedEmailFolderEntity {
        try {
            val mutationInput =
                CreateCustomEmailFolderInput(
                    emailAddressId = input.emailAddressId,
                    customFolderName = input.customFolderName,
                )
            val mutationResponse =
                apiClient.createCustomEmailFolderMutation(
                    mutationInput,
                )
            if (mutationResponse.hasErrors()) {
                throw ErrorTransformer.interpretEmailFolderError(mutationResponse.errors.first())
            }

            return EmailFolderTransformer.graphQLToSealedEntity(mutationResponse.data.createCustomEmailFolder.emailFolder)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailFolderException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailFolderException(e)
            }
        }
    }

    override suspend fun deleteCustom(input: DeleteCustomEmailFolderRequest): SealedEmailFolderEntity? {
        try {
            val mutationInput =
                DeleteCustomEmailFolderInput(
                    emailFolderId = input.emailFolderId,
                    emailAddressId = input.emailAddressId,
                )

            val mutationResponse =
                apiClient.deleteCustomEmailFolderMutation(
                    mutationInput,
                )
            if (mutationResponse.hasErrors()) {
                throw ErrorTransformer.interpretEmailFolderError(mutationResponse.errors.first())
            }
            val result = mutationResponse.data?.deleteCustomEmailFolder
            result?.let {
                return EmailFolderTransformer.graphQLToSealedEntity(result.emailFolder)
            }
            return null
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            print("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailFolderException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailFolderException(e)
            }
        }
    }

    override suspend fun updateCustom(input: UpdateCustomEmailFolderRequest): SealedEmailFolderEntity {
        try {
            val mutationInput =
                UpdateCustomEmailFolderInput(
                    emailAddressId = input.emailAddressId,
                    emailFolderId = input.emailFolderId,
                    values =
                        CustomEmailFolderUpdateValuesInput(
                            customFolderName = Optional.presentIfNotNull(input.customFolderName),
                        ),
                )
            val mutationResponse =
                apiClient.updateCustomEmailFolderMutation(
                    mutationInput,
                )
            if (mutationResponse.hasErrors()) {
                throw ErrorTransformer.interpretEmailFolderError(mutationResponse.errors.first())
            }

            return EmailFolderTransformer.graphQLToSealedEntity(mutationResponse.data.updateCustomEmailFolder.emailFolder)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailFolderException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailFolderException(e)
            }
        }
    }
}
