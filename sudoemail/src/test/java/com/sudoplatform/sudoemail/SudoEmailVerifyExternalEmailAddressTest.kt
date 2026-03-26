/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.VerifyExternalEmailAddressResultEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMask.VerifyExternalEmailAddressUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.VerifyExternalEmailAddressInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Test the correct operation of [SudoEmailClient.verifyExternalEmailAddress]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailVerifyExternalEmailAddressTest : BaseTests() {
    private val emailAddress = "external@example.com"
    private val emailMaskId = "mask-id-123"
    private val verificationCode = "123456"

    private val mockEmailMaskService by before {
        mock<EmailMaskService>()
    }

    private val mockServiceKeyManager by before {
        DefaultServiceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createVerifyExternalEmailAddressUseCase() } doReturn
                VerifyExternalEmailAddressUseCase(
                    mockEmailMaskService,
                )
        }
    }

    private val client by before {
        DefaultSudoEmailClient(
            context = mockContext,
            serviceKeyManager = mockServiceKeyManager,
            apiClient = mockApiClient,
            sudoUserClient = mockUserClient,
            logger = mockLogger,
            region = "region",
            emailBucket = "identityBucket",
            transientBucket = "transientBucket",
            notificationHandler = null,
            s3TransientClient = mockS3Client,
            s3EmailClient = mockS3Client,
            useCaseFactory = mockUseCaseFactory,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockContext,
            mockUserClient,
            mockKeyManager,
            mockApiClient,
            mockS3Client,
            mockEmailMaskService,
            mockUseCaseFactory,
        )
    }

    @Test
    fun `verifyExternalEmailAddress() should trigger sending verification email when code is null`() =
        runTest {
            mockEmailMaskService.stub {
                onBlocking { verifyExternalEmailAddress(any()) } doReturn
                    VerifyExternalEmailAddressResultEntity(
                        isVerified = false,
                        reason = "Verification email sent",
                    )
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = emailAddress,
                    emailMaskId = emailMaskId,
                    verificationCode = null,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.verifyExternalEmailAddress(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isVerified shouldBe false
            result.reason shouldBe "Verification email sent"

            verify(mockUseCaseFactory).createVerifyExternalEmailAddressUseCase()
            verify(mockEmailMaskService).verifyExternalEmailAddress(any())
        }

    @Test
    fun `verifyExternalEmailAddress() should verify successfully with valid code`() =
        runTest {
            mockEmailMaskService.stub {
                onBlocking { verifyExternalEmailAddress(any()) } doReturn
                    VerifyExternalEmailAddressResultEntity(
                        isVerified = true,
                        reason = null,
                    )
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = emailAddress,
                    emailMaskId = emailMaskId,
                    verificationCode = verificationCode,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.verifyExternalEmailAddress(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isVerified shouldBe true
            result.reason shouldBe null

            verify(mockUseCaseFactory).createVerifyExternalEmailAddressUseCase()
            verify(mockEmailMaskService).verifyExternalEmailAddress(any())
        }

    @Test
    fun `verifyExternalEmailAddress() should return failure with invalid code`() =
        runTest {
            mockEmailMaskService.stub {
                onBlocking { verifyExternalEmailAddress(any()) } doReturn
                    VerifyExternalEmailAddressResultEntity(
                        isVerified = false,
                        reason = "Invalid verification code",
                    )
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = emailAddress,
                    emailMaskId = emailMaskId,
                    verificationCode = "wrong-code",
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.verifyExternalEmailAddress(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isVerified shouldBe false
            result.reason shouldBe "Invalid verification code"

            verify(mockUseCaseFactory).createVerifyExternalEmailAddressUseCase()
            verify(mockEmailMaskService).verifyExternalEmailAddress(any())
        }

    @Test
    fun `verifyExternalEmailAddress() should throw when service throws`() =
        runTest {
            mockEmailMaskService.stub {
                onBlocking {
                    verifyExternalEmailAddress(any())
                } doThrow SudoEmailClient.EmailMaskException.FailedException("Service failed")
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = emailAddress,
                    emailMaskId = emailMaskId,
                    verificationCode = verificationCode,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMaskException.FailedException> {
                        client.verifyExternalEmailAddress(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createVerifyExternalEmailAddressUseCase()
            verify(mockEmailMaskService).verifyExternalEmailAddress(any())
        }

    @Test
    fun `verifyExternalEmailAddress() should throw EmailMaskNotFoundException when mask not found`() =
        runTest {
            mockEmailMaskService.stub {
                onBlocking {
                    verifyExternalEmailAddress(any())
                } doThrow SudoEmailClient.EmailMaskException.EmailMaskNotFoundException("Mask not found")
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = emailAddress,
                    emailMaskId = "nonExistentMaskId",
                    verificationCode = verificationCode,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMaskException.EmailMaskNotFoundException> {
                        client.verifyExternalEmailAddress(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createVerifyExternalEmailAddressUseCase()
            verify(mockEmailMaskService).verifyExternalEmailAddress(any())
        }

    @Test
    fun `verifyExternalEmailAddress() should pass correct parameters`() =
        runTest {
            val customEmail = "custom@test.com"
            val customMaskId = "custom-mask-id"
            val customCode = "999999"

            mockEmailMaskService.stub {
                onBlocking { verifyExternalEmailAddress(any()) } doReturn
                    VerifyExternalEmailAddressResultEntity(
                        isVerified = true,
                        reason = null,
                    )
            }

            val input =
                VerifyExternalEmailAddressInput(
                    emailAddress = customEmail,
                    emailMaskId = customMaskId,
                    verificationCode = customCode,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.verifyExternalEmailAddress(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isVerified shouldBe true

            verify(mockUseCaseFactory).createVerifyExternalEmailAddressUseCase()
            verify(mockEmailMaskService).verifyExternalEmailAddress(any())
        }
}
