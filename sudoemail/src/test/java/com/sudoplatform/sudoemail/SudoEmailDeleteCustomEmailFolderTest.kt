/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder.DeleteCustomEmailFolderUseCase
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.DeleteCustomEmailFolderInput
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
 * Test correct operation of [SudoEmailClient.deleteCustomEmailFolder]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailDeleteCustomEmailFolderTest : BaseTests() {
    private val unsealedEmailFolder = EntityDataFactory.getUnsealedEmailFolderEntity(customFolderName = mockCustomFolderName)
    private val input by before {
        DeleteCustomEmailFolderInput(
            mockCustomFolderId,
            mockEmailAddressId,
        )
    }

    private val mockUseCase by before {
        mock<DeleteCustomEmailFolderUseCase>().stub {
            onBlocking { execute(any()) } doReturn unsealedEmailFolder
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createDeleteCustomEmailFolderUseCase() } doReturn mockUseCase
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>()
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
        verifyNoMoreInteractions(
            mockUserClient,
            mockKeyManager,
            mockServiceKeyManager,
            mockApiClient,
            mockS3Client,
            mockUseCaseFactory,
            mockUseCase,
        )
    }

    @Test
    fun `deleteCustomEmailFolder() should throw an error if graphQl mutation fails`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                } doThrow SudoEmailClient.EmailFolderException.FailedException("Mock Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
                        client.deleteCustomEmailFolder(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createDeleteCustomEmailFolderUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailFolderId shouldBe mockCustomFolderId
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `deleteCustomEmailFolder() should return deleted folder`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteCustomEmailFolder(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result!!.id shouldBe mockFolderId
            result.customFolderName shouldBe mockCustomFolderName

            verify(mockUseCaseFactory).createDeleteCustomEmailFolderUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailFolderId shouldBe mockCustomFolderId
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `deleteCustomEmailFolder() should return null if folder not found`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                } doAnswer {
                    null
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteCustomEmailFolder(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe null

            verify(mockUseCaseFactory).createDeleteCustomEmailFolderUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailFolderId shouldBe mockCustomFolderId
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }
}
