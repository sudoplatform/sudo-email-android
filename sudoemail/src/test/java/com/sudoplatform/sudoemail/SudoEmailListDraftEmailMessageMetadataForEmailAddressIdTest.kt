/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.ListDraftEmailMessageMetadataForEmailAddressIdUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import io.kotlintest.shouldBe
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
 * Test the correct operation of [SudoEmailClient.listDraftEmailMessageMetadataForEmailAddressId]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListDraftEmailMessageMetadataForEmailAddressIdTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId
    private val draftEmailMessageMetadataEntity1 =
        EntityDataFactory.getDraftEmailMessageMetadataEntity(
            id = "draftId1",
            emailAddressId = emailAddressId,
        )
    private val draftEmailMessageMetadataEntity2 =
        EntityDataFactory.getDraftEmailMessageMetadataEntity(
            id = "draftId2",
            emailAddressId = emailAddressId,
        )

    private val mockUseCase by before {
        mock<ListDraftEmailMessageMetadataForEmailAddressIdUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn listOf(draftEmailMessageMetadataEntity1, draftEmailMessageMetadataEntity2)
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createListDraftEmailMessageMetadataForEmailAddressIdUseCase() } doReturn mockUseCase
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
    fun `listDraftEmailMessageMetadataForEmailAddressId() should return a list of metadata for the email address`() =
        runTest {
            val emailAddressId = mockEmailAddressId

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessageMetadataForEmailAddressId(emailAddressId)
                }

            deferredResult.start()
            val result = deferredResult.await()

            result.size shouldBe 2
            result[0].id shouldBe draftEmailMessageMetadataEntity1.id
            result[0].emailAddressId shouldBe emailAddressId
            result[1].id shouldBe draftEmailMessageMetadataEntity2.id
            result[1].emailAddressId shouldBe emailAddressId

            verify(mockUseCaseFactory).createListDraftEmailMessageMetadataForEmailAddressIdUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `listDraftEmailMessageMetadataForEmailAddressId() should return an empty list if no drafts found`() =
        runTest {
            val emailAddressId = mockEmailAddressId

            mockUseCase.stub {
                onBlocking {
                    execute(any())
                } doReturn emptyList()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listDraftEmailMessageMetadataForEmailAddressId(emailAddressId)
                }

            deferredResult.start()
            val result = deferredResult.await()

            result.size shouldBe 0

            verify(mockUseCaseFactory).createListDraftEmailMessageMetadataForEmailAddressIdUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `listDraftEmailMessageMetadataForEmailAddressId() should throw when use case throws`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                }.thenAnswer {
                    throw SudoEmailClient.EmailAddressException.EmailAddressNotFoundException("Mock")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                        client.listDraftEmailMessageMetadataForEmailAddressId(emailAddressId)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createListDraftEmailMessageMetadataForEmailAddressIdUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput shouldBe emailAddressId
                },
            )
        }
}
