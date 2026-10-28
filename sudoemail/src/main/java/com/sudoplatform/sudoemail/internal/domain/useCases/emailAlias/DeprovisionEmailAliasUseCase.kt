/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.emailAlias.transformers.EmailAliasTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.DeprovisionEmailAliasRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.PartialEmailAliasEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.SealedEmailAliasEntity
import com.sudoplatform.sudologging.Logger

/**
 * Input for the deprovision email alias use case.
 *
 * @property emailAliasId [String] The ID of the email alias to deprovision.
 */
internal data class DeprovisionEmailAliasUseCaseInput(
    val emailAliasId: String,
)

/**
 * Use case for deprovisioning an email alias.
 *
 * This use case handles removing an existing email alias.
 *
 * @property emailAliasService [EmailAliasService] Service for email alias operations.
 * @property logger [Logger] Logger for debugging.
 */
internal class DeprovisionEmailAliasUseCase(
    private val emailAliasService: EmailAliasService,
    private val logger: Logger,
) {
    /**
     * Executes the deprovision email alias use case.
     *
     * @param input [DeprovisionEmailAliasUseCaseInput] The input parameters.
     * @return [SealedEmailAliasEntity] The deprovisioned email alias.
     * @throws SudoEmailClient.EmailAliasException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: DeprovisionEmailAliasUseCaseInput): PartialEmailAliasEntity {
        logger.debug("DeprovisionEmailAliasUseCase execute input: $input")
        try {
            val deprovisionEmailAliasRequest =
                DeprovisionEmailAliasRequest(
                    emailAliasId = input.emailAliasId,
                )

            val deprovisionedEmailAlias = emailAliasService.deprovision(deprovisionEmailAliasRequest)

            val emailAlias = EmailAliasTransformer.toPartialEntity(deprovisionedEmailAlias)
            return emailAlias
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
