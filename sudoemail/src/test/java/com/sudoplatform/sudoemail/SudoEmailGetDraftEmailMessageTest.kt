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
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.GetDraftEmailMessageUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
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
* Test the correct operation of [SudoEmailClient.getDraftEmailMessage]
* using mocks and spies
*/
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetDraftEmailMessageTest : BaseTests() {
    private val unsealedDraftString = DataFactory.unsealedHeaderDetailsString

    private val input by before {
        GetDraftEmailMessageInput(mockDraftId, mockEmailAddressId)
    }

    private val draftMessageWithContentEntity by before {
        EntityDataFactory.getDraftEmailMessageWithContentEntity(
            id = mockDraftId,
            emailAddressId = mockEmailAddressId,
            rfc822Data = unsealedDraftString.toByteArray(),
        )
    }

    private val mockUseCase by before {
        mock<GetDraftEmailMessageUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn draftMessageWithContentEntity
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createGetDraftEmailMessageUseCase() } doReturn mockUseCase
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
            mockUseCase,
        )
    }

    @Test
    fun `getDraftEmailMessage() should return proper data if no errors`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getDraftEmailMessage(input)
                }

            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe DraftEmailMessageTransformer.entityWithContentToApi(draftMessageWithContentEntity)

            verify(mockUseCaseFactory).createGetDraftEmailMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.draftId shouldBe mockDraftId
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `getDraftEmailMessage() should throw when use case throws`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                }.thenAnswer {
                    throw SudoEmailClient.EmailMessageException.EmailMessageNotFoundException("Mock")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                        client.getDraftEmailMessage(input)
                    }
                }

            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createGetDraftEmailMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.draftId shouldBe mockDraftId
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }
}
