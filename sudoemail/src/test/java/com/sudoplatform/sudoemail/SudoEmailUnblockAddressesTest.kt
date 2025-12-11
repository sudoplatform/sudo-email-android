/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationStatusEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.blockedAddress.UnblockEmailAddressesUseCase
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
 * Test the correct operation of [SudoEmailClient.unblockEmailAddresses]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailUnblockAddressesTest : BaseTests() {
    private var addresses: List<String> =
        listOf(
            "spammyMcSpamface${UUID.randomUUID()}@spambot.com",
            "spammyMcSpamface${UUID.randomUUID()}@spambot2.com",
        )

    private val successResult by before {
        BatchOperationResultEntity<String, String>(
            status = BatchOperationStatusEntity.SUCCESS,
            successValues = addresses,
            failureValues = emptyList(),
        )
    }

    private val partialResult by before {
        BatchOperationResultEntity<String, String>(
            status = BatchOperationStatusEntity.PARTIAL,
            successValues = listOf(addresses[1]),
            failureValues = listOf(addresses[0]),
        )
    }

    private val failureResult by before {
        BatchOperationResultEntity<String, String>(
            status = BatchOperationStatusEntity.FAILURE,
            successValues = emptyList(),
            failureValues = addresses,
        )
    }

    private val mockUseCase by before {
        mock<UnblockEmailAddressesUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn successResult
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createUnblockEmailAddressesUseCase() } doReturn mockUseCase
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
    fun `unblockEmailAddresses() should return success when no errors present`() =
        runTest {
            addresses.size shouldNotBe 0

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.unblockEmailAddresses(addresses)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockUseCaseFactory).createUnblockEmailAddressesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.addresses shouldBe addresses
                },
            )
        }

    @Test
    fun `unblockEmailAddresses() should return failure when use case returns failed status`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                } doReturn failureResult
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.unblockEmailAddresses(addresses)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.FAILURE

            verify(mockUseCaseFactory).createUnblockEmailAddressesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.addresses shouldBe addresses
                },
            )
        }

    @Test
    fun `unblockEmailAddresses() should return proper lists on partial`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                } doReturn partialResult
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.unblockEmailAddresses(addresses)
                }
            deferredResult.start()
            val result = deferredResult.await()
            result shouldNotBe null

            result.status shouldBe BatchOperationStatus.PARTIAL
            result.failureValues?.shouldContain(addresses[0])
            result.successValues?.shouldContain(addresses[1])

            verify(mockUseCaseFactory).createUnblockEmailAddressesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.addresses shouldBe addresses
                },
            )
        }

    @Test
    fun `unblockEmailAddresses() should throw when use case throws error`() =
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
                        client.unblockEmailAddresses(addresses)
                    }
                }
            deferredResult.start()
            val result = deferredResult.await()
            result shouldNotBe null

            verify(mockUseCaseFactory).createUnblockEmailAddressesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.addresses shouldBe addresses
                },
            )
        }
}
