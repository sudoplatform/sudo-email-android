/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.draftMessage.transformers.DraftEmailMessageTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ListDraftEmailMessagesOutput
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.ListDraftEmailMessagesForEmailAddressIdUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.ListDraftEmailMessagesForEmailAddressIdInput
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.listDraftEmailMessagesForEmailAddressId]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListDraftEmailMessagesForEmailAddressIdTest : BaseTests() {
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

    private val emailAddressIdDrafts by before {
        listOf(draftMessage1, draftMessage2)
    }

    private val mockListDraftEmailMessagesForEmailAddressIdUseCase by before {
        mock<ListDraftEmailMessagesForEmailAddressIdUseCase>().stub {
            onBlocking {
                execute(any(), anyOrNull(), anyOrNull())
            } doReturn ListDraftEmailMessagesOutput(emailAddressIdDrafts, null)
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createListDraftEmailMessagesForEmailAddressIdUseCase() } doReturn mockListDraftEmailMessagesForEmailAddressIdUseCase
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
            mockListDraftEmailMessagesForEmailAddressIdUseCase,
        )
    }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should return drafts for specified email address`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessagesForEmailAddressId(ListDraftEmailMessagesForEmailAddressIdInput(emailAddressId))
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.items.size shouldBe 2
            result.items[0].id shouldBe draftId1
            result.items[0].emailAddressId shouldBe emailAddressId
            result.items[1].id shouldBe draftId2
            result.items[1].emailAddressId shouldBe emailAddressId

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(
                check { emailAddressIdArg ->
                    emailAddressIdArg shouldBe emailAddressId
                },
                anyOrNull(),
                anyOrNull(),
            )
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should return empty list when no drafts for address`() =
        runTest {
            mockListDraftEmailMessagesForEmailAddressIdUseCase.stub {
                onBlocking { execute(any(), anyOrNull(), anyOrNull()) } doReturn ListDraftEmailMessagesOutput(emptyList(), null)
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessagesForEmailAddressId(ListDraftEmailMessagesForEmailAddressIdInput(emailAddressId))
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.items.size shouldBe 0

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(emailAddressId, null, null)
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should return single draft for address`() =
        runTest {
            mockListDraftEmailMessagesForEmailAddressIdUseCase.stub {
                onBlocking { execute(any(), anyOrNull(), anyOrNull()) } doReturn ListDraftEmailMessagesOutput(listOf(draftMessage1), null)
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessagesForEmailAddressId(ListDraftEmailMessagesForEmailAddressIdInput(emailAddressId))
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.items.size shouldBe 1
            result.items[0].id shouldBe draftId1

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(emailAddressId, null, null)
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should handle different email address IDs`() =
        runTest {
            mockListDraftEmailMessagesForEmailAddressIdUseCase.stub {
                onBlocking { execute(emailAddressId2, null, null) } doReturn ListDraftEmailMessagesOutput(listOf(draftMessage3), null)
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessagesForEmailAddressId(
                        ListDraftEmailMessagesForEmailAddressIdInput(emailAddressId2, null, null),
                    )
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.items.size shouldBe 1
            result.items[0].id shouldBe draftId3
            result.items[0].emailAddressId shouldBe emailAddressId2

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(emailAddressId2, null, null)
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should handle large number of drafts for address`() =
        runTest {
            val largeDraftList =
                (1..30).map { index ->
                    EntityDataFactory.getDraftEmailMessageWithContentEntity(
                        id = "draft-$index",
                        emailAddressId = emailAddressId,
                        rfc822Data = unsealedDraftString.toByteArray(),
                    )
                }

            mockListDraftEmailMessagesForEmailAddressIdUseCase.stub {
                onBlocking { execute(emailAddressId, 30, null) } doReturn ListDraftEmailMessagesOutput(largeDraftList, null)
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessagesForEmailAddressId(ListDraftEmailMessagesForEmailAddressIdInput(emailAddressId, 30))
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.items.size shouldBe 30

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(emailAddressId, 30, null)
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should throw when use case throws`() =
        runTest {
            mockListDraftEmailMessagesForEmailAddressIdUseCase.stub {
                onBlocking { execute(any(), anyOrNull(), anyOrNull()) }.thenAnswer {
                    throw SudoEmailClient.EmailMessageException.FailedException("Mock error")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.listDraftEmailMessagesForEmailAddressId(ListDraftEmailMessagesForEmailAddressIdInput(emailAddressId))
                    }
                }

            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(emailAddressId, null, null)
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should properly transform entities to API types`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessagesForEmailAddressId(ListDraftEmailMessagesForEmailAddressIdInput(emailAddressId))
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.items shouldBe emailAddressIdDrafts.map { DraftEmailMessageTransformer.entityWithContentToApi(it) }

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(emailAddressId, null, null)
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should handle special characters in email address ID`() =
        runTest {
            val specialEmailAddressId = "email-id-with-special-chars-!@#"
            mockListDraftEmailMessagesForEmailAddressIdUseCase.stub {
                onBlocking { execute(specialEmailAddressId, null, null) } doReturn ListDraftEmailMessagesOutput(listOf(draftMessage1), null)
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessagesForEmailAddressId(
                        ListDraftEmailMessagesForEmailAddressIdInput(specialEmailAddressId, null, null),
                    )
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(specialEmailAddressId, null, null)
        }
}
