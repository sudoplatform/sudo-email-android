/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMask

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.mechanisms.EmailMaskUnsealer
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.emailMask.transformers.EmailMaskTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EnableEmailMaskRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.SealedEmailMaskEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.UnsealedEmailMaskEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudologging.Logger

/**
 * Input for the enable email mask use case.
 *
 * @property emailMaskId [String] The ID of the email mask to enable.
 */
internal data class EnableEmailMaskUseCaseInput(
    val emailMaskId: String,
)

/**
 * Use case for enabling an email mask.
 *
 * This use case handles enabling a disabled email mask to allow it to receive emails again.
 *
 * @property emailMaskService [EmailMaskService] Service for email mask operations.
 * @property logger [Logger] Logger for debugging.
 */
internal class EnableEmailMaskUseCase(
    private val emailMaskService: EmailMaskService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the enable email mask use case.
     *
     * @param input [EnableEmailMaskUseCaseInput] The input parameters.
     * @return [SealedEmailMaskEntity] The enabled email mask.
     * @throws SudoEmailClient.EmailMaskException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: EnableEmailMaskUseCaseInput): UnsealedEmailMaskEntity {
        logger.debug("EnableEmailMaskUseCase execute input: $input")
        try {
            val enableEmailMaskRequest =
                EnableEmailMaskRequest(
                    emailMaskId = input.emailMaskId,
                )

            val enabledEmailMask = emailMaskService.enable(enableEmailMaskRequest)
            val emailMaskUnsealer =
                EmailMaskUnsealer(
                    this.serviceKeyManager,
                )
            try {
                val unsealed = emailMaskUnsealer.unseal(enabledEmailMask)
                return unsealed
            } catch (e: Exception) {
                logger.warning("EnableEmailMaskUseCase failed to unseal email mask ${enabledEmailMask.id}: $e")
                val partialEmailMask =
                    EmailMaskTransformer
                        .toUnsealedEntity(enabledEmailMask)
                return partialEmailMask
            }
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
