/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageRfc822DataEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.GetEmailMessageRfc822DataUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageRfc822DataInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.getEmailMessageRfc822Data]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetEmailMessageRfc822DataTest : BaseTests() {
    private val emailMessageRfc822DataEntity =
        EmailMessageRfc822DataEntity(
            id = mockEmailMessageId,
            rfc822Data = DataFactory.unsealedHeaderDetailsString.toByteArray(),
        )
    private val input by before {
        GetEmailMessageRfc822DataInput(
            id = mockEmailMessageId,
            emailAddressId = mockEmailAddressId,
        )
    }

    private val mockUseCase by before {
        mock<GetEmailMessageRfc822DataUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn emailMessageRfc822DataEntity
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createGetEmailMessageRfc822DataUseCase() } doReturn mockUseCase
        }
    }
    private val mockServiceKeyManager by before {
        DefaultServiceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
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
            mockUseCaseFactory,
            mockUseCase,
        )
    }

    @Test
    fun `getEmailMessageRfc822Data() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailMessageRfc822Data(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result?.id shouldBe mockEmailMessageId
            result?.rfc822Data shouldBe DataFactory.unsealedHeaderDetailsString.toByteArray()

            verify(mockUseCaseFactory).createGetEmailMessageRfc822DataUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.id shouldBe mockEmailMessageId
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `getEmailMessageRfc822Data() should return null result when use case response is null`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doAnswer {
                    null
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailMessageRfc822Data(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe null

            verify(mockUseCaseFactory).createGetEmailMessageRfc822DataUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.id shouldBe mockEmailMessageId
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `getEmailMessageRfc822Data() should throw when error occurs`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doThrow SudoEmailClient.EmailMessageException.FailedException("Mock Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.getEmailMessageRfc822Data(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createGetEmailMessageRfc822DataUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.id shouldBe mockEmailMessageId
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }
}
