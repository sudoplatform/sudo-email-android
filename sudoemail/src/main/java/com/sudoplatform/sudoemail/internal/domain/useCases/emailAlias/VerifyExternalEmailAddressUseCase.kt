/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias

import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.VerifyExternalEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.VerifyExternalEmailAddressResultEntity
import com.sudoplatform.sudoemail.types.VerifyExternalEmailAddressResult
import com.sudoplatform.sudoemail.types.inputs.VerifyExternalEmailAddressInput

/**
 * Use case for verifying an external email address for an email alias.
 *
 * @property emailAliasService [EmailAliasService] Service for managing email aliases.
 */
internal class VerifyExternalEmailAddressUseCase(
    private val emailAliasService: EmailAliasService,
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
                emailAliasId = input.emailAliasId,
                verificationCode = input.verificationCode,
            )

        val resultEntity = emailAliasService.verifyExternalEmailAddress(request)

        return VerifyExternalEmailAddressResult(
            isVerified = resultEntity.isVerified,
            reason = resultEntity.reason,
        )
    }
}
