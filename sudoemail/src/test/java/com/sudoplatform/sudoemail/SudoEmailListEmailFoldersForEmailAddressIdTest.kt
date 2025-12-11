/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.UnsealedEmailFolderEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder.ListEmailFoldersForEmailAddressIdUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.ListOutput
import com.sudoplatform.sudoemail.types.inputs.ListEmailFoldersForEmailAddressIdInput
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
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.listEmailFoldersForEmailAddressId]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListEmailFoldersForEmailAddressIdTest : BaseTests() {
    private val resultNextToken = "resultNextToken"
    private val unsealedEmailFolder = EntityDataFactory.getUnsealedEmailFolderEntity()
    private val input by before {
        ListEmailFoldersForEmailAddressIdInput(
            mockEmailAddressId,
        )
    }

    private val listResult by before {
        ListOutput(
            items = listOf(unsealedEmailFolder),
            nextToken = null,
        )
    }

    private val listResultWithNextToken by before {
        ListOutput(
            items = listOf(unsealedEmailFolder),
            nextToken = resultNextToken,
        )
    }

    private val listResultWithEmptyList by before {
        ListOutput<UnsealedEmailFolderEntity>(
            items = emptyList(),
            nextToken = null,
        )
    }

    private val mockUseCase by before {
        mock<ListEmailFoldersForEmailAddressIdUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn listResult
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createListEmailFoldersForEmailAddressIdUseCase() } doReturn mockUseCase
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
    fun `listEmailFoldersForEmailAddressId() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailFoldersForEmailAddressId(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.items.isEmpty() shouldBe false
            result.items.size shouldBe 1
            result.nextToken shouldBe null

            with(result.items[0]) {
                id shouldBe mockFolderId
                owner shouldBe mockOwner
                owners.first().id shouldBe mockOwner
                owners.first().issuer shouldBe "issuer"
                emailAddressId shouldBe mockEmailAddressId
                folderName shouldBe "folderName"
                size shouldBe 0.0
                unseenCount shouldBe 0
                version shouldBe 1
                createdAt shouldBe Date(1L)
                updatedAt shouldBe Date(1L)
            }

            verify(mockUseCaseFactory).createListEmailFoldersForEmailAddressIdUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe SudoEmailClient.DEFAULT_EMAIL_FOLDER_LIMIT
                },
            )
        }

    @Test
    fun `listEmailFoldersForEmailAddressId() should return results when populating nextToken`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doAnswer {
                    listResultWithNextToken
                }
            }

            val input = ListEmailFoldersForEmailAddressIdInput(mockEmailAddressId, nextToken = "dummyNextToken")
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailFoldersForEmailAddressId(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.items.isEmpty() shouldBe false
            result.items.size shouldBe 1
            result.nextToken shouldBe resultNextToken

            with(result.items[0]) {
                id shouldBe mockFolderId
                owner shouldBe mockOwner
                owners.first().id shouldBe mockOwner
                owners.first().issuer shouldBe "issuer"
                emailAddressId shouldBe mockEmailAddressId
                folderName shouldBe "folderName"
                size shouldBe 0.0
                unseenCount shouldBe 0
                version shouldBe 1
                createdAt shouldBe Date(1L)
                updatedAt shouldBe Date(1L)
            }

            verify(mockUseCaseFactory).createListEmailFoldersForEmailAddressIdUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                    useCaseInput.nextToken shouldBe "dummyNextToken"
                    useCaseInput.limit shouldBe SudoEmailClient.DEFAULT_EMAIL_FOLDER_LIMIT
                },
            )
        }

    @Test
    fun `listEmailFoldersForEmailAddressId() should return empty list output when query result data is empty`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doAnswer {
                    listResultWithEmptyList
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailFoldersForEmailAddressId(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.items.isEmpty() shouldBe true
            result.items.size shouldBe 0
            result.nextToken shouldBe null

            verify(mockUseCaseFactory).createListEmailFoldersForEmailAddressIdUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe SudoEmailClient.DEFAULT_EMAIL_FOLDER_LIMIT
                },
            )
        }

    @Test
    fun `listEmailFoldersForEmailAddressId() should throw when use case error occurs`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                } doThrow
                    SudoEmailClient.EmailFolderException.FailedException("Mock Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
                        client.listEmailFoldersForEmailAddressId(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createListEmailFoldersForEmailAddressIdUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.limit shouldBe SudoEmailClient.DEFAULT_EMAIL_FOLDER_LIMIT
                },
            )
        }

    @Test
    fun `listEmailFoldersForEmailAddressId() should pass limit parameter when specified`() =
        runTest {
            val customLimit = 20
            val input =
                ListEmailFoldersForEmailAddressIdInput(
                    emailAddressId = mockEmailAddressId,
                    limit = customLimit,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailFoldersForEmailAddressId(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.items.size shouldBe 1

            verify(mockUseCaseFactory).createListEmailFoldersForEmailAddressIdUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                    useCaseInput.limit shouldBe customLimit
                    useCaseInput.nextToken shouldBe null
                },
            )
        }
}
