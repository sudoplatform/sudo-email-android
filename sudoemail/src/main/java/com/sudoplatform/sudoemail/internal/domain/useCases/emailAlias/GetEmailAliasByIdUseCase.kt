/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.mechanisms.EmailAliasUnsealer
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.emailAlias.transformers.EmailAliasTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.GetEmailAliasByIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.UnsealedEmailAliasEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudologging.Logger

/**
 * Input for the get email alias by id use case.
 *
 * @property id [String] The ID of the email alias to retrieve.
 */
internal data class GetEmailAliasByIdUseCaseInput(
    val id: String,
)

/**
 * Use case for retrieving a single email alias by its unique identifier.
 *
 * This use case fetches the sealed email alias from the service and unseals it.
 *
 * @property emailAliasService [EmailAliasService] Service for email alias operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property logger [Logger] Logger for debugging.
 */
internal class GetEmailAliasByIdUseCase(
    private val emailAliasService: EmailAliasService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the get email alias by id use case.
     *
     * @param input [GetEmailAliasByIdUseCaseInput] The input parameters.
     * @return [UnsealedEmailAliasEntity] The retrieved email alias.
     * @throws SudoEmailClient.EmailAliasException.EmailAliasNotFoundException if the alias is not found.
     * @throws SudoEmailClient.EmailAliasException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: GetEmailAliasByIdUseCaseInput): UnsealedEmailAliasEntity {
        logger.debug("GetEmailAliasByIdUseCase execute input: $input")
        try {
            val getEmailAliasByIdRequest =
                GetEmailAliasByIdRequest(
                    id = input.id,
                )

            val sealedEmailAlias =
                emailAliasService.getById(getEmailAliasByIdRequest)
                    ?: throw SudoEmailClient.EmailAliasException.EmailAliasNotFoundException(
                        StringConstants.EMAIL_ALIAS_NOT_FOUND_MSG,
                    )

            val emailAliasUnsealer =
                EmailAliasUnsealer(
                    this.serviceKeyManager,
                )
            return try {
                emailAliasUnsealer.unseal(sealedEmailAlias)
            } catch (e: Exception) {
                logger.warning("GetEmailAliasByIdUseCase failed to unseal email alias ${sealedEmailAlias.id}: $e")
                EmailAliasTransformer.toUnsealedEntity(sealedEmailAlias)
            }
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
