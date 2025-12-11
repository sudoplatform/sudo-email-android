/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.ProvisionEmailAddressUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.ProvisionEmailAddressInput
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
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.provisionEmailAddress]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailProvisionEmailAddressTest : BaseTests() {
    private val input by before {
        ProvisionEmailAddressInput(
            "example@sudoplatform.com",
            "ownershipProofToken",
        )
    }

    private val unsealedEmailAddress by before {
        EntityDataFactory.getUnsealedEmailAddressEntity()
    }

    private val mockUseCase by before {
        mock<ProvisionEmailAddressUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn unsealedEmailAddress
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createProvisionEmailAddressUseCase() } doReturn mockUseCase
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
    fun `provisionEmailAddress() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.provisionEmailAddress(input)
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
                folders.size shouldBe 1
                with(folders[0]) {
                    id shouldBe mockFolderId
                    owner shouldBe mockOwner
                    owners.first().id shouldBe mockOwner
                    owners.first().issuer shouldBe "issuer"
                    emailAddressId shouldBe mockEmailAddressId
                    folderName shouldBe "folderName"
                    size shouldBe 0.0
                    unseenCount shouldBe 0.0
                    version shouldBe 1
                    createdAt shouldBe Date(1L)
                    updatedAt shouldBe Date(1L)
                    customFolderName shouldBe null
                }
            }

            verify(mockUseCaseFactory).createProvisionEmailAddressUseCase()
            verify(mockUseCase).execute(
                check { input ->
                    input.emailAddress shouldBe "example@sudoplatform.com"
                    input.ownershipProofToken shouldBe "ownershipProofToken"
                    input.alias shouldBe null
                },
            )
        }

    @Test
    fun `provisionEmailAddress() should pass alias when provided`() =
        runTest {
            val input =
                ProvisionEmailAddressInput(
                    "example@sudoplatform.com",
                    "ownershipProofToken",
                    "alias",
                )
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.provisionEmailAddress(input)
                }
            deferredResult.start()
            val result = deferredResult.await()
            result shouldNotBe null
            verify(mockUseCaseFactory).createProvisionEmailAddressUseCase()
            verify(mockUseCase).execute(
                check { input ->
                    input.emailAddress shouldBe "example@sudoplatform.com"
                    input.ownershipProofToken shouldBe "ownershipProofToken"
                    input.alias shouldBe "alias"
                },
            )
        }

    @Test
    fun `provisionEmailAddress() should pass keyId when provided`() =
        runTest {
            val input =
                ProvisionEmailAddressInput(
                    "example@sudoplatform.com",
                    "ownershipProofToken",
                    "alias",
                    "keyId",
                )
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.provisionEmailAddress(input)
                }
            deferredResult.start()
            val result = deferredResult.await()
            result shouldNotBe null
            verify(mockUseCaseFactory).createProvisionEmailAddressUseCase()
            verify(mockUseCase).execute(
                check { input ->
                    input.emailAddress shouldBe "example@sudoplatform.com"
                    input.ownershipProofToken shouldBe "ownershipProofToken"
                    input.alias shouldBe "alias"
                    input.keyId shouldBe "keyId"
                },
            )
        }

    @Test
    fun `provisionEmailAddress() should throw error when use case throws`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                }.thenAnswer {
                    throw SudoEmailClient.EmailAddressException.ProvisionFailedException("Mock")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.ProvisionFailedException> {
                        client.provisionEmailAddress(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createProvisionEmailAddressUseCase()
            verify(mockUseCase).execute(
                check { input ->
                    input.emailAddress shouldBe "example@sudoplatform.com"
                    input.ownershipProofToken shouldBe "ownershipProofToken"
                    input.alias shouldBe null
                },
            )
        }
}
