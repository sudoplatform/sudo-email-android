/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.DeleteEmailMessagesUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.DeleteEmailMessageSuccessResult
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
 * Test the correct operation of [SudoEmailClient.deleteEmailMessage]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailDeleteEmailMessageTest : BaseTests() {
    private val successResult by before {
        DeleteEmailMessageSuccessResult(mockEmailMessageId)
    }

    private val mockUseCase by before {
        mock<DeleteEmailMessagesUseCase>().stub {
            onBlocking {
                execute(any<String>())
            } doReturn successResult
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createDeleteEmailMessagesUseCase() } doReturn mockUseCase
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
            mockUserClient,
            mockKeyManager,
            mockApiClient,
            mockS3Client,
            mockUseCase,
            mockUseCaseFactory,
        )
    }

    @Test
    fun `deleteEmailMessage() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteEmailMessage(mockEmailMessageId)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result?.id?.isBlank() shouldBe false

            verify(mockUseCaseFactory).createDeleteEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check<String> { id ->
                    id shouldBe mockEmailMessageId
                },
            )
        }

    @Test
    fun `deleteEmailMessage() should return null result when use case returns null`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any<String>(),
                    )
                } doAnswer {
                    null
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteEmailMessage(mockEmailMessageId)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe null

            verify(mockUseCaseFactory).createDeleteEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check<String> { id ->
                    id shouldBe mockEmailMessageId
                },
            )
        }

    @Test
    fun `deleteEmailMessage() should throw when when use case throws error`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any<String>(),
                    )
                }.thenAnswer {
                    throw SudoEmailClient.EmailMessageException.FailedException("Mock")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.deleteEmailMessage(mockEmailMessageId)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createDeleteEmailMessagesUseCase()
            verify(mockUseCase).execute(
                check<String> { id ->
                    id shouldBe mockEmailMessageId
                },
            )
        }
}
