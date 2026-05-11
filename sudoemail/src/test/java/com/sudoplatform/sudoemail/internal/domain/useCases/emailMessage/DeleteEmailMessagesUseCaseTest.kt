/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.DeleteEmailMessagesResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.EmailMessageBodyCache
import com.sudoplatform.sudoemail.types.BatchOperationStatus
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
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [DeleteEmailMessagesUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class DeleteEmailMessagesUseCaseTest : BaseTests() {
    private val configurationData by before {
        EntityDataFactory.getConfigurationDataEntity(
            deleteEmailMessagesLimit = 10,
        )
    }

    private val successResult by before {
        DeleteEmailMessagesResultEntity(
            successIds = mutableListOf("message-id-1", "message-id-2", "message-id-3"),
            failureIds = emptyList(),
        )
    }

    private val mockEmailMessageService by before {
        mock<EmailMessageService>().stub {
            onBlocking { delete(any()) } doReturn successResult
        }
    }

    private val mockConfigurationDataService by before {
        mock<ConfigurationDataService>().stub {
            onBlocking { getConfigurationData() } doReturn configurationData
        }
    }

    private val mockEmailMessageBodyCache by before {
        mock<EmailMessageBodyCache>()
    }

    private val useCase by before {
        DeleteEmailMessagesUseCase(
            mockEmailMessageService,
            mockConfigurationDataService,
            mockLogger,
            mockEmailMessageBodyCache,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockEmailMessageService,
            mockConfigurationDataService,
            mockEmailMessageBodyCache,
        )
    }

    /** Begin Batch Delete Tests */

    @Test
    fun `execute() should return success result when all messages deleted`() =
        runTest {
            val ids = setOf("message-id-1", "message-id-2", "message-id-3")

            val result = useCase.execute(ids)

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS
            result.successValues?.size shouldBe 3
            result.failureValues?.size shouldBe 0

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).delete(ids)
            verify(mockEmailMessageBodyCache).deleteMessage("message-id-1")
            verify(mockEmailMessageBodyCache).deleteMessage("message-id-2")
            verify(mockEmailMessageBodyCache).deleteMessage("message-id-3")
        }

    @Test
    fun `execute() should handle partial success result`() =
        runTest {
            val partialResult =
                DeleteEmailMessagesResultEntity(
                    successIds = mutableListOf("success-1", "success-2"),
                    failureIds = listOf("failed-1", "failed-2"),
                )
            mockEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn partialResult
            }

            val ids = setOf("success-1", "success-2", "failed-1", "failed-2")

            val result = useCase.execute(ids)

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.PARTIAL
            result.successValues?.size shouldBe 2
            result.failureValues?.size shouldBe 2
            result.successValues?.get(0)?.id shouldBe "success-1"
            result.successValues?.get(1)?.id shouldBe "success-2"

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).delete(ids)
            verify(mockEmailMessageBodyCache).deleteMessage("success-1")
            verify(mockEmailMessageBodyCache).deleteMessage("success-2")
        }

    @Test
    fun `execute() should handle all failed result`() =
        runTest {
            val failedResult =
                DeleteEmailMessagesResultEntity(
                    successIds = mutableListOf(),
                    failureIds = listOf("failed-1", "failed-2", "failed-3"),
                )
            mockEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn failedResult
            }

            val ids = setOf("failed-1", "failed-2", "failed-3")

            val result = useCase.execute(ids)

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.FAILURE
            result.successValues?.size shouldBe 0
            result.failureValues?.size shouldBe 3

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).delete(ids)
        }

    @Test
    fun `execute() should throw LimitExceededException when ids exceed limit`() =
        runTest {
            val limitedConfig =
                EntityDataFactory.getConfigurationDataEntity(
                    deleteEmailMessagesLimit = 5,
                )
            mockConfigurationDataService.stub {
                onBlocking { getConfigurationData() } doReturn limitedConfig
            }

            val tooManyIds = setOf("id-1", "id-2", "id-3", "id-4", "id-5", "id-6")

            shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
                useCase.execute(tooManyIds)
            }

            verify(mockConfigurationDataService).getConfigurationData()
        }

    @Test
    fun `execute() should throw InvalidArgumentException when ids is empty`() =
        runTest {
            val emptyIds = emptySet<String>()

            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                useCase.execute(emptyIds)
            }

            verify(mockConfigurationDataService).getConfigurationData()
        }

    @Test
    fun `execute() should handle maximum allowed ids`() =
        runTest {
            val maxIds = (1..10).map { "message-id-$it" }.toSet()
            val maxResult =
                DeleteEmailMessagesResultEntity(
                    successIds = maxIds.toMutableList(),
                    failureIds = emptyList(),
                )
            mockEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn maxResult
            }

            val result = useCase.execute(maxIds)

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS
            result.successValues?.size shouldBe 10

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).delete(maxIds)
            maxIds.forEach { id ->
                verify(mockEmailMessageBodyCache).deleteMessage(id)
            }
        }

    @Test
    fun `execute() should reject ids at limit plus one`() =
        runTest {
            val tooManyIds = (1..11).map { "message-id-$it" }.toSet()

            shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
                useCase.execute(tooManyIds)
            }

            verify(mockConfigurationDataService).getConfigurationData()
        }

    @Test
    fun `execute() should deduplicate ids using set`() =
        runTest {
            // Set will automatically deduplicate
            val ids = setOf("id-1", "id-2", "id-1", "id-3")
            val deduplicatedResult =
                DeleteEmailMessagesResultEntity(
                    successIds = mutableListOf("id-1", "id-2", "id-3"),
                    failureIds = emptyList(),
                )
            mockEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn deduplicatedResult
            }

            val result = useCase.execute(ids)

            result shouldNotBe null
            ids.size shouldBe 3 // Set deduplicates
            result.successValues?.size shouldBe 3

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).delete(ids)
            verify(mockEmailMessageBodyCache).deleteMessage("id-1")
            verify(mockEmailMessageBodyCache).deleteMessage("id-2")
            verify(mockEmailMessageBodyCache).deleteMessage("id-3")
        }

    @Test
    fun `execute() should throw when service throws exception`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking {
                    delete(any())
                } doThrow RuntimeException("Service error")
            }

            val ids = setOf("message-id-1")

            shouldThrow<RuntimeException> {
                useCase.execute(ids)
            }

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).delete(ids)
        }

    @Test
    fun `execute() should map success and failure results correctly`() =
        runTest {
            val mixedResult =
                DeleteEmailMessagesResultEntity(
                    successIds = mutableListOf("success-1"),
                    failureIds = listOf("failed-1"),
                )
            mockEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn mixedResult
            }

            val ids = setOf("success-1", "failed-1")

            val result = useCase.execute(ids)

            result shouldNotBe null
            result.successValues?.get(0)?.id shouldBe "success-1"
            result.failureValues?.get(0)?.id shouldBe "failed-1"
            result.failureValues?.get(0)?.errorType shouldBe "Failed to delete email message"

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).delete(ids)
            verify(mockEmailMessageBodyCache).deleteMessage("success-1")
        }

    /** Begin Single Delete Tests */

    @Test
    fun `execute(single id) should return success result when message deleted`() =
        runTest {
            val singleResult =
                DeleteEmailMessagesResultEntity(
                    successIds = mutableListOf("message-id-1"),
                    failureIds = emptyList(),
                )
            mockEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn singleResult
            }

            val result = useCase.execute("message-id-1")

            result shouldNotBe null
            result?.id shouldBe "message-id-1"

            verify(mockEmailMessageService).delete(setOf("message-id-1"))
            verify(mockEmailMessageBodyCache).deleteMessage("message-id-1")
        }

    @Test
    fun `execute(single id) should return null when message not deleted`() =
        runTest {
            val failedResult =
                DeleteEmailMessagesResultEntity(
                    successIds = mutableListOf(),
                    failureIds = listOf("message-id-1"),
                )
            mockEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn failedResult
            }

            val result = useCase.execute("message-id-1")

            result shouldBe null

            verify(mockEmailMessageService).delete(setOf("message-id-1"))
        }

    @Test
    fun `execute(single id) should pass single id as set to service`() =
        runTest {
            val singleResult =
                DeleteEmailMessagesResultEntity(
                    successIds = mutableListOf("test-id"),
                    failureIds = emptyList(),
                )
            mockEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn singleResult
            }

            val result = useCase.execute("test-id")

            result shouldNotBe null

            verify(mockEmailMessageService).delete(setOf("test-id"))
            verify(mockEmailMessageBodyCache).deleteMessage("test-id")
        }

    @Test
    fun `execute(single id) should handle service exception`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking {
                    delete(any())
                } doThrow RuntimeException("Service error")
            }

            shouldThrow<RuntimeException> {
                useCase.execute("message-id-1")
            }

            verify(mockEmailMessageService).delete(setOf("message-id-1"))
        }

    /** Begin Cache Integration Tests */

    @Test
    fun `execute() should call cache deleteMessage for each successfully deleted ID`() =
        runTest {
            val ids = setOf("message-id-1", "message-id-2", "message-id-3")

            useCase.execute(ids)

            verify(mockEmailMessageBodyCache).deleteMessage("message-id-1")
            verify(mockEmailMessageBodyCache).deleteMessage("message-id-2")
            verify(mockEmailMessageBodyCache).deleteMessage("message-id-3")

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).delete(ids)
        }

    @Test
    fun `execute() should not call cache deleteMessage for failed IDs`() =
        runTest {
            val partialResult =
                DeleteEmailMessagesResultEntity(
                    successIds = mutableListOf("success-1"),
                    failureIds = listOf("failed-1"),
                )
            mockEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn partialResult
            }

            val ids = setOf("success-1", "failed-1")

            useCase.execute(ids)

            verify(mockEmailMessageBodyCache).deleteMessage("success-1")
            verify(mockEmailMessageBodyCache, never()).deleteMessage("failed-1")

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).delete(ids)
        }

    @Test
    fun `execute() should not propagate cache deletion failure`() =
        runTest {
            mockEmailMessageBodyCache.stub {
                onBlocking { deleteMessage(any()) } doThrow RuntimeException("Cache error")
            }

            val ids = setOf("message-id-1", "message-id-2", "message-id-3")

            // Should not throw despite cache errors
            val result = useCase.execute(ids)

            result.status shouldBe BatchOperationStatus.SUCCESS
            result.successValues?.size shouldBe 3

            verify(mockEmailMessageBodyCache, times(3)).deleteMessage(any())
            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).delete(ids)
        }

    @Test
    fun `execute() should not call cache deleteMessage when all deletes fail`() =
        runTest {
            val failedResult =
                DeleteEmailMessagesResultEntity(
                    successIds = mutableListOf(),
                    failureIds = listOf("failed-1", "failed-2"),
                )
            mockEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn failedResult
            }

            val ids = setOf("failed-1", "failed-2")

            useCase.execute(ids)

            verify(mockEmailMessageBodyCache, never()).deleteMessage(any())

            verify(mockConfigurationDataService).getConfigurationData()
            verify(mockEmailMessageService).delete(ids)
        }

    @Test
    fun `execute(single id) should call cache deleteMessage on successful delete`() =
        runTest {
            val singleResult =
                DeleteEmailMessagesResultEntity(
                    successIds = mutableListOf("message-id-1"),
                    failureIds = emptyList(),
                )
            mockEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn singleResult
            }

            useCase.execute("message-id-1")

            verify(mockEmailMessageBodyCache).deleteMessage("message-id-1")
            verify(mockEmailMessageService).delete(setOf("message-id-1"))
        }

    @Test
    fun `execute(single id) should not call cache deleteMessage on failed delete`() =
        runTest {
            val failedResult =
                DeleteEmailMessagesResultEntity(
                    successIds = mutableListOf(),
                    failureIds = listOf("message-id-1"),
                )
            mockEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn failedResult
            }

            useCase.execute("message-id-1")

            verify(mockEmailMessageBodyCache, never()).deleteMessage(any())
            verify(mockEmailMessageService).delete(setOf("message-id-1"))
        }

    @Test
    fun `execute(single id) should not propagate cache deletion failure`() =
        runTest {
            val singleResult =
                DeleteEmailMessagesResultEntity(
                    successIds = mutableListOf("message-id-1"),
                    failureIds = emptyList(),
                )
            mockEmailMessageService.stub {
                onBlocking { delete(any()) } doReturn singleResult
            }
            mockEmailMessageBodyCache.stub {
                onBlocking { deleteMessage(any()) } doThrow RuntimeException("Cache error")
            }

            // Should not throw despite cache error
            val result = useCase.execute("message-id-1")

            result shouldNotBe null
            result?.id shouldBe "message-id-1"

            verify(mockEmailMessageBodyCache).deleteMessage("message-id-1")
            verify(mockEmailMessageService).delete(setOf("message-id-1"))
        }
}
