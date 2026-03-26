/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMask

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.VerifyExternalEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.VerifyExternalEmailAddressResultEntity
import com.sudoplatform.sudoemail.types.inputs.VerifyExternalEmailAddressInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [VerifyExternalEmailAddressUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class VerifyExternalEmailAddressUseCaseTest : BaseTests() {
    private val emailAddress = "external@example.com"
    private val emailMaskId = "mask-id-123"
    private val verificationCode = "123456"

    private val mockEmailMaskService by before {
        mock<EmailMaskService>()
    }

    private val useCase by before {
        VerifyExternalEmailAddressUseCase(
            emailMaskService = mockEmailMaskService,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockEmailMaskService,
        )
    }

    @Test
    fun `execute() should trigger sending verification email when code is null`() =
        runTest {
            val resultEntity =
                VerifyExternalEmailAddressResultEntity(
                    isVerified = false,
                    reason = "Verification email sent",
                )
            mockEmailMaskService.stub {
                onBlocking { verifyExternalEmailAddress(any()) } doReturn resultEntity
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = emailAddress,
                    emailMaskId = emailMaskId,
                    verificationCode = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.isVerified shouldBe false
            result.reason shouldBe "Verification email sent"

            verify(mockEmailMaskService).verifyExternalEmailAddress(
                VerifyExternalEmailAddressRequest(
                    emailAddress = emailAddress,
                    emailMaskId = emailMaskId,
                    verificationCode = null,
                ),
            )
        }

    @Test
    fun `execute() should verify successfully with valid code`() =
        runTest {
            val resultEntity =
                VerifyExternalEmailAddressResultEntity(
                    isVerified = true,
                    reason = null,
                )
            mockEmailMaskService.stub {
                onBlocking { verifyExternalEmailAddress(any()) } doReturn resultEntity
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = emailAddress,
                    emailMaskId = emailMaskId,
                    verificationCode = verificationCode,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.isVerified shouldBe true
            result.reason shouldBe null

            verify(mockEmailMaskService).verifyExternalEmailAddress(
                VerifyExternalEmailAddressRequest(
                    emailAddress = emailAddress,
                    emailMaskId = emailMaskId,
                    verificationCode = verificationCode,
                ),
            )
        }

    @Test
    fun `execute() should return failure with invalid code`() =
        runTest {
            val resultEntity =
                VerifyExternalEmailAddressResultEntity(
                    isVerified = false,
                    reason = "Invalid verification code",
                )
            mockEmailMaskService.stub {
                onBlocking { verifyExternalEmailAddress(any()) } doReturn resultEntity
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = emailAddress,
                    emailMaskId = emailMaskId,
                    verificationCode = "wrong-code",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.isVerified shouldBe false
            result.reason shouldBe "Invalid verification code"

            verify(mockEmailMaskService).verifyExternalEmailAddress(any())
        }

    @Test
    fun `execute() should throw when service throws`() =
        runTest {
            mockEmailMaskService.stub {
                onBlocking { verifyExternalEmailAddress(any()) } doThrow RuntimeException("Service failed")
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = emailAddress,
                    emailMaskId = emailMaskId,
                    verificationCode = verificationCode,
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockEmailMaskService).verifyExternalEmailAddress(any())
        }

    @Test
    fun `execute() should pass correct parameters to service`() =
        runTest {
            val customEmail = "custom@test.com"
            val customMaskId = "custom-mask-id"
            val customCode = "999999"

            val resultEntity =
                VerifyExternalEmailAddressResultEntity(
                    isVerified = true,
                    reason = null,
                )
            mockEmailMaskService.stub {
                onBlocking { verifyExternalEmailAddress(any()) } doReturn resultEntity
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = customEmail,
                    emailMaskId = customMaskId,
                    verificationCode = customCode,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockEmailMaskService).verifyExternalEmailAddress(
                VerifyExternalEmailAddressRequest(
                    emailAddress = customEmail,
                    emailMaskId = customMaskId,
                    verificationCode = customCode,
                ),
            )
        }
}
