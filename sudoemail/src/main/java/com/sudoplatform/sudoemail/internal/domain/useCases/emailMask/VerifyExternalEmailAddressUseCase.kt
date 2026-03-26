/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMask

import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.VerifyExternalEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.VerifyExternalEmailAddressResultEntity
import com.sudoplatform.sudoemail.types.VerifyExternalEmailAddressResult
import com.sudoplatform.sudoemail.types.inputs.VerifyExternalEmailAddressInput

/**
 * Use case for verifying an external email address for an email mask.
 *
 * @property emailMaskService [EmailMaskService] Service for managing email masks.
 */
internal class VerifyExternalEmailAddressUseCase(
    private val emailMaskService: EmailMaskService,
) {
    /**
     * Execute the use case to verify an external email address.
     *
     * @param input [VerifyExternalEmailAddressInput] Input parameters for verification.
     * @return [VerifyExternalEmailAddressResult] Result indicating verification status.
     */
    suspend fun execute(input: VerifyExternalEmailAddressInput): VerifyExternalEmailAddressResult {
        val request =
            VerifyExternalEmailAddressRequest(
                emailAddress = input.emailAddress,
                emailMaskId = input.emailMaskId,
                verificationCode = input.verificationCode,
            )

        val resultEntity = emailMaskService.verifyExternalEmailAddress(request)

        return VerifyExternalEmailAddressResult(
            isVerified = resultEntity.isVerified,
            reason = resultEntity.reason,
        )
    }
}
