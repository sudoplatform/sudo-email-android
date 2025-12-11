/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.UpdateEmailAddressMetadataUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailAddressMetadataInput
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
 * Test the correct operation of [SudoEmailClient.updateEmailAddressMetadata]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailUpdateEmailAddressMetadataTest : BaseTests() {
    private val unsealedEmailAddress by before {
        EntityDataFactory.getUnsealedEmailAddressEntity()
    }

    private val input by before {
        UpdateEmailAddressMetadataInput(
            unsealedEmailAddress.id,
            "John Doe",
        )
    }

    private val mockUseCase by before {
        mock<UpdateEmailAddressMetadataUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn unsealedEmailAddress.id
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createUpdateEmailAddressMetadataUseCase() } doReturn mockUseCase
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
    fun `updateEmailAddressMetadata() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.updateEmailAddressMetadata(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe unsealedEmailAddress.id
            verify(mockUseCaseFactory).createUpdateEmailAddressMetadataUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe unsealedEmailAddress.id
                    useCaseInput.alias shouldBe input.alias
                },
            )
        }

    @Test
    fun `updateEmailAddressMetadata() should throw when use case throws`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                }.thenAnswer {
                    throw SudoEmailClient.EmailAddressException.UpdateFailedException("Mock")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.UpdateFailedException> {
                        client.updateEmailAddressMetadata(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createUpdateEmailAddressMetadataUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe unsealedEmailAddress.id
                    useCaseInput.alias shouldBe input.alias
                },
            )
        }
}
