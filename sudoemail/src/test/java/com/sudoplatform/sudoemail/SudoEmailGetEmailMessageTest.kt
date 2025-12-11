/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.emailMessage.transformers.EmailMessageTransformer
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.GetEmailMessageUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.getEmailMessage]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetEmailMessageTest : BaseTests() {
    private val unsealedEmailMessageEntity = EntityDataFactory.getEmailMessageEntity(id = mockEmailMessageId)
    private val input by before {
        GetEmailMessageInput(id = mockEmailMessageId)
    }

    private val mockUseCase by before {
        mock<GetEmailMessageUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn unsealedEmailMessageEntity
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createGetEmailMessageUseCase() } doReturn mockUseCase
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
            mockUseCase,
            mockUseCaseFactory,
        )
    }

    @Test
    fun `getEmailMessage() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null

            result shouldBe EmailMessageTransformer.entityToApi(unsealedEmailMessageEntity)

            verify(mockUseCaseFactory).createGetEmailMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.id shouldBe mockEmailMessageId
                },
            )
        }

    @Test
    fun `getEmailMessage() should return null result when use case response is null`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doAnswer {
                    null
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe null

            verify(mockUseCaseFactory).createGetEmailMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.id shouldBe mockEmailMessageId
                },
            )
        }

    @Test
    fun `getEmailMessage() should throw when unknown error occurs`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                }.thenAnswer {
                    throw SudoEmailClient.EmailMessageException.FailedException("Mock")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.getEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createGetEmailMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.id shouldBe mockEmailMessageId
                },
            )
        }
}
