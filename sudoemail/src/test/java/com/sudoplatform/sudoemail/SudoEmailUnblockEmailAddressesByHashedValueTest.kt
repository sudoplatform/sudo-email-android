/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationStatusEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.blockedAddress.UnblockEmailAddressesByHashedValueUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import io.kotlintest.matchers.collections.shouldContain
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
import java.util.UUID

/**
 * Test the correct operation of [SudoEmailClient.unblockEmailAddressesByHashedValue]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailUnblockEmailAddressesByHashedValueTest : BaseTests() {
    private var hashedValues: List<String> =
        listOf(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
        )

    private val successResult by before {
        BatchOperationResultEntity<String, String>(
            status = BatchOperationStatusEntity.SUCCESS,
            successValues = hashedValues,
            failureValues = emptyList(),
        )
    }

    private val partialResult by before {
        BatchOperationResultEntity<String, String>(
            status = BatchOperationStatusEntity.PARTIAL,
            successValues = listOf(hashedValues[1]),
            failureValues = listOf(hashedValues[0]),
        )
    }

    private val failureResult by before {
        BatchOperationResultEntity<String, String>(
            status = BatchOperationStatusEntity.FAILURE,
            successValues = emptyList(),
            failureValues = hashedValues,
        )
    }

    private val mockUseCase by before {
        mock<UnblockEmailAddressesByHashedValueUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn successResult
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createUnblockEmailAddressesByHashedValueUseCase() } doReturn mockUseCase
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
            emailBucket = "emailBucket",
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
            mockUseCase,
            mockUseCaseFactory,
        )
    }

    @Test
    fun `unblockEmailAddressesByHashedValue() should return success when no errors present`() =
        runTest {
            hashedValues.size shouldNotBe 0

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.unblockEmailAddressesByHashedValue(hashedValues)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockUseCaseFactory).createUnblockEmailAddressesByHashedValueUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.hashedValues shouldBe hashedValues
                },
            )
        }

    @Test
    fun `unblockEmailAddressesByHashedValue() should return failure when use case returns failed status`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                } doReturn failureResult
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.unblockEmailAddressesByHashedValue(hashedValues)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.FAILURE

            verify(mockUseCaseFactory).createUnblockEmailAddressesByHashedValueUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.hashedValues shouldBe hashedValues
                },
            )
        }

    @Test
    fun `unblockEmailAddressesByHashedValue() should return proper lists on partial`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                } doReturn partialResult
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.unblockEmailAddressesByHashedValue(hashedValues)
                }
            deferredResult.start()
            val result = deferredResult.await()
            result shouldNotBe null

            result.status shouldBe BatchOperationStatus.PARTIAL
            result.failureValues?.shouldContain(hashedValues[0])
            result.successValues?.shouldContain(hashedValues[1])

            verify(mockUseCaseFactory).createUnblockEmailAddressesByHashedValueUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.hashedValues shouldBe hashedValues
                },
            )
        }

    @Test
    fun `unblockEmailAddressesByHashedValue() should throw when use case throws error`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                }.thenAnswer {
                    throw SudoEmailClient.EmailBlocklistException.FailedException("Mock")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailBlocklistException.FailedException> {
                        client.unblockEmailAddressesByHashedValue(hashedValues)
                    }
                }
            deferredResult.start()
            val result = deferredResult.await()
            result shouldNotBe null

            verify(mockUseCaseFactory).createUnblockEmailAddressesByHashedValueUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.hashedValues shouldBe hashedValues
                },
            )
        }
}
