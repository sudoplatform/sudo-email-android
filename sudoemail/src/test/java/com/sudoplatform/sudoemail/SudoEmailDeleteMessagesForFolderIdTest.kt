/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amplifyframework.api.graphql.GraphQLResponse
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.inputs.DeleteMessagesForFolderIdInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
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
    private val mockFolderId = "mockFolderId"
    private val mockEmailAddressId = "mockEmailAddressId"
    private val mutationResponse by before {
        DataFactory.deleteEmailMessagesForFolderIdMutationResponse(mockFolderId)
    }

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                deleteEmailMessagesByFolderIdMutation(
                    any(),
                )
            } doAnswer {
                mutationResponse
            }
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val mockServiceKeyManager by before {
        DefaultServiceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
    }

    private val mockS3Client by before {
        mock<S3Client>()
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        DefaultSealingService(
            mockServiceKeyManager,
            mockLogger,
        )
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>()
    }

    private val client by before {
        DefaultSudoEmailClient(
            context,
            mockApiClient,
            mockUserClient,
            mockLogger,
            mockServiceKeyManager,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
            "region",
            "identityBucket",
            "transientBucket",
            null,
            mockS3Client,
            mockS3Client,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockUserClient,
            mockKeyManager,
            mockApiClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `deleteMessagesForFolderId() should return folder id on success`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteMessagesForFolderId(
                        DeleteMessagesForFolderIdInput(
                            emailAddressId = mockEmailAddressId,
                            emailFolderId = mockFolderId,
                        ),
                    )
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result shouldBe mockFolderId

            verify(mockApiClient).deleteEmailMessagesByFolderIdMutation(
                check { input ->
                    input.folderId shouldBe mockFolderId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.hardDelete shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `deleteMessagesForFolderId() should pass hardDelete parameter properly`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteMessagesForFolderId(
                        DeleteMessagesForFolderIdInput(
                            emailAddressId = mockEmailAddressId,
                            emailFolderId = mockFolderId,
                            hardDelete = false,
                        ),
                    )
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result shouldBe mockFolderId

            verify(mockApiClient).deleteEmailMessagesByFolderIdMutation(
                check { input ->
                    input.folderId shouldBe mockFolderId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.hardDelete shouldBe Optional.present(false)
                },
            )
        }

    @Test
    fun `deleteMessagesForFolderId() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "EmailAddressNotFound"),
                )
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesByFolderIdMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
                        client.deleteMessagesForFolderId(
                            DeleteMessagesForFolderIdInput(
                                emailAddressId = mockEmailAddressId,
                                emailFolderId = mockFolderId,
                            ),
                        )
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deleteEmailMessagesByFolderIdMutation(
                check { input ->
                    input.folderId shouldBe mockFolderId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.hardDelete shouldBe Optional.absent()
                },
            )
        }
}
