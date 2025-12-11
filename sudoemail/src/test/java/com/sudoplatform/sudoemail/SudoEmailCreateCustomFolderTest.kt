/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder.CreateCustomEmailFolderUseCase
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.CreateCustomEmailFolderInput
import io.kotlintest.shouldBe
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
 * Test correct operation of [SudoEmailClient.createCustomEmailFolder]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailCreateCustomFolderTest : BaseTests() {
    private val unsealedEmailFolder = EntityDataFactory.getUnsealedEmailFolderEntity(customFolderName = mockCustomFolderName)
    private val input by before {
        CreateCustomEmailFolderInput(
            mockEmailAddressId,
            mockCustomFolderName,
        )
    }

    private val mockUseCase by before {
        mock<CreateCustomEmailFolderUseCase>().stub {
            onBlocking { execute(any()) } doReturn unsealedEmailFolder
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createCustomEmailFolderUseCase() } doReturn mockUseCase
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
    fun `createCustomEmailFolder() should return newly created folder`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.createCustomEmailFolder(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result.id shouldBe mockFolderId
            result.customFolderName shouldBe mockCustomFolderName

            verify(mockUseCaseFactory).createCustomEmailFolderUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.customFolderName shouldBe mockCustomFolderName
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `createCustomEmailFolder() should throw an error if use case throws error`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(any())
                } doThrow SudoEmailClient.EmailFolderException.FailedException("Mock Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
                        client.createCustomEmailFolder(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createCustomEmailFolderUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.customFolderName shouldBe mockCustomFolderName
                    useCaseInput.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }
}
