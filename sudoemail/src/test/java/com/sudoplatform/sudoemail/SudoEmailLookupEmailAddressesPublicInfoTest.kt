/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.emailAddress.transformers.EmailAddressPublicInfoTransformer
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.LookupEmailAddressesPublicInfoUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.LookupEmailAddressesPublicInfoInput
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [SudoEmailClient.lookupEmailAddressesPublicInfo]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailLookupEmailAddressesPublicInfoTest : BaseTests() {
    private val emailAddressPublicInfoEntity = EntityDataFactory.getEmailAddressPublicInfoEntity()
    private val input by before {
        LookupEmailAddressesPublicInfoInput(
            listOf("emailAddress"),
        )
    }

    private val publicInfoEntities by before {
        listOf(emailAddressPublicInfoEntity)
    }

    private val mockUseCase by before {
        mock<LookupEmailAddressesPublicInfoUseCase>().stub {
            onBlocking { execute(any()) } doReturn publicInfoEntities
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createLookupEmailAddressesPublicInfoUseCase() } doReturn mockUseCase
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
    fun `lookupEmailAddressesPublicInfo() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.lookupEmailAddressesPublicInfo(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.count() shouldBe 1
            result[0] shouldBe EmailAddressPublicInfoTransformer.entityToApi(emailAddressPublicInfoEntity)

            verify(mockUseCaseFactory).createLookupEmailAddressesPublicInfoUseCase()
            verify(mockUseCase).execute(
                check { input ->
                    input.addresses shouldBe listOf("emailAddress")
                    input.throwIfNotAllInternal shouldBe false
                },
            )
        }

    @Test
    fun `lookupEmailAddressesPublicInfo() should return empty result when query result data is empty`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doAnswer {
                    emptyList()
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.lookupEmailAddressesPublicInfo(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe emptyList()

            verify(mockUseCaseFactory).createLookupEmailAddressesPublicInfoUseCase()
            verify(mockUseCase).execute(
                check { input ->
                    input.addresses shouldBe listOf("emailAddress")
                    input.throwIfNotAllInternal shouldBe false
                },
            )
        }

    @Test
    fun `lookupEmailAddressesPublicInfo() should throw when http error occurs`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doThrow SudoEmailClient.EmailAddressException.FailedException("HTTP Error")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                        client.lookupEmailAddressesPublicInfo(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createLookupEmailAddressesPublicInfoUseCase()
            verify(mockUseCase).execute(
                check { input ->
                    input.addresses shouldBe listOf("emailAddress")
                    input.throwIfNotAllInternal shouldBe false
                },
            )
        }

    @Test
    fun `lookupEmailAddressesPublicInfo() should not block coroutine cancellation exception`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            shouldThrow<CancellationException> {
                client.lookupEmailAddressesPublicInfo(input)
            }

            verify(mockUseCaseFactory).createLookupEmailAddressesPublicInfoUseCase()
            verify(mockUseCase).execute(
                check { input ->
                    input.addresses shouldBe listOf("emailAddress")
                    input.throwIfNotAllInternal shouldBe false
                },
            )
        }
}
