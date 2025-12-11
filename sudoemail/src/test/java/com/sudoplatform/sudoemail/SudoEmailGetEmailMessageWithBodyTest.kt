/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.GetEmailMessageWithBodyUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageWithBodyInput
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.getEmailMessageWithBody]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetEmailMessageWithBodyTest : BaseTests() {
    private val emailMessageWithBodyEntity =
        EntityDataFactory.getEmailMessageWithBodyEntity(
            id = mockEmailAddressId,
        )
    private val input by before {
        GetEmailMessageWithBodyInput(
            id = mockEmailMessageId,
            emailAddressId = mockEmailAddressId,
        )
    }

    private val mockUseCase by before {
        mock<GetEmailMessageWithBodyUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn emailMessageWithBodyEntity
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createGetEmailMessageWithBodyUseCase() } doReturn mockUseCase
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
    fun `getEmailMessageWithBody() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailMessageWithBody(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result?.id shouldBe mockEmailMessageId
            result?.body shouldBe emailMessageWithBodyEntity.body

            verify(mockUseCaseFactory).createGetEmailMessageWithBodyUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.id shouldBe mockEmailMessageId
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `getEmailMessageWithBody() should return null result when use case response is null`() =
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
                    client.getEmailMessageWithBody(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe null

            verify(mockUseCaseFactory).createGetEmailMessageWithBodyUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.id shouldBe mockEmailMessageId
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `getEmailMessageWithBody() should throw when error occurs`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doThrow SudoEmailClient.EmailMessageException.FailedException("Mock Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.getEmailMessageWithBody(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createGetEmailMessageWithBodyUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.id shouldBe mockEmailMessageId
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }
}
