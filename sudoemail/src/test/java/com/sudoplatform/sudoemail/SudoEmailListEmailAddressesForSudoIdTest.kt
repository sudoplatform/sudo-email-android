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
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.fragment.SealedAttribute
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesForSudoIdInput
import com.sudoplatform.sudoemail.types.transformers.Unsealer
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
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
 * Test the correct operation of [SudoEmailClient.listEmailAddressesForSudoId]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListEmailAddressesForSudoIdTest : BaseTests() {

    private val input by before {
        ListEmailAddressesForSudoIdInput(
            "sudoId",
        )
    }

    private val queryResponse by before {
        DataFactory.listEmailAddressesForSudoIdQueryResponse(
            listOf(
                DataFactory.EmailAddressQueryResponseData(),
            ),
        )
    }

    private val queryResponseWithAlias by before {
        DataFactory.listEmailAddressesForSudoIdQueryResponse(
            listOf(
                DataFactory.EmailAddressQueryResponseData(
                    DataFactory.getEmailAddressWithoutFolder(
                        alias = EmailAddressWithoutFolders.Alias(
                            "SealedAttribute",
                            SealedAttribute(
                                algorithm = "algorithm",
                                keyId = "keyId",
                                plainTextType = "string",
                                base64EncodedSealedData = "alias",
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private val queryResponseWithNextToken by before {
        DataFactory.listEmailAddressesForSudoIdQueryResponse(
            listOf(DataFactory.EmailAddressQueryResponseData()),
            nextToken = "dummyNextToken",
        )
    }

    private val queryResponseWithEmptyList by before {
        DataFactory.listEmailAddressesForSudoIdQueryResponse(emptyList())
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
                listEmailAddressesForSudoIdQuery(
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
    fun `listEmailAddressesForSudoId() should return results when no error present`() = runTest {
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listEmailAddressesForSudoId(input)
        }
        deferredResult.start()
        val listEmailAddresses = deferredResult.await()

        listEmailAddresses shouldNotBe null
        when (listEmailAddresses) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.size shouldBe 1
                listEmailAddresses.result.nextToken shouldBe null

                with(listEmailAddresses.result.items[0]) {
                    id shouldBe "emailAddressId"
                    owner shouldBe "owner"
                    owners.first().id shouldBe "ownerId"
                    owners.first().issuer shouldBe "issuer"
                    emailAddress shouldBe "example@sudoplatform.com"
                    size shouldBe 0.0
                    numberOfEmailMessages shouldBe 0
                    version shouldBe 1
                    createdAt shouldBe Date(1L)
                    updatedAt shouldBe Date(1L)
                    lastReceivedAt shouldBe Date(1L)
                    folders.size shouldBe 1
                    with(folders[0]) {
                        id shouldBe "folderId"
                        owner shouldBe "owner"
                        owners.first().id shouldBe "ownerId"
                        owners.first().issuer shouldBe "issuer"
                        emailAddressId shouldBe "emailAddressId"
                        folderName shouldBe "folderName"
                        size shouldBe 0.0
                        unseenCount shouldBe 0.0
                        version shouldBe 1
                        createdAt shouldBe Date(1L)
                        updatedAt shouldBe Date(1L)
                    }
                }
            }
            else -> { fail("Unexpected ListAPIResult") }
        }

        verify(mockApiClient).listEmailAddressesForSudoIdQuery(
            check { input ->
                input.sudoId shouldBe "sudoId"
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }

    @Test
    fun `listEmailAddressesForSudoId() should return results when populating nextToken`() = runTest {
        mockApiClient.stub {
            onBlocking {
                listEmailAddressesForSudoIdQuery(
                    any(),
                )
            } doAnswer {
                queryResponseWithNextToken
            }
        }

        val input = ListEmailAddressesForSudoIdInput("sudoId", 1, "dummyNextToken")
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listEmailAddressesForSudoId(input)
        }
        deferredResult.start()
        val listEmailAddresses = deferredResult.await()

        listEmailAddresses shouldNotBe null
        when (listEmailAddresses) {
            is ListAPIResult.Success -> {
                listEmailAddresses.result.items.size shouldBe 1
                listEmailAddresses.result.nextToken shouldBe "dummyNextToken"

                with(listEmailAddresses.result.items[0]) {
                    id shouldBe "emailAddressId"
                    owner shouldBe "owner"
                    owners.size shouldBe 1
                    emailAddress shouldBe "example@sudoplatform.com"
                    size shouldBe 0.0
                    numberOfEmailMessages shouldBe 0
                    version shouldBe 1
                    createdAt shouldBe Date(1L)
                    updatedAt shouldBe Date(1L)
                    lastReceivedAt shouldBe Date(1L)
                }
            }
            else -> { fail("Unexpected ListAPIResult") }
        }

        verify(mockApiClient).listEmailAddressesForSudoIdQuery(
            check { input ->
                input.sudoId shouldBe "sudoId"
                input.limit shouldBe Optional.present(1)
                input.nextToken shouldBe Optional.present("dummyNextToken")
            },
        )
    }

    @Test
    fun `listEmailAddressesForSudoId() should return empty list output when query result data is empty`() = runTest {
        mockApiClient.stub {
            onBlocking {
                listEmailAddressesForSudoIdQuery(
                    any(),
                )
            } doAnswer {
                queryResponseWithEmptyList
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
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
            else -> { fail("Unexpected ListAPIResult") }
        }

        verify(mockApiClient).listEmailAddressesForSudoIdQuery(
            check { input ->
                input.sudoId shouldBe "sudoId"
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }

    @Test
    fun `listEmailAddressesForSudoId() should return empty list output when query response is null`() = runTest {
        mockApiClient.stub {
            onBlocking {
                listEmailAddressesForSudoIdQuery(
                    any(),
                )
            }.thenAnswer {
                GraphQLResponse(null, null)
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
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
            else -> { fail("Unexpected ListAPIResult") }
        }

        verify(mockApiClient).listEmailAddressesForSudoIdQuery(
            check { input ->
                input.sudoId shouldBe "sudoId"
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }

    @Test
    fun `listEmailAddressesForSudoId() should return partial results when unsealing fails`() = runTest {
        mockKeyManager.stub {
            on { decryptWithPrivateKey(anyString(), any(), any()) } doThrow KeyManagerException("KeyManagerException")
        }

        mockApiClient.stub {
            onBlocking {
                listEmailAddressesForSudoIdQuery(
                    any(),
                )
            } doAnswer {
                queryResponseWithAlias
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listEmailAddressesForSudoId(input)
        }
        deferredResult.start()
        val listEmailAddresses = deferredResult.await()

        listEmailAddresses shouldNotBe null
        when (listEmailAddresses) {
            is ListAPIResult.Partial -> {
                listEmailAddresses.result.items.size shouldBe 0
                listEmailAddresses.result.failed.size shouldBe 1
                listEmailAddresses.result.nextToken shouldBe null

                with(listEmailAddresses.result.failed[0].partial) {
                    id shouldBe "emailAddressId"
                    owner shouldBe "owner"
                    owners.first().id shouldBe "ownerId"
                    owners.first().issuer shouldBe "issuer"
                    emailAddress shouldBe "example@sudoplatform.com"
                    size shouldBe 0.0
                    version shouldBe 1
                    createdAt shouldBe Date(1L)
                    updatedAt shouldBe Date(1L)
                    lastReceivedAt shouldBe Date(1L)
                    folders.size shouldBe 1
                    with(folders[0]) {
                        id shouldBe "folderId"
                        owner shouldBe "owner"
                        owners.first().id shouldBe "ownerId"
                        owners.first().issuer shouldBe "issuer"
                        emailAddressId shouldBe "emailAddressId"
                        folderName shouldBe "folderName"
                        size shouldBe 0.0
                        unseenCount shouldBe 0.0
                        version shouldBe 1
                        createdAt shouldBe Date(1L)
                        updatedAt shouldBe Date(1L)
                    }
                }
            }
            else -> { fail("Unexpected ListAPIResult") }
        }

        verify(mockApiClient).listEmailAddressesForSudoIdQuery(
            check { input ->
                input.sudoId shouldBe "sudoId"
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }

    @Test
    fun `listEmailAddressesForSudoId() should throw when unsealing fails`() = runTest {
        mockApiClient.stub {
            onBlocking {
                listEmailAddressesForSudoIdQuery(
                    any(),
                )
            } doThrow
                Unsealer.UnsealerException.SealedDataTooShortException("Mock Unsealer Exception")
        }

        shouldThrow<SudoEmailClient.EmailAddressException.UnsealingException> {
            client.listEmailAddressesForSudoId(input)
        }

        verify(mockApiClient).listEmailAddressesForSudoIdQuery(
            check { input ->
                input.sudoId shouldBe "sudoId"
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }

    @Test
    fun `listEmailAddressesForSudoId() should throw when http error occurs`() = runTest {
        val testError = GraphQLResponse.Error(
            "mock",
            null,
            null,
            mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
        )
        mockApiClient.stub {
            onBlocking {
                listEmailAddressesForSudoIdQuery(
                    any(),
                )
            }.thenAnswer {
                GraphQLResponse(null, listOf(testError))
            }
        }
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                client.listEmailAddressesForSudoId(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiClient).listEmailAddressesForSudoIdQuery(
            check { input ->
                input.sudoId shouldBe "sudoId"
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }

    @Test
    fun `listEmailAddressesForSudoId() should throw when unknown error occurs`() = runTest {
        mockApiClient.stub {
            onBlocking {
                listEmailAddressesForSudoIdQuery(
                    any(),
                )
            } doThrow
                RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.UnknownException> {
                client.listEmailAddressesForSudoId(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiClient).listEmailAddressesForSudoIdQuery(
            check { input ->
                input.sudoId shouldBe "sudoId"
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }

    @Test
    fun `listEmailAddressesForSudoId() should not block coroutine cancellation exception`() = runTest {
        mockApiClient.stub {
            onBlocking {
                listEmailAddressesForSudoIdQuery(
                    any(),
                )
            } doThrow
                CancellationException("Mock Cancellation Exception")
        }

        shouldThrow<CancellationException> {
            client.listEmailAddressesForSudoId(input)
        }

        verify(mockApiClient).listEmailAddressesForSudoIdQuery(
            check { input ->
                input.sudoId shouldBe "sudoId"
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }
}
