/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.common.mechanisms.Unsealer
import com.sudoplatform.sudoemail.internal.data.emailAlias.transformers.EmailAliasTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListAPIResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListPartialResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListSuccessResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.PartialResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.UnsealedEmailAliasListEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias.ListEmailAliasesUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.EmailAliasRealAddressType
import com.sudoplatform.sudoemail.types.EmailAliasStatus
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.EmailAliasFilterInput
import com.sudoplatform.sudoemail.types.inputs.EqualStatusFilter
import com.sudoplatform.sudoemail.types.inputs.ListEmailAliasesInput
import com.sudoplatform.sudoemail.types.inputs.NotEqualRealAddressTypeFilter
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.listEmailAliases]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListEmailAliasesTest : BaseTests() {
    private val unsealedEmailAlias by before {
        EntityDataFactory.getUnsealedEmailAliasListEntity()
    }

    private val partialEmailAlias by before {
        EntityDataFactory.getPartialUnsealedAliasListEntity()
    }

    private val resultNextToken = "resultNextToken"

    private val input by before {
        ListEmailAliasesInput()
    }

    private val listSuccessResult by before {
        ListAPIResultEntity.Success(
            ListSuccessResultEntity(
                listOf(unsealedEmailAlias),
                null,
            ),
        )
    }

    private val listSuccessResultWithNextToken by before {
        ListAPIResultEntity.Success(
            ListSuccessResultEntity(
                listOf(unsealedEmailAlias),
                resultNextToken,
            ),
        )
    }

    private val listSuccessResultWithEmptyList by before {
        ListAPIResultEntity.Success(
            ListSuccessResultEntity(
                emptyList<UnsealedEmailAliasListEntity>(),
                null,
            ),
        )
    }

    private val listPartialResult by before {
        ListAPIResultEntity.Partial(
            ListPartialResultEntity(
                listOf(unsealedEmailAlias),
                listOf(
                    PartialResultEntity(
                        partialEmailAlias,
                        Unsealer.UnsealerException.UnsupportedAlgorithmException(),
                    ),
                ),
                null,
            ),
        )
    }

    private val mockUseCase by before {
        mock<ListEmailAliasesUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn listSuccessResult
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createListEmailAliasesUseCase() } doReturn mockUseCase
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
            enforceSingletonKeyPair = false,
            allowCallerManagedKeys = true,
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
    fun `listEmailAliases() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailAliases(input)
                }
            deferredResult.start()

            val listAliasesResult = deferredResult.await()

            listAliasesResult shouldNotBe null
            when (listAliasesResult) {
                is ListAPIResult.Success -> {
                    listAliasesResult.result.items.size shouldBe 1
                    listAliasesResult.result.nextToken shouldBe null
                    listAliasesResult.result.items[0] shouldBe EmailAliasTransformer.unsealedListEntityToApi(unsealedEmailAlias)
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailAliasesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.sudoId shouldBe null
                    useCaseInput.filter shouldBe null
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe null
                },
            )
        }

    @Test
    fun `listEmailAliases() should handle sudoId parameter correctly`() =
        runTest {
            val sudoId = "mockSudoId"
            val input = ListEmailAliasesInput(sudoId = sudoId)
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailAliases(input)
                }
            deferredResult.start()

            val listAliasesResult = deferredResult.await()

            listAliasesResult shouldNotBe null
            when (listAliasesResult) {
                is ListAPIResult.Success -> {
                    listAliasesResult.result.items.size shouldBe 1
                    listAliasesResult.result.nextToken shouldBe null
                    listAliasesResult.result.items[0] shouldBe EmailAliasTransformer.unsealedListEntityToApi(unsealedEmailAlias)
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailAliasesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.sudoId shouldBe sudoId
                    useCaseInput.filter shouldBe null
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe null
                },
            )
        }

    @Test
    fun `listEmailAliases() should properly handle filter parameter`() =
        runTest {
            val filter =
                EmailAliasFilterInput(
                    status = EqualStatusFilter(EmailAliasStatus.DISABLED),
                    realAddressType = NotEqualRealAddressTypeFilter(EmailAliasRealAddressType.EXTERNAL),
                )
            val input = ListEmailAliasesInput(filter = filter)
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailAliases(input)
                }
            deferredResult.start()

            val listAliasesResult = deferredResult.await()

            listAliasesResult shouldNotBe null
            when (listAliasesResult) {
                is ListAPIResult.Success -> {
                    listAliasesResult.result.items.size shouldBe 1
                    listAliasesResult.result.nextToken shouldBe null
                    listAliasesResult.result.items[0] shouldBe EmailAliasTransformer.unsealedListEntityToApi(unsealedEmailAlias)
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailAliasesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.sudoId shouldBe null
                    useCaseInput.filter shouldBe filter
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe null
                },
            )
        }

    @Test
    fun `listEmailAliases() should properly handle limit parameter`() =
        runTest {
            val limit = 2
            val input = ListEmailAliasesInput(limit = limit)
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailAliases(input)
                }
            deferredResult.start()

            val listAliasesResult = deferredResult.await()

            listAliasesResult shouldNotBe null
            when (listAliasesResult) {
                is ListAPIResult.Success -> {
                    listAliasesResult.result.items.size shouldBe 1
                    listAliasesResult.result.nextToken shouldBe null
                    listAliasesResult.result.items[0] shouldBe EmailAliasTransformer.unsealedListEntityToApi(unsealedEmailAlias)
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailAliasesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.sudoId shouldBe null
                    useCaseInput.filter shouldBe null
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe limit
                },
            )
        }

    @Test
    fun `listEmailAliases() should properly handle nextToken parameter`() =
        runTest {
            val nextToken = "mockToken"
            val input = ListEmailAliasesInput(nextToken = nextToken)
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailAliases(input)
                }
            deferredResult.start()

            val listAliasesResult = deferredResult.await()

            listAliasesResult shouldNotBe null
            when (listAliasesResult) {
                is ListAPIResult.Success -> {
                    listAliasesResult.result.items.size shouldBe 1
                    listAliasesResult.result.nextToken shouldBe null
                    listAliasesResult.result.items[0] shouldBe EmailAliasTransformer.unsealedListEntityToApi(unsealedEmailAlias)
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailAliasesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.sudoId shouldBe null
                    useCaseInput.filter shouldBe null
                    useCaseInput.nextToken shouldBe nextToken
                    useCaseInput.limit shouldBe null
                },
            )
        }

    @Test
    fun `listEmailAliases() should properly handle all parameters`() =
        runTest {
            val sudoId = "mockSudoId"
            val filter =
                EmailAliasFilterInput(
                    status = EqualStatusFilter(EmailAliasStatus.DISABLED),
                    realAddressType = NotEqualRealAddressTypeFilter(EmailAliasRealAddressType.EXTERNAL),
                )
            val limit = 2
            val nextToken = "mockToken"
            val input = ListEmailAliasesInput(sudoId, limit, nextToken, filter)

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailAliases(input)
                }
            deferredResult.start()

            val listAliasesResult = deferredResult.await()

            listAliasesResult shouldNotBe null
            when (listAliasesResult) {
                is ListAPIResult.Success -> {
                    listAliasesResult.result.items.size shouldBe 1
                    listAliasesResult.result.nextToken shouldBe null
                    listAliasesResult.result.items[0] shouldBe EmailAliasTransformer.unsealedListEntityToApi(unsealedEmailAlias)
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailAliasesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.sudoId shouldBe sudoId
                    useCaseInput.filter shouldBe filter
                    useCaseInput.nextToken shouldBe nextToken
                    useCaseInput.limit shouldBe limit
                },
            )
        }

    @Test
    fun `listEmailAliases() should return empty list when no aliases returned`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute(any()) } doReturn listSuccessResultWithEmptyList
            }
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailAliases(input)
                }
            deferredResult.start()

            val listAliasesResult = deferredResult.await()

            listAliasesResult shouldNotBe null
            when (listAliasesResult) {
                is ListAPIResult.Success -> {
                    listAliasesResult.result.items.size shouldBe 0
                    listAliasesResult.result.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailAliasesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.sudoId shouldBe null
                    useCaseInput.filter shouldBe null
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe null
                },
            )
        }

    @Test
    fun `listEmailAliases should return nextToken when required`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute(any()) } doReturn listSuccessResultWithNextToken
            }
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailAliases(input)
                }
            deferredResult.start()

            val listAliasesResult = deferredResult.await()

            listAliasesResult shouldNotBe null
            when (listAliasesResult) {
                is ListAPIResult.Success -> {
                    listAliasesResult.result.items.size shouldBe 1
                    listAliasesResult.result.nextToken shouldBe resultNextToken
                    listAliasesResult.result.items[0] shouldBe EmailAliasTransformer.unsealedListEntityToApi(unsealedEmailAlias)
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailAliasesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.sudoId shouldBe null
                    useCaseInput.filter shouldBe null
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe null
                },
            )
        }

    @Test
    fun `listEmailAliases() should return partial result when use case returns partial result`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute(any()) } doReturn listPartialResult
            }
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailAliases(input)
                }
            deferredResult.start()

            val listAliasesResult = deferredResult.await()

            listAliasesResult shouldNotBe null
            when (listAliasesResult) {
                is ListAPIResult.Partial -> {
                    listAliasesResult.result.items.size shouldBe 1
                    listAliasesResult.result.nextToken shouldBe null
                    listAliasesResult.result.items[0] shouldBe EmailAliasTransformer.unsealedListEntityToApi(unsealedEmailAlias)
                    listAliasesResult.result.failed.size shouldBe 1
                    listAliasesResult.result.failed[0].partial shouldBe EmailAliasTransformer.partialListEntityToApi(partialEmailAlias)
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockUseCaseFactory).createListEmailAliasesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.sudoId shouldBe null
                    useCaseInput.filter shouldBe null
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe null
                },
            )
        }

    @Test
    fun `listEmailAliases should throw when use case throws error`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute(any()) } doThrow SudoEmailClient.EmailAliasException.FailedException("Mock exception")
            }
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAliasException.FailedException> {
                        client.listEmailAliases(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createListEmailAliasesUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.sudoId shouldBe null
                    useCaseInput.filter shouldBe null
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe null
                },
            )
        }
}
