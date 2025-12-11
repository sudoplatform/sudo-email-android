/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.EmailMessageOperationFailureResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.UpdatedEmailMessageResultEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.UpdateEmailMessagesUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.EmailMessageOperationFailureResult
import com.sudoplatform.sudoemail.types.UpdatedEmailMessageResult.UpdatedEmailMessageSuccess
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailMessagesInput
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
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.updateEmailMessages]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailUpdateEmailMessagesTest : BaseTests() {
    private val messageId1 = "message-id-1"
    private val messageId2 = "message-id-2"
    private val input by before {
        UpdateEmailMessagesInput(
            listOf(messageId1, messageId2),
            UpdateEmailMessagesInput.UpdatableValues("folderId2", true),
        )
    }

    private val successResult by before {
        BatchOperationResultEntity<
            UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity,
            EmailMessageOperationFailureResultEntity,
        >(
            status = BatchOperationStatusEntity.SUCCESS,
            successValues =
                listOf(
                    UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity(
                        id = messageId1,
                        createdAt = Date(1000),
                        updatedAt = Date(2000),
                    ),
                    UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity(
                        id = messageId2,
                        createdAt = Date(1000),
                        updatedAt = Date(2000),
                    ),
                ),
            failureValues = emptyList(),
        )
    }

    private val partialResult by before {
        BatchOperationResultEntity(
            status = BatchOperationStatusEntity.PARTIAL,
            successValues =
                listOf(
                    UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity(
                        id = messageId1,
                        createdAt = Date(1000),
                        updatedAt = Date(2000),
                    ),
                ),
            failureValues =
                listOf(
                    EmailMessageOperationFailureResultEntity(
                        id = messageId2,
                        errorType = "MessageNotFound",
                    ),
                ),
        )
    }

    private val failedResult by before {
        BatchOperationResultEntity<
            UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity,
            EmailMessageOperationFailureResultEntity,
        >(
            status = BatchOperationStatusEntity.FAILURE,
            successValues = emptyList(),
            failureValues =
                listOf(
                    EmailMessageOperationFailureResultEntity(
                        id = messageId1,
                        errorType = "UnauthorizedAddress",
                    ),
                    EmailMessageOperationFailureResultEntity(
                        id = messageId2,
                        errorType = "UnauthorizedAddress",
                    ),
                ),
        )
    }

    private val mockUseCase by before {
        mock<UpdateEmailMessagesUseCase>().stub {
            onBlocking { execute(any()) } doReturn successResult
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createUpdateEmailMessagesUseCase() } doReturn mockUseCase
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
    fun `updateEmailMessages() should return success result when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.updateEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockUseCaseFactory).createUpdateEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.ids shouldBe listOf(messageId1, messageId2)
                    useCaseInput.values.folderId shouldBe input.values.folderId
                    useCaseInput.values.seen shouldBe input.values.seen
                },
            )
        }

    @Test
    fun `updateEmailMessages() should return failure result when no error present`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doAnswer {
                    failedResult
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.updateEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.FAILURE

            verify(mockUseCaseFactory).createUpdateEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.ids shouldBe listOf(messageId1, messageId2)
                    useCaseInput.values.folderId shouldBe input.values.folderId
                    useCaseInput.values.seen shouldBe input.values.seen
                },
            )
        }

    @Test
    fun `updateEmailMessages() should return partial result when no error present`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                }.thenAnswer {
                    partialResult
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.updateEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.PARTIAL
            result.successValues shouldBe
                listOf(
                    UpdatedEmailMessageSuccess(
                        messageId1,
                        Date(1000),
                        Date(2000),
                    ),
                )
            result.failureValues shouldBe
                listOf(
                    EmailMessageOperationFailureResult(
                        messageId2,
                        "MessageNotFound",
                    ),
                )

            verify(mockUseCaseFactory).createUpdateEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.ids shouldBe listOf(messageId1, messageId2)
                    useCaseInput.values.folderId shouldBe input.values.folderId
                    useCaseInput.values.seen shouldBe input.values.seen
                },
            )
        }

    @Test
    fun `updateEmailMessages() should throw when use case throws error`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                }.thenAnswer {
                    throw SudoEmailClient.EmailMessageException.FailedException("Mock")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.updateEmailMessages(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createUpdateEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.ids shouldBe listOf(messageId1, messageId2)
                    useCaseInput.values.folderId shouldBe input.values.folderId
                    useCaseInput.values.seen shouldBe input.values.seen
                },
            )
        }
}
