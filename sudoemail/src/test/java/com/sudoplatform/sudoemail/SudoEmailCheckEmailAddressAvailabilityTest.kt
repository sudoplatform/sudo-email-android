/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.CheckEmailAddressAvailabilityInput
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
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
 * Test the correct operation of [SudoEmailClient.checkEmailAddressAvailability]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailCheckEmailAddressAvailabilityTest : BaseTests() {
    private val localParts = listOf("foo")
    private val domains = listOf("bar.com")
    private val addresses = listOf("foo@bar.com", "food@bar.com")

    private val input by before {
        CheckEmailAddressAvailabilityInput(
            localParts,
            domains,
        )
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking {
                checkAvailability(
                    any(),
                )
            } doAnswer {
                addresses
            }
        }
    }

    override val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { getPassword(anyString()) } doReturn ByteArray(42)
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

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>()
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
            emailAddressService = mockEmailAddressService,
            useCaseFactory = mockUseCaseFactory,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockContext,
            mockUserClient,
            mockKeyManager,
            mockEmailAddressService,
            mockS3Client,
        )
    }

    @Test
    fun `checkEmailAddressAvailability() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.checkEmailAddressAvailability(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isEmpty() shouldBe false
            result.size shouldBe 2
            result shouldContainExactlyInAnyOrder addresses

            verify(mockEmailAddressService).checkAvailability(
                any(),
            )
        }

    @Test
    fun `checkEmailAddressAvailability() should return empty list output when query result data is empty`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking {
                    checkAvailability(
                        any(),
                    )
                } doAnswer {
                    emptyList<String>()
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.checkEmailAddressAvailability(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isEmpty() shouldBe true
            result.size shouldBe 0

            verify(mockEmailAddressService).checkAvailability(
                any(),
            )
        }

    @Test
    fun `checkEmailAddressAvailability() should return empty list output when query response is null`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking {
                    checkAvailability(
                        any(),
                    )
                } doAnswer {
                    emptyList<String>()
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.checkEmailAddressAvailability(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isEmpty() shouldBe true
            result.size shouldBe 0

            verify(mockEmailAddressService).checkAvailability(
                any(),
            )
        }

    @Test
    fun `checkEmailAddressAvailability() should throw when response has error`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking {
                    checkAvailability(
                        any(),
                    )
                } doThrow SudoEmailClient.EmailAddressException.FailedException("Test generated error")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                        client.checkEmailAddressAvailability(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockEmailAddressService).checkAvailability(
                any(),
            )
        }

    @Test
    fun `checkEmailAddressAvailability() should throw when http error occurs`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking {
                    checkAvailability(
                        any(),
                    )
                } doThrow SudoEmailClient.EmailAddressException.FailedException("HTTP Error")
            }
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                        client.checkEmailAddressAvailability(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockEmailAddressService).checkAvailability(
                any(),
            )
        }

    @Test
    fun `checkEmailAddressAvailability() should not block coroutine cancellation exception`() =
        runBlocking<Unit> {
            mockEmailAddressService.stub {
                onBlocking {
                    checkAvailability(
                        any(),
                    )
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            shouldThrow<CancellationException> {
                client.checkEmailAddressAvailability(input)
            }

            verify(mockEmailAddressService).checkAvailability(
                any(),
            )
        }
}
