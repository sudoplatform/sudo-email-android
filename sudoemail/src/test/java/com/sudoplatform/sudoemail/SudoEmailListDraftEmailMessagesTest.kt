/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.draftMessage.transformers.DraftEmailMessageTransformer
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.ListDraftEmailMessagesUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.listDraftEmailMessages]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListDraftEmailMessagesTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId
    private val emailAddressId2 = "emailAddressId2"
    private val draftId1 = "draftId1"
    private val draftId2 = "draftId2"
    private val draftId3 = "draftId3"
    private val unsealedDraftString = DataFactory.unsealedHeaderDetailsString

    private val draftMessage1 by before {
        EntityDataFactory.getDraftEmailMessageWithContentEntity(
            id = draftId1,
            emailAddressId = emailAddressId,
            rfc822Data = unsealedDraftString.toByteArray(),
        )
    }

    private val draftMessage2 by before {
        EntityDataFactory.getDraftEmailMessageWithContentEntity(
            id = draftId2,
            emailAddressId = emailAddressId,
            rfc822Data = unsealedDraftString.toByteArray(),
        )
    }

    private val draftMessage3 by before {
        EntityDataFactory.getDraftEmailMessageWithContentEntity(
            id = draftId3,
            emailAddressId = emailAddressId2,
            rfc822Data = unsealedDraftString.toByteArray(),
        )
    }

    private val allDraftMessages by before {
        listOf(draftMessage1, draftMessage2, draftMessage3)
    }

    private val mockListDraftEmailMessagesUseCase by before {
        mock<ListDraftEmailMessagesUseCase>().stub {
            onBlocking {
                execute()
            } doReturn allDraftMessages
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createListDraftEmailMessagesUseCase() } doReturn mockListDraftEmailMessagesUseCase
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
            mockListDraftEmailMessagesUseCase,
        )
    }

    @Test
    fun `listDraftEmailMessages() should return all draft messages when no errors present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessages()
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 3
            result[0].id shouldBe draftId1
            result[1].id shouldBe draftId2
            result[2].id shouldBe draftId3

            verify(mockUseCaseFactory).createListDraftEmailMessagesUseCase()
            verify(mockListDraftEmailMessagesUseCase).execute()
        }

    @Test
    fun `listDraftEmailMessages() should return empty list when no drafts exist`() =
        runTest {
            mockListDraftEmailMessagesUseCase.stub {
                onBlocking { execute() } doReturn emptyList()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessages()
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 0

            verify(mockUseCaseFactory).createListDraftEmailMessagesUseCase()
            verify(mockListDraftEmailMessagesUseCase).execute()
        }

    @Test
    fun `listDraftEmailMessages() should return single draft message`() =
        runTest {
            mockListDraftEmailMessagesUseCase.stub {
                onBlocking { execute() } doReturn listOf(draftMessage1)
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessages()
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 1
            result[0].id shouldBe draftId1
            result[0].emailAddressId shouldBe emailAddressId

            verify(mockUseCaseFactory).createListDraftEmailMessagesUseCase()
            verify(mockListDraftEmailMessagesUseCase).execute()
        }

    @Test
    fun `listDraftEmailMessages() should handle large number of drafts`() =
        runTest {
            val largeDraftList =
                (1..50).map { index ->
                    EntityDataFactory.getDraftEmailMessageWithContentEntity(
                        id = "draft-$index",
                        emailAddressId = emailAddressId,
                        rfc822Data = unsealedDraftString.toByteArray(),
                    )
                }

            mockListDraftEmailMessagesUseCase.stub {
                onBlocking { execute() } doReturn largeDraftList
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessages()
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 50

            verify(mockUseCaseFactory).createListDraftEmailMessagesUseCase()
            verify(mockListDraftEmailMessagesUseCase).execute()
        }

    @Test
    fun `listDraftEmailMessages() should throw when use case throws`() =
        runTest {
            mockListDraftEmailMessagesUseCase.stub {
                onBlocking { execute() }.thenAnswer {
                    throw SudoEmailClient.EmailMessageException.FailedException("Mock error")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.listDraftEmailMessages()
                    }
                }

            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createListDraftEmailMessagesUseCase()
            verify(mockListDraftEmailMessagesUseCase).execute()
        }

    @Test
    fun `listDraftEmailMessages() should properly transform entities to API types`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessages()
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 3
            result shouldBe allDraftMessages.map { DraftEmailMessageTransformer.entityWithContentToApi(it) }

            verify(mockUseCaseFactory).createListDraftEmailMessagesUseCase()
            verify(mockListDraftEmailMessagesUseCase).execute()
        }
}
