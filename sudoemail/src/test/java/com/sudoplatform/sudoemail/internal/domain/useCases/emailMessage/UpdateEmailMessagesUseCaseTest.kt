/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.EmailMessageOperationFailureResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.UpdatedEmailMessageResultEntity
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [UpdateEmailMessagesUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class UpdateEmailMessagesUseCaseTest : BaseTests() {
    private val configurationData by before {
        EntityDataFactory.getConfigurationDataEntity(
            updateEmailMessagesLimit = 10,
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
                        id = "message-id-1",
                        createdAt = Date(1000),
                        updatedAt = Date(2000),
                    ),
                    UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity(
                        id = "message-id-2",
                        createdAt = Date(1000),
                        updatedAt = Date(2000),
                    ),
                ),
            failureValues = emptyList(),
        )
    }

    private val mockEmailMessageService by before {
        mock<EmailMessageService>().stub {
            onBlocking { update(any()) } doReturn successResult
        }
    }

    private val mockConfigurationDataService by before {
        mock<ConfigurationDataService>().stub {
            onBlocking { getConfigurationData() } doReturn configurationData
        }
    }

    private val useCase by before {
        UpdateEmailMessagesUseCase(
            mockEmailMessageService,
            mockConfigurationDataService,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockEmailMessageService,
            mockConfigurationDataService,
        )
    }

    @Test
    fun `execute() should return success result when all messages updated`() =
        runTest {
            val input =
                UpdateEmailMessagesUseCaseInput(
                    ids = listOf("message-id-1", "message-id-2"),
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            folderId = "new-folder-id",
                            seen = true,
                        ),
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues?.size shouldBe 2
            result.failureValues?.size shouldBe 0

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).update(
                check { request ->
                    request.ids shouldBe listOf("message-id-1", "message-id-2")
                    request.values.folderId shouldBe "new-folder-id"
                    request.values.seen shouldBe true
                },
            )
        }

    @Test
    fun `execute() should handle partial success result`() =
        runTest {
            val partialResult =
                BatchOperationResultEntity(
                    status = BatchOperationStatusEntity.PARTIAL,
                    successValues =
                        listOf(
                            UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity(
                                id = "success-id",
                                createdAt = Date(1000),
                                updatedAt = Date(2000),
                            ),
                        ),
                    failureValues =
                        listOf(
                            EmailMessageOperationFailureResultEntity(
                                id = "failed-id",
                                errorType = "MessageNotFound",
                            ),
                        ),
                )
            mockEmailMessageService.stub {
                onBlocking { update(any()) } doReturn partialResult
            }

            val input =
                UpdateEmailMessagesUseCaseInput(
                    ids = listOf("success-id", "failed-id"),
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            seen = true,
                        ),
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.status shouldBe BatchOperationStatusEntity.PARTIAL
            result.successValues?.size shouldBe 1
            result.failureValues?.size shouldBe 1

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).update(any())
        }

    @Test
    fun `execute() should handle all failed result`() =
        runTest {
            val failedResult =
                BatchOperationResultEntity<
                    UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity,
                    EmailMessageOperationFailureResultEntity,
                >(
                    status = BatchOperationStatusEntity.FAILURE,
                    successValues = emptyList(),
                    failureValues =
                        listOf(
                            EmailMessageOperationFailureResultEntity(
                                id = "failed-id-1",
                                errorType = "UnauthorizedAddress",
                            ),
                            EmailMessageOperationFailureResultEntity(
                                id = "failed-id-2",
                                errorType = "UnauthorizedAddress",
                            ),
                        ),
                )
            mockEmailMessageService.stub {
                onBlocking { update(any()) } doReturn failedResult
            }

            val input =
                UpdateEmailMessagesUseCaseInput(
                    ids = listOf("failed-id-1", "failed-id-2"),
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            folderId = "folder-id",
                        ),
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.status shouldBe BatchOperationStatusEntity.FAILURE
            result.successValues?.size shouldBe 0
            result.failureValues?.size shouldBe 2

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).update(any())
        }

    @Test
    fun `execute() should throw LimitExceededException when ids exceed limit`() =
        runTest {
            val limitedConfig =
                EntityDataFactory.getConfigurationDataEntity(
                    updateEmailMessagesLimit = 3,
                )
            mockConfigurationDataService.stub {
                onBlocking { getConfigurationData() } doReturn limitedConfig
            }

            val input =
                UpdateEmailMessagesUseCaseInput(
                    ids = listOf("id-1", "id-2", "id-3", "id-4"),
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            seen = true,
                        ),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
                useCase.execute(input)
            }

            verify(mockConfigurationDataService).getConfigurationData()
        }

    @Test
    fun `execute() should throw InvalidArgumentException when ids list is empty`() =
        runTest {
            val input =
                UpdateEmailMessagesUseCaseInput(
                    ids = emptyList(),
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            seen = true,
                        ),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                useCase.execute(input)
            }

            verify(mockConfigurationDataService).getConfigurationData()
        }

    @Test
    fun `execute() should update only folderId when seen is null`() =
        runTest {
            val input =
                UpdateEmailMessagesUseCaseInput(
                    ids = listOf("message-id-1"),
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            folderId = "target-folder",
                            seen = null,
                        ),
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).update(
                check { request ->
                    request.values.folderId shouldBe "target-folder"
                    request.values.seen shouldBe null
                },
            )
        }

    @Test
    fun `execute() should update only seen when folderId is null`() =
        runTest {
            val input =
                UpdateEmailMessagesUseCaseInput(
                    ids = listOf("message-id-1", "message-id-2"),
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            folderId = null,
                            seen = false,
                        ),
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).update(
                check { request ->
                    request.values.folderId shouldBe null
                    request.values.seen shouldBe false
                },
            )
        }

    @Test
    fun `execute() should handle single message update`() =
        runTest {
            val singleResult =
                BatchOperationResultEntity<
                    UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity,
                    EmailMessageOperationFailureResultEntity,
                >(
                    status = BatchOperationStatusEntity.SUCCESS,
                    successValues =
                        listOf(
                            UpdatedEmailMessageResultEntity.UpdatedEmailMessageSuccessEntity(
                                id = "single-id",
                                createdAt = Date(1000),
                                updatedAt = Date(2000),
                            ),
                        ),
                    failureValues = emptyList(),
                )
            mockEmailMessageService.stub {
                onBlocking { update(any()) } doReturn singleResult
            }

            val input =
                UpdateEmailMessagesUseCaseInput(
                    ids = listOf("single-id"),
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            seen = true,
                        ),
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.successValues?.size shouldBe 1
            result.successValues?.get(0)?.id shouldBe "single-id"

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).update(
                check { request ->
                    request.ids.size shouldBe 1
                    request.ids[0] shouldBe "single-id"
                },
            )
        }

    @Test
    fun `execute() should deduplicate ids using set`() =
        runTest {
            val input =
                UpdateEmailMessagesUseCaseInput(
                    ids = listOf("id-1", "id-2", "id-1", "id-3", "id-2"),
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            seen = true,
                        ),
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).update(
                check { request ->
                    // Original list is passed, but validation uses set
                    request.ids shouldBe listOf("id-1", "id-2", "id-1", "id-3", "id-2")
                },
            )
        }

    @Test
    fun `execute() should handle updating both folderId and seen`() =
        runTest {
            val input =
                UpdateEmailMessagesUseCaseInput(
                    ids = listOf("msg-1", "msg-2", "msg-3"),
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            folderId = "archive-folder",
                            seen = true,
                        ),
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).update(
                check { request ->
                    request.ids.size shouldBe 3
                    request.values.folderId shouldBe "archive-folder"
                    request.values.seen shouldBe true
                },
            )
        }

    @Test
    fun `execute() should handle marking messages as unread`() =
        runTest {
            val input =
                UpdateEmailMessagesUseCaseInput(
                    ids = listOf("unread-1", "unread-2"),
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            seen = false,
                        ),
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).update(
                check { request ->
                    request.values.seen shouldBe false
                },
            )
        }

    @Test
    fun `execute() should throw when service throws exception`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking {
                    update(any())
                } doThrow RuntimeException("Service error")
            }

            val input =
                UpdateEmailMessagesUseCaseInput(
                    ids = listOf("message-id"),
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            seen = true,
                        ),
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).update(any())
        }

    @Test
    fun `execute() should accept maximum allowed ids`() =
        runTest {
            val maxIds = (1..10).map { "id-$it" }
            val input =
                UpdateEmailMessagesUseCaseInput(
                    ids = maxIds,
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            seen = true,
                        ),
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).update(
                check { request ->
                    request.ids.size shouldBe 10
                },
            )
        }

    @Test
    fun `execute() should reject ids at limit plus one`() =
        runTest {
            val tooManyIds = (1..11).map { "id-$it" }
            val input =
                UpdateEmailMessagesUseCaseInput(
                    ids = tooManyIds,
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            seen = true,
                        ),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
                useCase.execute(input)
            }

            verify(mockConfigurationDataService).getConfigurationData()
        }
}
