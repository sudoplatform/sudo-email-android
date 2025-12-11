/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.common.mechanisms.Unsealer
import com.sudoplatform.sudoemail.internal.data.emailAddress.transformers.EmailAddressTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListAPIResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListPartialResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListSuccessResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.PartialResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.UnsealedEmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.ListEmailAddressesForSudoIdUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesForSudoIdInput
import io.kotlintest.fail
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

/**
 * Test the correct operation of [SudoEmailClient.listEmailAddressesForSudoId]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListEmailAddressesForSudoIdTest : BaseTests() {
    private val unsealedEmailAddress = EntityDataFactory.getUnsealedEmailAddressEntity()
    private val partialEmailAddress = EntityDataFactory.getPartialEmailAddressEntity()
    private val resultNextToken = "resultNextToken"
    private val input by before {
        ListEmailAddressesForSudoIdInput(mockSudoId)
    }

    private val listSuccessResult by before {
        ListAPIResultEntity.Success(
            ListSuccessResultEntity(
                listOf(unsealedEmailAddress),
                null,
            ),
        )
    }

    private val listSuccessResultWithNextToken by before {
        ListAPIResultEntity.Success(
            ListSuccessResultEntity(
                listOf(unsealedEmailAddress),
                resultNextToken,
            ),
        )
    }

    private val listSuccessResultWithEmptyList by before {
        ListAPIResultEntity.Success(
            ListSuccessResultEntity(
                emptyList<UnsealedEmailAddressEntity>(),
                null,
            ),
        )
    }

    private val listPartialResult by before {
        ListAPIResultEntity.Partial(
            ListPartialResultEntity(
                listOf(unsealedEmailAddress),
                listOf(
                    PartialResultEntity(
                        partialEmailAddress,
                        Unsealer.UnsealerException.UnsupportedAlgorithmException(),
                    ),
                ),
                null,
            ),
        )
    }

    private val mockUseCase by before {
        mock<ListEmailAddressesForSudoIdUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn listSuccessResult
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createListEmailAddressesForSudoIdUseCase() } doReturn mockUseCase
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
    fun `listEmailAddressesForSudoId() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailAddressesForSudoId(input)
                }
            deferredResult.start()
            val listEmailAddressesForSudoId = deferredResult.await()

            listEmailAddressesForSudoId shouldNotBe null
            when (listEmailAddressesForSudoId) {
                is ListAPIResult.Success -> {
                    listEmailAddressesForSudoId.result.items.size shouldBe 1
                    listEmailAddressesForSudoId.result.nextToken shouldBe null
                    listEmailAddressesForSudoId.result.items[0] shouldBe EmailAddressTransformer.unsealedEntityToApi(unsealedEmailAddress)
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailAddressesForSudoIdUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.sudoId shouldBe mockSudoId
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe SudoEmailClient.DEFAULT_EMAIL_ADDRESS_LIMIT
                },
            )
        }

    @Test
    fun `listEmailAddressesForSudoId() should return results when populating nextToken`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doAnswer {
                    listSuccessResultWithNextToken
                }
            }
            val nextToken = "nextToken"
            val input = ListEmailAddressesForSudoIdInput(mockSudoId, nextToken = nextToken)
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailAddressesForSudoId(input)
                }
            deferredResult.start()
            val listEmailAddressesForSudoId = deferredResult.await()

            listEmailAddressesForSudoId shouldNotBe null
            when (listEmailAddressesForSudoId) {
                is ListAPIResult.Success -> {
                    listEmailAddressesForSudoId.result.items.size shouldBe 1
                    listEmailAddressesForSudoId.result.nextToken shouldBe resultNextToken
                    listEmailAddressesForSudoId.result.items[0] shouldBe EmailAddressTransformer.unsealedEntityToApi(unsealedEmailAddress)
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailAddressesForSudoIdUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.sudoId shouldBe mockSudoId
                    useCaseInput.nextToken shouldBe nextToken
                    useCaseInput.limit shouldBe SudoEmailClient.DEFAULT_EMAIL_ADDRESS_LIMIT
                },
            )
        }

    @Test
    fun `listEmailAddressesForSudoId() should return empty list output when use case result is empty`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doAnswer {
                    listSuccessResultWithEmptyList
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailAddressesForSudoId(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            when (result) {
                is ListAPIResult.Success -> {
                    result.result.items.isEmpty() shouldBe true
                    result.result.items.size shouldBe 0
                    result.result.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailAddressesForSudoIdUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.sudoId shouldBe mockSudoId
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe SudoEmailClient.DEFAULT_EMAIL_ADDRESS_LIMIT
                },
            )
        }

    @Test
    fun `listEmailAddressesForSudoId() should return partial result when use case returns partial`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doAnswer {
                    listPartialResult
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailAddressesForSudoId(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null

            when (result) {
                is ListAPIResult.Partial -> {
                    result.result.items.size shouldBe 1
                    result.result.items[0] shouldBe EmailAddressTransformer.unsealedEntityToApi(unsealedEmailAddress)
                    result.result.failed.size shouldBe 1
                    result.result.failed[0].partial shouldBe EmailAddressTransformer.partialEntityToApi(partialEmailAddress)
                    result.result.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailAddressesForSudoIdUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe SudoEmailClient.DEFAULT_EMAIL_ADDRESS_LIMIT
                },
            )
        }

    @Test
    fun `listEmailAddressesForSudoId() should throw when use case error occurs`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doThrow
                    SudoEmailClient.EmailAddressException.FailedException("Mock Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                        client.listEmailAddressesForSudoId(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createListEmailAddressesForSudoIdUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.sudoId shouldBe mockSudoId
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe SudoEmailClient.DEFAULT_EMAIL_ADDRESS_LIMIT
                },
            )
        }
}
