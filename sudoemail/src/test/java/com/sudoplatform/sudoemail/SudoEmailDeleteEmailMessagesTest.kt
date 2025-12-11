/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.DeleteEmailMessagesUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.BatchOperationResult
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.DeleteEmailMessageSuccessResult
import com.sudoplatform.sudoemail.types.EmailMessageOperationFailureResult
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.deleteEmailMessages]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailDeleteEmailMessagesTest : BaseTests() {
    private val mockDeleteEmailMessagesLimit = 10
    private val messageIds = listOf("id1", "id2")

    private val successResult by before {
        BatchOperationResult<DeleteEmailMessageSuccessResult, EmailMessageOperationFailureResult>(
            status = BatchOperationStatus.SUCCESS,
            successValues = messageIds.map { DeleteEmailMessageSuccessResult(it) },
            failureValues = emptyList(),
        )
    }

    private val partialResult by before {
        BatchOperationResult(
            status = BatchOperationStatus.PARTIAL,
            successValues = listOf(DeleteEmailMessageSuccessResult(messageIds[0])),
            failureValues = listOf(EmailMessageOperationFailureResult(messageIds[1], "Failed to delete email message")),
        )
    }

    private val failureResult by before {
        BatchOperationResult<DeleteEmailMessageSuccessResult, EmailMessageOperationFailureResult>(
            status = BatchOperationStatus.FAILURE,
            successValues = emptyList(),
            failureValues = messageIds.map { EmailMessageOperationFailureResult(it, "Failed to delete email message") },
        )
    }

    private val mockUseCase by before {
        mock<DeleteEmailMessagesUseCase>().stub {
            onBlocking {
                execute(any<Set<String>>())
            } doReturn successResult
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createDeleteEmailMessagesUseCase() } doReturn mockUseCase
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
            mockUseCase,
            mockUseCaseFactory,
        )
    }

    @Test
    fun `deleteEmailMessages() should return success result when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteEmailMessages(messageIds)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockUseCaseFactory).createDeleteEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check<Set<String>> { ids ->
                    ids shouldBe messageIds.toSet()
                },
            )
        }

    @Test
    fun `deleteEmailMessages() should return failure result when no error present`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any<Set<String>>())
                } doReturn failureResult
            }
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteEmailMessages(messageIds)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.FAILURE

            verify(mockUseCaseFactory).createDeleteEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check<Set<String>> { ids ->
                    ids shouldBe messageIds.toSet()
                },
            )
        }

    @Test
    fun `deleteEmailMessages() should return partial result when no error present`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any<Set<String>>())
                } doReturn partialResult
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteEmailMessages(messageIds)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.PARTIAL
            result.successValues shouldBe listOf(DeleteEmailMessageSuccessResult(messageIds[0]))
            result.failureValues shouldBe listOf(EmailMessageOperationFailureResult(messageIds[1], "Failed to delete email message"))

            verify(mockUseCaseFactory).createDeleteEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check<Set<String>> { ids ->
                    ids shouldBe messageIds.toSet()
                },
            )
        }

    @Test
    fun `deleteEmailMessages() should throw when use case throws error`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any<Set<String>>(),
                    )
                }.thenAnswer {
                    throw SudoEmailClient.EmailMessageException.FailedException("Mock")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.deleteEmailMessages(listOf("id1", "id2"))
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createDeleteEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check<Set<String>> { ids ->
                    ids shouldBe messageIds.toSet()
                },
            )
        }
}
