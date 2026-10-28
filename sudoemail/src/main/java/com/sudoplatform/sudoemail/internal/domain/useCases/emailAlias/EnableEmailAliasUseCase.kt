/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.mechanisms.EmailAliasUnsealer
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.emailAlias.transformers.EmailAliasTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EnableEmailAliasRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.SealedEmailAliasEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.UnsealedEmailAliasEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudologging.Logger

/**
 * Input for the enable email alias use case.
 *
 * @property emailAliasId [String] The ID of the email alias to enable.
 */
internal data class EnableEmailAliasUseCaseInput(
    val emailAliasId: String,
)

/**
 * Use case for enabling an email alias.
 *
 * This use case handles enabling a disabled email alias to allow it to receive emails again.
 *
 * @property emailAliasService [EmailAliasService] Service for email alias operations.
 * @property logger [Logger] Logger for debugging.
 */
internal class EnableEmailAliasUseCase(
    private val emailAliasService: EmailAliasService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the enable email alias use case.
     *
     * @param input [EnableEmailAliasUseCaseInput] The input parameters.
     * @return [SealedEmailAliasEntity] The enabled email alias.
     * @throws SudoEmailClient.EmailAliasException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: EnableEmailAliasUseCaseInput): UnsealedEmailAliasEntity {
        logger.debug("EnableEmailAliasUseCase execute input: $input")
        try {
            val enableEmailAliasRequest =
                EnableEmailAliasRequest(
                    emailAliasId = input.emailAliasId,
                )

            val enabledEmailAlias = emailAliasService.enable(enableEmailAliasRequest)
            val emailAliasUnsealer =
                EmailAliasUnsealer(
                    this.serviceKeyManager,
                )
            try {
                val unsealed = emailAliasUnsealer.unseal(enabledEmailAlias)
                return unsealed
            } catch (e: Exception) {
                logger.warning("EnableEmailAliasUseCase failed to unseal email alias ${enabledEmailAlias.id}: $e")
                val partialEmailAlias =
                    EmailAliasTransformer
                        .toUnsealedEntity(enabledEmailAlias)
                return partialEmailAlias
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
