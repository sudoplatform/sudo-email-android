/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.UpdateDraftEmailMessageUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.UpdateDraftEmailMessageInput
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
 * Test the correct operation of [SudoEmailClient.updateDraftEmailMessage]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailUpdateDraftEmailMessageTest : BaseTests() {
    private val input by before {
        UpdateDraftEmailMessageInput(
            mockDraftId,
            DataFactory.unsealedHeaderDetailsString.toByteArray(),
            mockEmailAddressId,
        )
    }

    private val mockUseCase by before {
        mock<UpdateDraftEmailMessageUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn mockDraftId
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createUpdateDraftEmailMessageUseCase() } doReturn mockUseCase
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
            apiClient = mockApiClient,
            sudoUserClient = mockUserClient,
            logger = mockLogger,
            serviceKeyManager = mockServiceKeyManager,
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
    fun `updateDraftEmailMessage() should return draft id on success`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.updateDraftEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()
            result shouldBe mockDraftId

            verify(mockUseCaseFactory).createUpdateDraftEmailMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.rfc822Data shouldBe input.rfc822Data
                    useCaseInput.emailAddressId shouldBe input.senderEmailAddressId
                    useCaseInput.draftId shouldBe input.id
                },
            )
        }

    @Test
    fun `updateDraftEmailMessage() should throw when use case throws FailedException`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                }.thenAnswer {
                    throw SudoEmailClient.EmailMessageException.FailedException("Mock")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.updateDraftEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createUpdateDraftEmailMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.rfc822Data shouldBe input.rfc822Data
                    useCaseInput.emailAddressId shouldBe input.senderEmailAddressId
                    useCaseInput.draftId shouldBe input.id
                },
            )
        }

    @Test
    fun `updateDraftEmailMessage() should throw when use case throws EmailAddressNotFoundException`() =
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
                        client.updateDraftEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createUpdateDraftEmailMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.rfc822Data shouldBe input.rfc822Data
                    useCaseInput.emailAddressId shouldBe input.senderEmailAddressId
                    useCaseInput.draftId shouldBe input.id
                },
            )
        }

    @Test
    fun `updateDraftEmailMessage() should throw when use case throws EmailMessageNotFoundException`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                }.thenAnswer {
                    throw SudoEmailClient.EmailMessageException.EmailMessageNotFoundException("Draft not found")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                        client.updateDraftEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createUpdateDraftEmailMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.rfc822Data shouldBe input.rfc822Data
                    useCaseInput.emailAddressId shouldBe input.senderEmailAddressId
                    useCaseInput.draftId shouldBe input.id
                },
            )
        }

    @Test
    fun `updateDraftEmailMessage() should throw when use case throws InvalidArgumentException`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                }.thenAnswer {
                    throw SudoEmailClient.EmailMessageException.InvalidArgumentException("Invalid argument")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                        client.updateDraftEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createUpdateDraftEmailMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.rfc822Data shouldBe input.rfc822Data
                    useCaseInput.emailAddressId shouldBe input.senderEmailAddressId
                    useCaseInput.draftId shouldBe input.id
                },
            )
        }
}
