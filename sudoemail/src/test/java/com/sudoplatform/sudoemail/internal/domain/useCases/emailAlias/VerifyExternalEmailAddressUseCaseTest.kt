/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.VerifyExternalEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.VerifyExternalEmailAddressResultEntity
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
    private val emailAliasId = "alias-id-123"
    private val verificationCode = "123456"

    private val mockEmailAliasService by before {
        mock<EmailAliasService>()
    }

    private val useCase by before {
        VerifyExternalEmailAddressUseCase(
            emailAliasService = mockEmailAliasService,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockEmailAliasService,
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
            mockEmailAliasService.stub {
                onBlocking { verifyExternalEmailAddress(any()) } doReturn resultEntity
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = emailAddress,
                    emailAliasId = emailAliasId,
                    verificationCode = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.isVerified shouldBe false
            result.reason shouldBe "Verification email sent"

            verify(mockEmailAliasService).verifyExternalEmailAddress(
                VerifyExternalEmailAddressRequest(
                    emailAddress = emailAddress,
                    emailAliasId = emailAliasId,
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
            mockEmailAliasService.stub {
                onBlocking { verifyExternalEmailAddress(any()) } doReturn resultEntity
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = emailAddress,
                    emailAliasId = emailAliasId,
                    verificationCode = verificationCode,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.isVerified shouldBe true
            result.reason shouldBe null

            verify(mockEmailAliasService).verifyExternalEmailAddress(
                VerifyExternalEmailAddressRequest(
                    emailAddress = emailAddress,
                    emailAliasId = emailAliasId,
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
            mockEmailAliasService.stub {
                onBlocking { verifyExternalEmailAddress(any()) } doReturn resultEntity
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = emailAddress,
                    emailAliasId = emailAliasId,
                    verificationCode = "wrong-code",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.isVerified shouldBe false
            result.reason shouldBe "Invalid verification code"

            verify(mockEmailAliasService).verifyExternalEmailAddress(any())
        }

    @Test
    fun `execute() should throw when service throws`() =
        runTest {
            mockEmailAliasService.stub {
                onBlocking { verifyExternalEmailAddress(any()) } doThrow RuntimeException("Service failed")
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = emailAddress,
                    emailAliasId = emailAliasId,
                    verificationCode = verificationCode,
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockEmailAliasService).verifyExternalEmailAddress(any())
        }

    @Test
    fun `execute() should pass correct parameters to service`() =
        runTest {
            val customEmail = "custom@test.com"
            val customAliasId = "custom-alias-id"
            val customCode = "999999"

            val resultEntity =
                VerifyExternalEmailAddressResultEntity(
                    isVerified = true,
                    reason = null,
                )
            mockEmailAliasService.stub {
                onBlocking { verifyExternalEmailAddress(any()) } doReturn resultEntity
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = customEmail,
                    emailAliasId = customAliasId,
                    verificationCode = customCode,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockEmailAliasService).verifyExternalEmailAddress(
                VerifyExternalEmailAddressRequest(
                    emailAddress = customEmail,
                    emailAliasId = customAliasId,
                    verificationCode = customCode,
                ),
            )
        }
}
