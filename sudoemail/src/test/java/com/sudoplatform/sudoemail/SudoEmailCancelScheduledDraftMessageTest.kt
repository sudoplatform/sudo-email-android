/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.CancelScheduledDraftMessageUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.CancelScheduledDraftMessageInput
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.cancelScheduledDraftMessage]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailCancelScheduledDraftMessageTest : BaseTests() {
    private val dummyDraftId = "dummyId"
    private val dummyEmailAddressId = "dummyEmailAddressId"
    private val input by before {
        CancelScheduledDraftMessageInput(
            dummyDraftId,
            dummyEmailAddressId,
        )
    }

    private val mockUseCase by before {
        mock<CancelScheduledDraftMessageUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn dummyDraftId
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createCancelScheduledDraftMessageUseCase() } doReturn mockUseCase
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
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockUseCaseFactory,
            mockUseCase,
        )
    }

    @Test
    fun `cancelScheduledDraftMessage() should return draft id on success`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.cancelScheduledDraftMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe dummyDraftId

            verify(mockUseCaseFactory).createCancelScheduledDraftMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.draftId shouldBe dummyDraftId
                    useCaseInput.emailAddressId shouldBe dummyEmailAddressId
                },
            )
        }

    @Test
    fun `cancelScheduledDraftMessage() should throw when use case throws`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                }.thenAnswer {
                    throw SudoEmailClient.EmailMessageException.EmailMessageNotFoundException("Mock")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                        client.cancelScheduledDraftMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createCancelScheduledDraftMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.draftId shouldBe dummyDraftId
                    useCaseInput.emailAddressId shouldBe dummyEmailAddressId
                },
            )
        }
}
