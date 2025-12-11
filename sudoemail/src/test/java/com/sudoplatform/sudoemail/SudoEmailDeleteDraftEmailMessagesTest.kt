/*
* Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
*
* SPDX-License-Identifier: Apache-2.0
*/

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.EmailMessageOperationFailureResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.DeleteEmailMessageSuccessResultEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.DeleteDraftEmailMessagesUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.inputs.DeleteDraftEmailMessagesInput
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.deleteDraftEmailMessages]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailDeleteDraftEmailMessagesTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId
    private val draftId1 = "draftId1"
    private val draftId2 = "draftId2"
    private val draftId3 = "draftId3"
    private val draftIds = listOf(draftId1, draftId2)

    private val successResult by before {
        BatchOperationResultEntity<DeleteEmailMessageSuccessResultEntity, EmailMessageOperationFailureResultEntity>(
            status = BatchOperationStatusEntity.SUCCESS,
            successValues = draftIds.map { DeleteEmailMessageSuccessResultEntity(it) },
            failureValues = emptyList(),
        )
    }

    private val partialResult by before {
        BatchOperationResultEntity<DeleteEmailMessageSuccessResultEntity, EmailMessageOperationFailureResultEntity>(
            status = BatchOperationStatusEntity.PARTIAL,
            successValues = listOf(DeleteEmailMessageSuccessResultEntity(draftId1)),
            failureValues = listOf(EmailMessageOperationFailureResultEntity(draftId2, "Failed to delete draft")),
        )
    }

    private val failureResult by before {
        BatchOperationResultEntity<DeleteEmailMessageSuccessResultEntity, EmailMessageOperationFailureResultEntity>(
            status = BatchOperationStatusEntity.FAILURE,
            successValues = emptyList(),
            failureValues = draftIds.map { EmailMessageOperationFailureResultEntity(it, "Failed to delete draft") },
        )
    }

    private val mockUseCase by before {
        mock<DeleteDraftEmailMessagesUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn successResult
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createDeleteDraftEmailMessagesUseCase() } doReturn mockUseCase
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
    fun `deleteDraftEmailMessages() should return success result when no errors present`() =
        runTest {
            val input =
                DeleteDraftEmailMessagesInput(
                    ids = draftIds,
                    emailAddressId = emailAddressId,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteDraftEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS
            result.successValues!!.size shouldBe 2
            result.failureValues shouldBe emptyList()

            verify(mockUseCaseFactory).createDeleteDraftEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.ids shouldBe draftIds
                    useCaseInput.emailAddressId shouldBe emailAddressId
                },
            )
            verify(mockUseCaseFactory, times(draftIds.size)).createCancelScheduledDraftMessageUseCase()
        }

    @Test
    fun `deleteDraftEmailMessages() should return partial result when some deletions fail`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute(any()) } doReturn partialResult
            }

            val input =
                DeleteDraftEmailMessagesInput(
                    ids = draftIds,
                    emailAddressId = emailAddressId,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteDraftEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.PARTIAL
            result.successValues!!.size shouldBe 1
            result.successValues!![0].id shouldBe draftId1
            result.failureValues!!.size shouldBe 1
            result.failureValues!![0].id shouldBe draftId2

            verify(mockUseCaseFactory).createDeleteDraftEmailMessagesUseCase()
            verify(mockUseCase).execute(any())
            verify(mockUseCaseFactory, times(draftIds.size - 1)).createCancelScheduledDraftMessageUseCase()
        }

    @Test
    fun `deleteDraftEmailMessages() should return failure result when all deletions fail`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute(any()) } doReturn failureResult
            }

            val input =
                DeleteDraftEmailMessagesInput(
                    ids = draftIds,
                    emailAddressId = emailAddressId,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteDraftEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.FAILURE
            result.successValues shouldBe emptyList()
            result.failureValues!!.size shouldBe 2

            verify(mockUseCaseFactory).createDeleteDraftEmailMessagesUseCase()
            verify(mockUseCase).execute(any())
        }

    @Test
    fun `deleteDraftEmailMessages() should handle empty list`() =
        runTest {
            val emptyResult =
                BatchOperationResultEntity<DeleteEmailMessageSuccessResultEntity, EmailMessageOperationFailureResultEntity>(
                    status = BatchOperationStatusEntity.SUCCESS,
                    successValues = emptyList(),
                    failureValues = emptyList(),
                )

            mockUseCase.stub {
                onBlocking { execute(any()) } doReturn emptyResult
            }

            val input =
                DeleteDraftEmailMessagesInput(
                    ids = emptyList(),
                    emailAddressId = emailAddressId,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteDraftEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS
            result.successValues shouldBe emptyList()
            result.failureValues shouldBe emptyList()

            verify(mockUseCaseFactory).createDeleteDraftEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.ids shouldBe emptyList()
                },
            )
        }

    @Test
    fun `deleteDraftEmailMessages() should handle single draft message`() =
        runTest {
            val singleResult =
                BatchOperationResultEntity<DeleteEmailMessageSuccessResultEntity, EmailMessageOperationFailureResultEntity>(
                    status = BatchOperationStatusEntity.SUCCESS,
                    successValues = listOf(DeleteEmailMessageSuccessResultEntity(draftId1)),
                    failureValues = emptyList(),
                )

            mockUseCase.stub {
                onBlocking { execute(any()) } doReturn singleResult
            }

            val input =
                DeleteDraftEmailMessagesInput(
                    ids = listOf(draftId1),
                    emailAddressId = emailAddressId,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteDraftEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS
            result.successValues!!.size shouldBe 1
            result.successValues!![0].id shouldBe draftId1

            verify(mockUseCaseFactory).createDeleteDraftEmailMessagesUseCase()
            verify(mockUseCase).execute(any())
            verify(mockUseCaseFactory, times(1)).createCancelScheduledDraftMessageUseCase()
        }

    @Test
    fun `deleteDraftEmailMessages() should handle multiple draft messages`() =
        runTest {
            val multipleIds = listOf(draftId1, draftId2, draftId3)
            val multipleResult =
                BatchOperationResultEntity<DeleteEmailMessageSuccessResultEntity, EmailMessageOperationFailureResultEntity>(
                    status = BatchOperationStatusEntity.SUCCESS,
                    successValues = multipleIds.map { DeleteEmailMessageSuccessResultEntity(it) },
                    failureValues = emptyList(),
                )

            mockUseCase.stub {
                onBlocking { execute(any()) } doReturn multipleResult
            }

            val input =
                DeleteDraftEmailMessagesInput(
                    ids = multipleIds,
                    emailAddressId = emailAddressId,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteDraftEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS
            result.successValues!!.size shouldBe 3

            verify(mockUseCaseFactory).createDeleteDraftEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.ids.size shouldBe 3
                },
            )
            verify(mockUseCaseFactory, times(multipleIds.size)).createCancelScheduledDraftMessageUseCase()
        }

    @Test
    fun `deleteDraftEmailMessages() should handle different email address IDs`() =
        runTest {
            val customEmailAddressId = "customEmailAddressId"

            val input =
                DeleteDraftEmailMessagesInput(
                    ids = draftIds,
                    emailAddressId = customEmailAddressId,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteDraftEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null

            verify(mockUseCaseFactory).createDeleteDraftEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe customEmailAddressId
                },
            )
            verify(mockUseCaseFactory, times(draftIds.size)).createCancelScheduledDraftMessageUseCase()
        }

    @Test
    fun `deleteDraftEmailMessages() should throw when use case throws`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute(any()) }.thenAnswer {
                    throw SudoEmailClient.EmailMessageException.FailedException("Mock error")
                }
            }

            val input =
                DeleteDraftEmailMessagesInput(
                    ids = draftIds,
                    emailAddressId = emailAddressId,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.deleteDraftEmailMessages(input)
                    }
                }

            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createDeleteDraftEmailMessagesUseCase()
            verify(mockUseCase).execute(any())
        }

    @Test
    fun `deleteDraftEmailMessages() should pass correct parameters to use case`() =
        runTest {
            val input =
                DeleteDraftEmailMessagesInput(
                    ids = draftIds,
                    emailAddressId = emailAddressId,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteDraftEmailMessages(input)
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createDeleteDraftEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.ids shouldBe draftIds
                    useCaseInput.emailAddressId shouldBe emailAddressId
                },
            )
            verify(mockUseCaseFactory, times(draftIds.size)).createCancelScheduledDraftMessageUseCase()
        }
}
