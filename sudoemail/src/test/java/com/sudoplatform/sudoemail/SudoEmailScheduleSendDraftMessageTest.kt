/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageStateEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.ScheduleSendDraftMessageUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.types.inputs.ScheduleSendDraftMessageInput
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
import java.time.Duration
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.scheduleSendDraftMessage]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailScheduleSendDraftMessageTest : BaseTests() {
    private val dummyDraftId = "dummyId"
    private val dummyEmailAddressId = "dummyEmailAddressId"
    private val sendAt = Date(Date().time + Duration.ofDays(1).toMillis())
    private val input by before {
        ScheduleSendDraftMessageInput(
            dummyDraftId,
            dummyEmailAddressId,
            sendAt,
        )
    }

    private val mockScheduledDraftMessageEntity by before {
        EntityDataFactory.getScheduledDraftMessageEntity(
            id = dummyDraftId,
            emailAddressId = dummyEmailAddressId,
            sendAt = sendAt,
            state = ScheduledDraftMessageStateEntity.SCHEDULED,
        )
    }

    private val mockUseCase by before {
        mock<ScheduleSendDraftMessageUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn mockScheduledDraftMessageEntity
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createScheduleSendDraftMessageUseCase() } doReturn mockUseCase
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
            apiClient = mockApiClient,
            sudoUserClient = mockUserClient,
            logger = mockLogger,
            serviceKeyManager = mockServiceKeyManager,
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
    fun `scheduleSendDraftMessage should return new ScheduledDraftMessage entity on success`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.scheduleSendDraftMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result.id shouldBe dummyDraftId
            result.emailAddressId shouldBe dummyEmailAddressId
            result.sendAt shouldBe sendAt
            result.state shouldBe ScheduledDraftMessageState.SCHEDULED

            verify(mockUseCaseFactory).createScheduleSendDraftMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.id shouldBe dummyDraftId
                    useCaseInput.emailAddressId shouldBe dummyEmailAddressId
                    useCaseInput.sendAt shouldBe sendAt
                },
            )
        }

    @Test
    fun `scheduleSendDraftMessage() should throw when use case throws`() =
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
                        client.scheduleSendDraftMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createScheduleSendDraftMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.id shouldBe dummyDraftId
                    useCaseInput.emailAddressId shouldBe dummyEmailAddressId
                    useCaseInput.sendAt shouldBe sendAt
                },
            )
        }
}
