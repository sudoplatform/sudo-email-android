/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amplifyframework.api.graphql.GraphQLResponse
import com.apollographql.apollo3.api.Optional
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddress
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.inputs.ListEmailFoldersForEmailAddressIdInput
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
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.util.Date
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [SudoEmailClient.listEmailFoldersForEmailAddressId]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListEmailFoldersForEmailAddressIdTest : BaseTests() {

    private val input by before {
        ListEmailFoldersForEmailAddressIdInput(
            "emailAddressId",
        )
    }

    private val queryResponse by before {
        DataFactory.listEmailFoldersForEmailAddressIdQueryResponse(
            listOf(
                EmailAddress.Folder(
                    "__typename",
                    DataFactory.getEmailFolder(),
                ),
            ),
        )
    }

    private val queryResponseWithNextToken by before {
        DataFactory.listEmailFoldersForEmailAddressIdQueryResponse(
            listOf(
                EmailAddress.Folder(
                    "__typename",
                    DataFactory.getEmailFolder(),
                ),
            ),
            "dummyNextToken",
        )
    }

    private val queryResponseWithEmptyList by before {
        DataFactory.listEmailFoldersForEmailAddressIdQueryResponse(emptyList())
    }

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                listEmailFoldersForEmailAddressIdQuery(
                    any(),
                )
            } doAnswer {
                queryResponse
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
        mock<S3Client>().stub {
            onBlocking { upload(any(), anyString(), anyOrNull()) } doReturn "42"
        }
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
            mockContext,
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
            mockContext,
            mockUserClient,
            mockKeyManager,
            mockApiClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should return results when no error present`() = runTest {
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listEmailFoldersForEmailAddressId(input)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldNotBe null
        result.items.isEmpty() shouldBe false
        result.items.size shouldBe 1
        result.nextToken shouldBe null

        with(result.items[0]) {
            id shouldBe "folderId"
            owner shouldBe "owner"
            owners.first().id shouldBe "ownerId"
            owners.first().issuer shouldBe "issuer"
            emailAddressId shouldBe "emailAddressId"
            folderName shouldBe "folderName"
            size shouldBe 0.0
            unseenCount shouldBe 0
            version shouldBe 1
            createdAt shouldBe Date(1L)
            updatedAt shouldBe Date(1L)
        }

        verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(
            check { input ->
                input.emailAddressId shouldBe "emailAddressId"
                input.limit shouldBe Optional.Present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should return results when populating nextToken`() = runTest {
        mockApiClient.stub {
            onBlocking {
                listEmailFoldersForEmailAddressIdQuery(
                    any(),
                )
            } doAnswer {
                queryResponseWithNextToken
            }
        }

        val input = ListEmailFoldersForEmailAddressIdInput("emailAddressId", 1, "dummyNextToken")
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listEmailFoldersForEmailAddressId(input)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldNotBe null
        result.items.isEmpty() shouldBe false
        result.items.size shouldBe 1
        result.nextToken shouldBe "dummyNextToken"

        with(result.items[0]) {
            id shouldBe "folderId"
            owner shouldBe "owner"
            owners.first().id shouldBe "ownerId"
            owners.first().issuer shouldBe "issuer"
            emailAddressId shouldBe "emailAddressId"
            folderName shouldBe "folderName"
            size shouldBe 0.0
            unseenCount shouldBe 0
            version shouldBe 1
            createdAt shouldBe Date(1L)
            updatedAt shouldBe Date(1L)
        }

        verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(
            check { input ->
                input.emailAddressId shouldBe "emailAddressId"
                input.limit shouldBe Optional.Present(1)
                input.nextToken shouldBe Optional.Present("dummyNextToken")
            },
        )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should return empty list output when query result data is empty`() = runTest {
        mockApiClient.stub {
            onBlocking {
                listEmailFoldersForEmailAddressIdQuery(
                    any(),
                )
            } doAnswer {
                queryResponseWithEmptyList
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listEmailFoldersForEmailAddressId(input)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldNotBe null
        result.items.isEmpty() shouldBe true
        result.items.size shouldBe 0
        result.nextToken shouldBe null

        verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(
            check { input ->
                input.emailAddressId shouldBe "emailAddressId"
                input.limit shouldBe Optional.Present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should return empty list output when query response is null`() = runTest {
        mockApiClient.stub {
            onBlocking {
                listEmailFoldersForEmailAddressIdQuery(
                    any(),
                )
            }.thenAnswer {
                GraphQLResponse(null, null)
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listEmailFoldersForEmailAddressId(input)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldNotBe null
        result.items.isEmpty() shouldBe true
        result.items.size shouldBe 0
        result.nextToken shouldBe null

        verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(
            check { input ->
                input.emailAddressId shouldBe "emailAddressId"
                input.limit shouldBe Optional.Present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should throw when http error occurs`() = runTest {
        val testError = GraphQLResponse.Error(
            "mock",
            null,
            null,
            mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
        )
        mockApiClient.stub {
            onBlocking {
                listEmailFoldersForEmailAddressIdQuery(
                    any(),
                )
            }.thenAnswer {
                GraphQLResponse(null, listOf(testError))
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
                client.listEmailFoldersForEmailAddressId(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(
            check { input ->
                input.emailAddressId shouldBe "emailAddressId"
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should throw when unknown error occurs`() = runTest {
        mockApiClient.stub {
            onBlocking {
                listEmailFoldersForEmailAddressIdQuery(
                    any(),
                )
            } doThrow
                RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailFolderException.UnknownException> {
                client.listEmailFoldersForEmailAddressId(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(
            check { input ->
                input.emailAddressId shouldBe "emailAddressId"
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should not block coroutine cancellation exception`() = runTest {
        mockApiClient.stub {
            onBlocking {
                listEmailFoldersForEmailAddressIdQuery(
                    any(),
                )
            } doThrow
                CancellationException("Mock Cancellation Exception")
        }

        shouldThrow<CancellationException> {
            client.listEmailFoldersForEmailAddressId(input)
        }

        verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(
            check { input ->
                input.emailAddressId shouldBe "emailAddressId"
                input.limit shouldBe Optional.Present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }
}
