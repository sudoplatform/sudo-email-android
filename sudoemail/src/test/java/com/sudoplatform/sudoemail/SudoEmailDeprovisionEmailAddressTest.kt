/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.deprovisionEmailAddress]
 * using mocks and spies.
 */
class SudoEmailDeprovisionEmailAddressTest : BaseTests() {
    private val sealedEmailAddress by before {
        EntityDataFactory.getSealedEmailAddressEntity()
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking {
                deprovision(
                    any(),
                )
            } doAnswer {
                sealedEmailAddress
            }
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
            emailAddressService = mockEmailAddressService,
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
    fun `deprovisionEmailAddress() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deprovisionEmailAddress(mockEmailAddressId)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            with(result) {
                id shouldBe mockEmailAddressId
                owner shouldBe mockOwner
                owners.first().id shouldBe mockOwner
                owners.first().issuer shouldBe "issuer"
                emailAddress shouldBe "example@sudoplatform.com"
                size shouldBe 0.0
                numberOfEmailMessages shouldBe 0
                version shouldBe 1
                createdAt shouldBe Date(1L)
                updatedAt shouldBe Date(1L)
                lastReceivedAt shouldBe Date(1L)
                alias shouldBe null
            }

            verify(mockEmailAddressService).deprovision(
                check { input ->
                    input.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `deprovisionEmailAddress() should throw when service throws`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking {
                    deprovision(
                        any(),
                    )
                } doThrow SudoEmailClient.EmailAddressException.DeprovisionFailedException("Error")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.DeprovisionFailedException> {
                        client.deprovisionEmailAddress(mockEmailAddressId)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockEmailAddressService).deprovision(
                any(),
            )
        }
}
