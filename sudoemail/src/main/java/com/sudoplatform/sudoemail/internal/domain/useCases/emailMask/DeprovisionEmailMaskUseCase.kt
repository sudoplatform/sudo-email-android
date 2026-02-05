/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMask

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.emailMask.transformers.EmailMaskTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.DeprovisionEmailMaskRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.PartialEmailMaskEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.SealedEmailMaskEntity
import com.sudoplatform.sudologging.Logger

/**
 * Input for the deprovision email mask use case.
 *
 * @property emailMaskId [String] The ID of the email mask to deprovision.
 */
internal data class DeprovisionEmailMaskUseCaseInput(
    val emailMaskId: String,
)

/**
 * Use case for deprovisioning an email mask.
 *
 * This use case handles removing an existing email mask.
 *
 * @property emailMaskService [EmailMaskService] Service for email mask operations.
 * @property logger [Logger] Logger for debugging.
 */
internal class DeprovisionEmailMaskUseCase(
    private val emailMaskService: EmailMaskService,
    private val logger: Logger,
) {
    /**
     * Executes the deprovision email mask use case.
     *
     * @param input [DeprovisionEmailMaskUseCaseInput] The input parameters.
     * @return [SealedEmailMaskEntity] The deprovisioned email mask.
     * @throws SudoEmailClient.EmailMaskException.AuthenticationException if authentication fails.
     */
    suspend fun execute(input: DeprovisionEmailMaskUseCaseInput): PartialEmailMaskEntity {
        logger.debug("DeprovisionEmailMaskUseCase execute input: $input")
        try {
            val deprovisionEmailMaskRequest =
                DeprovisionEmailMaskRequest(
                    emailMaskId = input.emailMaskId,
                )

            val deprovisionedEmailMask = emailMaskService.deprovision(deprovisionEmailMaskRequest)

            val emailMask = EmailMaskTransformer.toPartialEntity(deprovisionedEmailMask)
            return emailMask
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
