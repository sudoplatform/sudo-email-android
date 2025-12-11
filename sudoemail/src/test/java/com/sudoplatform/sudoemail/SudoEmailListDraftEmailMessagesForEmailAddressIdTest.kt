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
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.ListDraftEmailMessagesForEmailAddressIdUseCase
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
import org.mockito.kotlin.any
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
                execute(any())
            } doReturn emailAddressIdDrafts
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
                    client.listDraftEmailMessagesForEmailAddressId(emailAddressId)
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 2
            result[0].id shouldBe draftId1
            result[0].emailAddressId shouldBe emailAddressId
            result[1].id shouldBe draftId2
            result[1].emailAddressId shouldBe emailAddressId

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(
                check { emailAddressIdArg ->
                    emailAddressIdArg shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should return empty list when no drafts for address`() =
        runTest {
            mockListDraftEmailMessagesForEmailAddressIdUseCase.stub {
                onBlocking { execute(any()) } doReturn emptyList()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessagesForEmailAddressId(emailAddressId)
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 0

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(emailAddressId)
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should return single draft for address`() =
        runTest {
            mockListDraftEmailMessagesForEmailAddressIdUseCase.stub {
                onBlocking { execute(any()) } doReturn listOf(draftMessage1)
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessagesForEmailAddressId(emailAddressId)
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 1
            result[0].id shouldBe draftId1

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(emailAddressId)
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should handle different email address IDs`() =
        runTest {
            mockListDraftEmailMessagesForEmailAddressIdUseCase.stub {
                onBlocking { execute(emailAddressId2) } doReturn listOf(draftMessage3)
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessagesForEmailAddressId(emailAddressId2)
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 1
            result[0].id shouldBe draftId3
            result[0].emailAddressId shouldBe emailAddressId2

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(emailAddressId2)
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
                onBlocking { execute(emailAddressId) } doReturn largeDraftList
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessagesForEmailAddressId(emailAddressId)
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 30

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(emailAddressId)
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should throw when use case throws`() =
        runTest {
            mockListDraftEmailMessagesForEmailAddressIdUseCase.stub {
                onBlocking { execute(any()) }.thenAnswer {
                    throw SudoEmailClient.EmailMessageException.FailedException("Mock error")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.listDraftEmailMessagesForEmailAddressId(emailAddressId)
                    }
                }

            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(emailAddressId)
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should properly transform entities to API types`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessagesForEmailAddressId(emailAddressId)
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result shouldBe emailAddressIdDrafts.map { DraftEmailMessageTransformer.entityWithContentToApi(it) }

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(emailAddressId)
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should handle special characters in email address ID`() =
        runTest {
            val specialEmailAddressId = "email-id-with-special-chars-!@#"
            mockListDraftEmailMessagesForEmailAddressIdUseCase.stub {
                onBlocking { execute(specialEmailAddressId) } doReturn listOf(draftMessage1)
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessagesForEmailAddressId(specialEmailAddressId)
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null

            verify(mockUseCaseFactory).createListDraftEmailMessagesForEmailAddressIdUseCase()
            verify(mockListDraftEmailMessagesForEmailAddressIdUseCase).execute(specialEmailAddressId)
        }
}
