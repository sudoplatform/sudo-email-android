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
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.DisableEmailAliasRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.SealedEmailAliasEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.UnsealedEmailAliasEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudologging.Logger

/**
 * Input for the disable email alias use case.
 *
 * @property emailAliasId [String] The ID of the email alias to disable.
 */
internal data class DisableEmailAliasUseCaseInput(
    val emailAliasId: String,
)

/**
 * Use case for disabling an email alias.
 *
 * This use case handles disabling an email alias to prevent it from receiving new emails.
 *
 * @property emailAliasService [EmailAliasService] Service for email alias operations.
 * @property logger [Logger] Logger for debugging.
 */
internal class DisableEmailAliasUseCase(
    private val emailAliasService: EmailAliasService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the disable email alias use case.
     *
     * @param input [DisableEmailAliasUseCaseInput] The input parameters.
     * @return [SealedEmailAliasEntity] The disabled email alias.
     * @throws SudoEmailClient.EmailAliasException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: DisableEmailAliasUseCaseInput): UnsealedEmailAliasEntity {
        logger.debug("DisableEmailAliasUseCase execute input: $input")
        try {
            val disableEmailAliasRequest =
                DisableEmailAliasRequest(
                    emailAliasId = input.emailAliasId,
                )

            val disabledEmailAlias = emailAliasService.disable(disableEmailAliasRequest)
            val emailAliasUnsealer =
                EmailAliasUnsealer(
                    this.serviceKeyManager,
                )
            try {
                val unsealed = emailAliasUnsealer.unseal(disabledEmailAlias)
                return unsealed
            } catch (e: Exception) {
                logger.warning("DisableEmailAliasUseCase failed to unseal email alias ${disabledEmailAlias.id}: $e")
                val partialEmailAlias =
                    EmailAliasTransformer
                        .toUnsealedEntity(disabledEmailAlias)
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
