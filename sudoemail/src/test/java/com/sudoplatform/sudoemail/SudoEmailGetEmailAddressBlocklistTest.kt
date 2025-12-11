/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.blockedAddress.GetEmailAddressBlocklistUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.BlockedEmailAddressAction
import com.sudoplatform.sudoemail.types.UnsealedBlockedAddress
import com.sudoplatform.sudoemail.types.UnsealedBlockedAddressStatus
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.getEmailAddressBlocklist]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetEmailAddressBlocklistTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId
    private val address1 = "spammer@example.com"
    private val address2 = "phisher@example.com"
    private val hashedValue1 = "hashedValue1"
    private val hashedValue2 = "hashedValue2"

    private val unsealedBlockedAddress1 =
        UnsealedBlockedAddress(
            address = address1,
            hashedBlockedValue = hashedValue1,
            action = BlockedEmailAddressAction.DROP,
            status = UnsealedBlockedAddressStatus.Completed,
            emailAddressId = emailAddressId,
        )

    private val unsealedBlockedAddress2 =
        UnsealedBlockedAddress(
            address = address2,
            hashedBlockedValue = hashedValue2,
            action = BlockedEmailAddressAction.SPAM,
            status = UnsealedBlockedAddressStatus.Completed,
            emailAddressId = null,
        )

    private val successResult by before {
        listOf(unsealedBlockedAddress1, unsealedBlockedAddress2)
    }

    private val mockUseCase by before {
        mock<GetEmailAddressBlocklistUseCase>().stub {
            onBlocking {
                execute()
            } doReturn successResult
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createGetEmailAddressBlocklistUseCase() } doReturn mockUseCase
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
            mockUseCase,
            mockUseCaseFactory,
        )
    }

    @Test
    fun `getEmailAddressBlocklist() should return blocked addresses when no errors present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailAddressBlocklist()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 2
            result[0].address shouldBe address1
            result[0].hashedBlockedValue shouldBe hashedValue1
            result[0].action shouldBe BlockedEmailAddressAction.DROP
            result[0].status shouldBe UnsealedBlockedAddressStatus.Completed
            result[0].emailAddressId shouldBe emailAddressId

            result[1].address shouldBe address2
            result[1].hashedBlockedValue shouldBe hashedValue2
            result[1].action shouldBe BlockedEmailAddressAction.SPAM
            result[1].status shouldBe UnsealedBlockedAddressStatus.Completed
            result[1].emailAddressId shouldBe null

            verify(mockUseCaseFactory).createGetEmailAddressBlocklistUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `getEmailAddressBlocklist() should return empty list when no blocked addresses`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute() } doReturn emptyList()
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailAddressBlocklist()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 0

            verify(mockUseCaseFactory).createGetEmailAddressBlocklistUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `getEmailAddressBlocklist() should return single blocked address`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute() } doReturn listOf(unsealedBlockedAddress1)
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailAddressBlocklist()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 1
            result[0].address shouldBe address1

            verify(mockUseCaseFactory).createGetEmailAddressBlocklistUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `getEmailAddressBlocklist() should handle addresses with different actions`() =
        runTest {
            val dropAddress =
                UnsealedBlockedAddress(
                    address = "drop@example.com",
                    hashedBlockedValue = "hashedDrop",
                    action = BlockedEmailAddressAction.DROP,
                    status = UnsealedBlockedAddressStatus.Completed,
                    emailAddressId = null,
                )
            val spamAddress =
                UnsealedBlockedAddress(
                    address = "spam@example.com",
                    hashedBlockedValue = "hashedSpam",
                    action = BlockedEmailAddressAction.SPAM,
                    status = UnsealedBlockedAddressStatus.Completed,
                    emailAddressId = emailAddressId,
                )

            mockUseCase.stub {
                onBlocking { execute() } doReturn listOf(dropAddress, spamAddress)
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailAddressBlocklist()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 2
            result[0].action shouldBe BlockedEmailAddressAction.DROP
            result[1].action shouldBe BlockedEmailAddressAction.SPAM

            verify(mockUseCaseFactory).createGetEmailAddressBlocklistUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `getEmailAddressBlocklist() should handle addresses with and without emailAddressId`() =
        runTest {
            val withEmailAddressId =
                UnsealedBlockedAddress(
                    address = "with@example.com",
                    hashedBlockedValue = "hashedWith",
                    action = BlockedEmailAddressAction.DROP,
                    status = UnsealedBlockedAddressStatus.Completed,
                    emailAddressId = emailAddressId,
                )
            val withoutEmailAddressId =
                UnsealedBlockedAddress(
                    address = "without@example.com",
                    hashedBlockedValue = "hashedWithout",
                    action = BlockedEmailAddressAction.DROP,
                    status = UnsealedBlockedAddressStatus.Completed,
                    emailAddressId = null,
                )

            mockUseCase.stub {
                onBlocking { execute() } doReturn listOf(withEmailAddressId, withoutEmailAddressId)
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailAddressBlocklist()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 2
            result[0].emailAddressId shouldBe emailAddressId
            result[1].emailAddressId shouldBe null

            verify(mockUseCaseFactory).createGetEmailAddressBlocklistUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `getEmailAddressBlocklist() should include addresses with Failed status`() =
        runTest {
            val failedException = SudoEmailClient.EmailBlocklistException.FailedException("Unsealing failed")
            val completedAddress =
                UnsealedBlockedAddress(
                    address = address1,
                    hashedBlockedValue = hashedValue1,
                    action = BlockedEmailAddressAction.DROP,
                    status = UnsealedBlockedAddressStatus.Completed,
                    emailAddressId = null,
                )
            val failedAddress =
                UnsealedBlockedAddress(
                    address = "",
                    hashedBlockedValue = hashedValue2,
                    action = BlockedEmailAddressAction.SPAM,
                    status = UnsealedBlockedAddressStatus.Failed(failedException),
                    emailAddressId = emailAddressId,
                )

            mockUseCase.stub {
                onBlocking { execute() } doReturn listOf(completedAddress, failedAddress)
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailAddressBlocklist()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 2
            result[0].status shouldBe UnsealedBlockedAddressStatus.Completed
            (result[1].status is UnsealedBlockedAddressStatus.Failed) shouldBe true

            verify(mockUseCaseFactory).createGetEmailAddressBlocklistUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `getEmailAddressBlocklist() should handle multiple blocked addresses`() =
        runTest {
            val addresses =
                (1..5).map { index ->
                    UnsealedBlockedAddress(
                        address = "address$index@example.com",
                        hashedBlockedValue = "hashedValue$index",
                        action = BlockedEmailAddressAction.DROP,
                        status = UnsealedBlockedAddressStatus.Completed,
                        emailAddressId = if (index % 2 == 0) emailAddressId else null,
                    )
                }

            mockUseCase.stub {
                onBlocking { execute() } doReturn addresses
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailAddressBlocklist()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 5
            result.forEachIndexed { index, unsealedAddress ->
                unsealedAddress.address shouldBe "address${index + 1}@example.com"
            }

            verify(mockUseCaseFactory).createGetEmailAddressBlocklistUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `getEmailAddressBlocklist() should throw when use case throws`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute() } doThrow SudoEmailClient.EmailBlocklistException.FailedException("Failed")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailBlocklistException.FailedException> {
                        client.getEmailAddressBlocklist()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createGetEmailAddressBlocklistUseCase()
            verify(mockUseCase).execute()
        }

    @Test
    fun `getEmailAddressBlocklist() should handle mixed Completed and Failed statuses`() =
        runTest {
            val addresses =
                listOf(
                    UnsealedBlockedAddress(
                        address = address1,
                        hashedBlockedValue = hashedValue1,
                        action = BlockedEmailAddressAction.DROP,
                        status = UnsealedBlockedAddressStatus.Completed,
                        emailAddressId = null,
                    ),
                    UnsealedBlockedAddress(
                        address = "",
                        hashedBlockedValue = hashedValue2,
                        action = BlockedEmailAddressAction.SPAM,
                        status =
                            UnsealedBlockedAddressStatus.Failed(
                                SudoEmailClient.EmailBlocklistException.FailedException("Failed"),
                            ),
                        emailAddressId = emailAddressId,
                    ),
                    UnsealedBlockedAddress(
                        address = "another@example.com",
                        hashedBlockedValue = "hashedValue3",
                        action = BlockedEmailAddressAction.DROP,
                        status = UnsealedBlockedAddressStatus.Completed,
                        emailAddressId = null,
                    ),
                )

            mockUseCase.stub {
                onBlocking { execute() } doReturn addresses
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailAddressBlocklist()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 3
            result[0].status shouldBe UnsealedBlockedAddressStatus.Completed
            (result[1].status is UnsealedBlockedAddressStatus.Failed) shouldBe true
            result[2].status shouldBe UnsealedBlockedAddressStatus.Completed

            verify(mockUseCaseFactory).createGetEmailAddressBlocklistUseCase()
            verify(mockUseCase).execute()
        }
}
