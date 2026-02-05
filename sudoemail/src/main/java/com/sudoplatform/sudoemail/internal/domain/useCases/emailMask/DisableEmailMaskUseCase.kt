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
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.DisableEmailMaskRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.SealedEmailMaskEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.UnsealedEmailMaskEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudologging.Logger

/**
 * Input for the disable email mask use case.
 *
 * @property emailMaskId [String] The ID of the email mask to disable.
 */
internal data class DisableEmailMaskUseCaseInput(
    val emailMaskId: String,
)

/**
 * Use case for disabling an email mask.
 *
 * This use case handles disabling an email mask to prevent it from receiving new emails.
 *
 * @property emailMaskService [EmailMaskService] Service for email mask operations.
 * @property logger [Logger] Logger for debugging.
 */
internal class DisableEmailMaskUseCase(
    private val emailMaskService: EmailMaskService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the disable email mask use case.
     *
     * @param input [DisableEmailMaskUseCaseInput] The input parameters.
     * @return [SealedEmailMaskEntity] The disabled email mask.
     * @throws SudoEmailClient.EmailMaskException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: DisableEmailMaskUseCaseInput): UnsealedEmailMaskEntity {
        logger.debug("DisableEmailMaskUseCase execute input: $input")
        try {
            val disableEmailMaskRequest =
                DisableEmailMaskRequest(
                    emailMaskId = input.emailMaskId,
                )

            val disabledEmailMask = emailMaskService.disable(disableEmailMaskRequest)
            val emailMaskUnsealer =
                EmailMaskUnsealer(
                    this.serviceKeyManager,
                )
            try {
                val unsealed = emailMaskUnsealer.unseal(disabledEmailMask)
                return unsealed
            } catch (e: Exception) {
                logger.warning("DisableEmailMaskUseCase failed to unseal email mask ${disabledEmailMask.id}: $e")
                val partialEmailMask =
                    EmailMaskTransformer
                        .toUnsealedEntity(disabledEmailMask)
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
