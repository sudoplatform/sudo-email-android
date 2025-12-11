/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.common.mechanisms.Unsealer
import com.sudoplatform.sudoemail.internal.data.emailMessage.transformers.EmailMessageTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListAPIResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListPartialResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListSuccessResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.PartialResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.ListEmailMessagesUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.DateRange
import com.sudoplatform.sudoemail.types.EmailMessageDateRange
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.SortOrder
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailAddressIdInput
import io.kotlintest.fail
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.listEmailMessagesForEmailAddressId] using mocks
 * and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListEmailMessagesForEmailAddressIdTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId
    private val unsealedEmailMessage1 =
        EntityDataFactory.getEmailMessageEntity(
            id = "messageId1",
            emailAddressId = emailAddressId,
        )
    private val unsealedEmailMessage2 =
        EntityDataFactory.getEmailMessageEntity(
            id = "messageId2",
            emailAddressId = emailAddressId,
        )
    private val partialEmailMessage =
        EntityDataFactory.getPartialEmailMessageEntity(
            id = "messageId3",
            emailAddressId = emailAddressId,
        )
    private val resultNextToken = "resultNextToken"

    private val input by before {
        ListEmailMessagesForEmailAddressIdInput(
            emailAddressId = emailAddressId,
        )
    }

    private val listSuccessResult by before {
        ListAPIResultEntity.Success(
            ListSuccessResultEntity(
                items = listOf(unsealedEmailMessage1, unsealedEmailMessage2),
                nextToken = null,
            ),
        )
    }

    private val listSuccessResultWithNextToken by before {
        ListAPIResultEntity.Success(
            ListSuccessResultEntity(
                items = listOf(unsealedEmailMessage1, unsealedEmailMessage2),
                nextToken = resultNextToken,
            ),
        )
    }

    private val listSuccessResultWithEmptyList by before {
        ListAPIResultEntity.Success(
            ListSuccessResultEntity(
                items = emptyList<EmailMessageEntity>(),
                nextToken = null,
            ),
        )
    }

    private val listPartialResult by before {
        ListAPIResultEntity.Partial(
            ListPartialResultEntity(
                items = listOf(unsealedEmailMessage1, unsealedEmailMessage2),
                failed =
                    listOf(
                        PartialResultEntity(
                            partial = partialEmailMessage,
                            cause = Unsealer.UnsealerException.UnsupportedAlgorithmException(),
                        ),
                    ),
                nextToken = null,
            ),
        )
    }

    private val mockUseCase by before {
        mock<ListEmailMessagesUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn listSuccessResult
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createListEmailMessagesUseCase() } doReturn mockUseCase
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
    fun `listEmailMessagesForEmailAddressId() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailMessagesForEmailAddressId(input)
                }
            deferredResult.start()
            val listEmailMessages = deferredResult.await()

            listEmailMessages shouldNotBe null
            when (listEmailMessages) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.isEmpty() shouldBe false
                    listEmailMessages.result.items.size shouldBe 2
                    listEmailMessages.result.nextToken shouldBe null

                    listEmailMessages.result.items[0] shouldBe EmailMessageTransformer.entityToApi(unsealedEmailMessage1)
                    listEmailMessages.result.items[1] shouldBe EmailMessageTransformer.entityToApi(unsealedEmailMessage2)
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe emailAddressId
                    useCaseInput.emailFolderId shouldBe null
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe SudoEmailClient.DEFAULT_EMAIL_MESSAGE_LIMIT
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return results when populating nextToken`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doAnswer {
                    listSuccessResultWithNextToken
                }
            }
            val nextToken = "nextToken"
            val input =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddressId,
                    nextToken = nextToken,
                    dateRange =
                        EmailMessageDateRange(
                            sortDate = DateRange(Date(), Date()),
                        ),
                    sortOrder = SortOrder.DESC,
                )
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailMessagesForEmailAddressId(input)
                }
            deferredResult.start()
            val listEmailMessages = deferredResult.await()

            listEmailMessages shouldNotBe null
            when (listEmailMessages) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.size shouldBe 2
                    listEmailMessages.result.nextToken shouldBe resultNextToken
                    listEmailMessages.result.items[0] shouldBe EmailMessageTransformer.entityToApi(unsealedEmailMessage1)
                    listEmailMessages.result.items[1] shouldBe EmailMessageTransformer.entityToApi(unsealedEmailMessage2)
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe emailAddressId
                    useCaseInput.emailFolderId shouldBe null
                    useCaseInput.nextToken shouldBe nextToken
                    useCaseInput.limit shouldBe SudoEmailClient.DEFAULT_EMAIL_MESSAGE_LIMIT
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return empty list output when use case result is empty`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doAnswer {
                    listSuccessResultWithEmptyList
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailMessagesForEmailAddressId(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.isEmpty() shouldBe true
                    result.result.items.size shouldBe 0
                    result.result.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe emailAddressId
                    useCaseInput.emailFolderId shouldBe null
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe SudoEmailClient.DEFAULT_EMAIL_MESSAGE_LIMIT
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return partial result when use case returns partial`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doAnswer {
                    listPartialResult
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailMessagesForEmailAddressId(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null

            when (result) {
                is ListAPIResult.Partial -> {
                    result.result.items.size shouldBe 2
                    result.result.items[0] shouldBe EmailMessageTransformer.entityToApi(unsealedEmailMessage1)
                    result.result.items[1] shouldBe EmailMessageTransformer.entityToApi(unsealedEmailMessage2)
                    result.result.failed.size shouldBe 1
                    result.result.failed[0].partial shouldBe EmailMessageTransformer.entityToPartialApi(partialEmailMessage)
                    result.result.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe emailAddressId
                    useCaseInput.emailFolderId shouldBe null
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe SudoEmailClient.DEFAULT_EMAIL_MESSAGE_LIMIT
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should throw when use case error occurs`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doThrow
                    SudoEmailClient.EmailMessageException.FailedException("Mock Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.listEmailMessagesForEmailAddressId(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createListEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe emailAddressId
                    useCaseInput.emailFolderId shouldBe null
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe SudoEmailClient.DEFAULT_EMAIL_MESSAGE_LIMIT
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should pass limit parameter when specified`() =
        runTest {
            val customLimit = 25
            val input =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddressId,
                    limit = customLimit,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailMessagesForEmailAddressId(input)
                }
            deferredResult.start()
            val listEmailMessages = deferredResult.await()

            listEmailMessages shouldNotBe null
            when (listEmailMessages) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.size shouldBe 2
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe emailAddressId
                    useCaseInput.emailFolderId shouldBe null
                    useCaseInput.limit shouldBe customLimit
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.sortOrder.name shouldBe SortOrder.DESC.name
                    useCaseInput.includeDeletedMessages shouldBe false
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should pass dateRange parameter when specified`() =
        runTest {
            val startDate = Date(1000)
            val endDate = Date(2000)
            val input =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddressId,
                    dateRange =
                        EmailMessageDateRange(
                            sortDate = DateRange(startDate, endDate),
                        ),
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailMessagesForEmailAddressId(input)
                }
            deferredResult.start()
            val listEmailMessages = deferredResult.await()

            listEmailMessages shouldNotBe null
            when (listEmailMessages) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.size shouldBe 2
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe emailAddressId
                    useCaseInput.emailFolderId shouldBe null
                    useCaseInput.dateRange shouldNotBe null
                    useCaseInput.dateRange?.sortDate?.startDate shouldBe startDate
                    useCaseInput.dateRange?.sortDate?.endDate shouldBe endDate
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should pass sortOrder parameter when specified`() =
        runTest {
            val input =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddressId,
                    sortOrder = SortOrder.ASC,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailMessagesForEmailAddressId(input)
                }
            deferredResult.start()
            val listEmailMessages = deferredResult.await()

            listEmailMessages shouldNotBe null
            when (listEmailMessages) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.size shouldBe 2
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe emailAddressId
                    useCaseInput.emailFolderId shouldBe null
                    useCaseInput.sortOrder.name shouldBe SortOrder.ASC.name
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should pass includeDeletedMessages parameter when specified`() =
        runTest {
            val input =
                ListEmailMessagesForEmailAddressIdInput(
                    emailAddressId = emailAddressId,
                    includeDeletedMessages = true,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailMessagesForEmailAddressId(input)
                }
            deferredResult.start()
            val listEmailMessages = deferredResult.await()

            listEmailMessages shouldNotBe null
            when (listEmailMessages) {
                is ListAPIResult.Success -> {
                    listEmailMessages.result.items.size shouldBe 2
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe emailAddressId
                    useCaseInput.emailFolderId shouldBe null
                    useCaseInput.includeDeletedMessages shouldBe true
                },
            )
        }
}
