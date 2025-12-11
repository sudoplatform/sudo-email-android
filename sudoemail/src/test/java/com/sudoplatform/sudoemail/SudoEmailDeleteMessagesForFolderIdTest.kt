/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.data.emailMessage.GraphQLEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.inputs.DeleteMessagesForFolderIdInput
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
 * Test the correct operation of [SudoEmailClient.deleteMessagesForFolderId]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailDeleteMessagesForFolderIdTest : BaseTests() {
    private val folderId = "folder-id-123"
    private val emailAddressId = "email-address-id-456"

    private val mockEmailMessageService by before {
        mock<GraphQLEmailMessageService>().stub {
            onBlocking {
                deleteForFolderId(any())
            } doReturn folderId
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>()
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
            emailMessageService = mockEmailMessageService,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockEmailMessageService,
            mockUseCaseFactory,
        )
    }

    @Test
    fun `deleteMessagesForFolderId() should return folder ID when deletion is successful`() =
        runTest {
            val input =
                DeleteMessagesForFolderIdInput(
                    emailAddressId = emailAddressId,
                    emailFolderId = folderId,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteMessagesForFolderId(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result shouldBe folderId

            verify(mockEmailMessageService).deleteForFolderId(
                check { request ->
                    request.emailAddressId shouldBe emailAddressId
                    request.emailFolderId shouldBe folderId
                    request.hardDelete shouldBe null
                },
            )
        }

    @Test
    fun `deleteMessagesForFolderId() should pass hardDelete as true when specified`() =
        runTest {
            val input =
                DeleteMessagesForFolderIdInput(
                    emailAddressId = emailAddressId,
                    emailFolderId = folderId,
                    hardDelete = true,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteMessagesForFolderId(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result shouldBe folderId

            verify(mockEmailMessageService).deleteForFolderId(
                check { request ->
                    request.emailAddressId shouldBe emailAddressId
                    request.emailFolderId shouldBe folderId
                    request.hardDelete shouldBe true
                },
            )
        }

    @Test
    fun `deleteMessagesForFolderId() should pass hardDelete as false when specified`() =
        runTest {
            val input =
                DeleteMessagesForFolderIdInput(
                    emailAddressId = emailAddressId,
                    emailFolderId = folderId,
                    hardDelete = false,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteMessagesForFolderId(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result shouldBe folderId

            verify(mockEmailMessageService).deleteForFolderId(
                check { request ->
                    request.emailAddressId shouldBe emailAddressId
                    request.emailFolderId shouldBe folderId
                    request.hardDelete shouldBe false
                },
            )
        }

    @Test
    fun `deleteMessagesForFolderId() should throw when service throws EmailFolderException`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking {
                    deleteForFolderId(any())
                } doThrow SudoEmailClient.EmailFolderException.FailedException("Failed to delete messages")
            }

            val input =
                DeleteMessagesForFolderIdInput(
                    emailAddressId = emailAddressId,
                    emailFolderId = folderId,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
                        client.deleteMessagesForFolderId(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockEmailMessageService).deleteForFolderId(any())
        }

    @Test
    fun `deleteMessagesForFolderId() should throw when service throws AuthenticationException`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking {
                    deleteForFolderId(any())
                } doThrow SudoEmailClient.EmailFolderException.AuthenticationException("Not authenticated")
            }

            val input =
                DeleteMessagesForFolderIdInput(
                    emailAddressId = emailAddressId,
                    emailFolderId = folderId,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailFolderException.AuthenticationException> {
                        client.deleteMessagesForFolderId(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockEmailMessageService).deleteForFolderId(any())
        }

    @Test
    fun `deleteMessagesForFolderId() should handle null hardDelete parameter`() =
        runTest {
            val input =
                DeleteMessagesForFolderIdInput(
                    emailAddressId = emailAddressId,
                    emailFolderId = folderId,
                    hardDelete = null,
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteMessagesForFolderId(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe folderId

            verify(mockEmailMessageService).deleteForFolderId(
                check { request ->
                    request.hardDelete shouldBe null
                },
            )
        }
}
