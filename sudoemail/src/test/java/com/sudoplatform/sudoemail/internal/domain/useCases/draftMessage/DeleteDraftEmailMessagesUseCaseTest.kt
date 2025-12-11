/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.EmailMessageOperationFailureResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.DeleteEmailMessageSuccessResultEntity
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
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
 * Test the correct operation of [DeleteDraftEmailMessagesUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class DeleteDraftEmailMessagesUseCaseTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId
    private val draftId1 = "draftId1"
    private val draftId2 = "draftId2"
    private val draftId3 = "draftId3"

    private val mockEmailAddress by before {
        EntityDataFactory.getSealedEmailAddressEntity(
            id = emailAddressId,
        )
    }

    private val mockS3Key1 = DefaultS3Client.constructS3KeyForDraftEmailMessage(emailAddressId, draftId1)
    private val mockS3Key2 = DefaultS3Client.constructS3KeyForDraftEmailMessage(emailAddressId, draftId2)
    private val mockS3Key3 = DefaultS3Client.constructS3KeyForDraftEmailMessage(emailAddressId, draftId3)

    private val successResult by before {
        BatchOperationResultEntity<DeleteEmailMessageSuccessResultEntity, EmailMessageOperationFailureResultEntity>(
            status = BatchOperationStatusEntity.SUCCESS,
            successValues =
                listOf(
                    DeleteEmailMessageSuccessResultEntity(mockS3Key1),
                    DeleteEmailMessageSuccessResultEntity(mockS3Key2),
                    DeleteEmailMessageSuccessResultEntity(mockS3Key3),
                ),
            failureValues = emptyList(),
        )
    }

    private val partialResult by before {
        BatchOperationResultEntity(
            status = BatchOperationStatusEntity.PARTIAL,
            successValues =
                listOf(
                    DeleteEmailMessageSuccessResultEntity(mockS3Key1),
                    DeleteEmailMessageSuccessResultEntity(mockS3Key3),
                ),
            failureValues =
                listOf(
                    EmailMessageOperationFailureResultEntity(
                        id = mockS3Key2,
                        errorType = "DeleteFailed",
                    ),
                ),
        )
    }

    private val failureResult by before {
        BatchOperationResultEntity<DeleteEmailMessageSuccessResultEntity, EmailMessageOperationFailureResultEntity>(
            status = BatchOperationStatusEntity.FAILURE,
            successValues = emptyList(),
            failureValues =
                listOf(
                    EmailMessageOperationFailureResultEntity(
                        id = mockS3Key1,
                        errorType = "DeleteFailed",
                    ),
                    EmailMessageOperationFailureResultEntity(
                        id = mockS3Key2,
                        errorType = "DeleteFailed",
                    ),
                ),
        )
    }

    private val mockDraftEmailMessageService by before {
        mock<DraftEmailMessageService>().stub {
            onBlocking { delete(any()) } doReturn successResult
        }
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { get(any()) } doReturn mockEmailAddress
        }
    }

    private val useCase by before {
        DeleteDraftEmailMessagesUseCase(
            draftEmailMessageService = mockDraftEmailMessageService,
            emailAddressService = mockEmailAddressService,
            logger = mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockDraftEmailMessageService,
            mockEmailAddressService,
        )
    }

    @Test
    fun `execute() should return success result when all deletes succeed`() =
        runTest {
            val input =
                DeleteDraftEmailMessagesUseCaseInput(
                    ids = listOf(draftId1, draftId2, draftId3),
                    emailAddressId = emailAddressId,
                )

            val result = useCase.execute(input)

            result shouldBe successResult
            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues?.size shouldBe 3
            result.failureValues?.size shouldBe 0

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe emailAddressId
                },
            )
            verify(mockDraftEmailMessageService).delete(
                check { request ->
                    request.s3Keys.size shouldBe 3
                    request.s3Keys shouldBe setOf(mockS3Key1, mockS3Key2, mockS3Key3)
                },
            )
        }

    @Test
    fun `execute() should return partial result when some deletes fail`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn partialResult
            }

            val input =
                DeleteDraftEmailMessagesUseCaseInput(
                    ids = listOf(draftId1, draftId2, draftId3),
                    emailAddressId = emailAddressId,
                )

            val result = useCase.execute(input)

            result shouldBe partialResult
            result.status shouldBe BatchOperationStatusEntity.PARTIAL
            result.successValues?.size shouldBe 2
            result.failureValues?.size shouldBe 1

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).delete(any())
        }

    @Test
    fun `execute() should return failure result when all deletes fail`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn failureResult
            }

            val input =
                DeleteDraftEmailMessagesUseCaseInput(
                    ids = listOf(draftId1, draftId2),
                    emailAddressId = emailAddressId,
                )

            val result = useCase.execute(input)

            result shouldBe failureResult
            result.status shouldBe BatchOperationStatusEntity.FAILURE
            result.successValues?.size shouldBe 0
            result.failureValues?.size shouldBe 2

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).delete(any())
        }

    @Test
    fun `execute() should throw EmailAddressNotFoundException when email address does not exist`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { get(any()) } doReturn null
            }

            val input =
                DeleteDraftEmailMessagesUseCaseInput(
                    ids = listOf(draftId1),
                    emailAddressId = emailAddressId,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `execute() should throw InvalidArgumentException when ids list is empty`() =
        runTest {
            val input =
                DeleteDraftEmailMessagesUseCaseInput(
                    ids = emptyList(),
                    emailAddressId = emailAddressId,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.INVALID_ARGUMENT_ERROR_MSG

            verify(mockEmailAddressService).get(any())
        }

    @Test
    fun `execute() should handle single draft ID successfully`() =
        runTest {
            val singleSuccessResult =
                BatchOperationResultEntity<DeleteEmailMessageSuccessResultEntity, EmailMessageOperationFailureResultEntity>(
                    status = BatchOperationStatusEntity.SUCCESS,
                    successValues = listOf(DeleteEmailMessageSuccessResultEntity(mockS3Key1)),
                    failureValues = emptyList(),
                )

            mockDraftEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn singleSuccessResult
            }

            val input =
                DeleteDraftEmailMessagesUseCaseInput(
                    ids = listOf(draftId1),
                    emailAddressId = emailAddressId,
                )

            val result = useCase.execute(input)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues?.size shouldBe 1
            result.failureValues?.size shouldBe 0

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).delete(
                check { request ->
                    request.s3Keys.size shouldBe 1
                    request.s3Keys shouldBe setOf(mockS3Key1)
                },
            )
        }

    @Test
    fun `execute() should construct correct S3 keys from draft IDs`() =
        runTest {
            val input =
                DeleteDraftEmailMessagesUseCaseInput(
                    ids = listOf(draftId1, draftId2),
                    emailAddressId = emailAddressId,
                )

            useCase.execute(input)

            verify(mockDraftEmailMessageService).delete(
                check { request ->
                    request.s3Keys shouldBe setOf(mockS3Key1, mockS3Key2)
                },
            )
            verify(mockEmailAddressService).get(any())
        }

    @Test
    fun `execute() should handle duplicate draft IDs by deduplicating in set`() =
        runTest {
            val input =
                DeleteDraftEmailMessagesUseCaseInput(
                    ids = listOf(draftId1, draftId1, draftId2),
                    emailAddressId = emailAddressId,
                )

            useCase.execute(input)

            verify(mockDraftEmailMessageService).delete(
                check { request ->
                    // Set should deduplicate the duplicate draftId1
                    request.s3Keys.size shouldBe 2
                    request.s3Keys shouldBe setOf(mockS3Key1, mockS3Key2)
                },
            )
            verify(mockEmailAddressService).get(any())
        }
}
