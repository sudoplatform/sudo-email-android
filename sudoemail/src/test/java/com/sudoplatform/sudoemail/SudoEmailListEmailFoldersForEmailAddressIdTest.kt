/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.apollographql.apollo3.api.Optional
import com.sudoplatform.sudoemail.graphql.ListEmailFoldersForEmailAddressIdQuery
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.inputs.ListEmailFoldersForEmailAddressIdInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
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
import com.sudoplatform.sudoemail.graphql.type.ListEmailFoldersForEmailAddressIdInput as ListEmailFoldersForEmailAddressIdRequest

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
        JSONObject(
            """
                {
                    'listEmailFoldersForEmailAddressId': {
                        'items': [{
                            '__typename': 'EmailFolder',
                            'id': 'folderId',
                            'owner': 'owner',
                            'owners': [{
                                '__typename': 'Owner',
                                'id': 'ownerId',
                                'issuer': 'issuer'
                            }],
                            'version': '1',
                            'createdAtEpochMs': 1.0,
                            'updatedAtEpochMs': 1.0,
                            'emailAddressId': 'emailAddressId',
                            'folderName': folderName,
                            'size': 0.0,
                            'unseenCount': 0.0,
                            'ttl': 1.0
                        }],
                        'nextToken': null
                    }
                }
            """.trimIndent(),
        )
    }

    private val queryResponseWithNextToken by before {
        JSONObject(
            """
                {
                    'listEmailFoldersForEmailAddressId': {
                        'items': [{
                            '__typename': 'EmailFolder',
                            'id': 'folderId',
                            'owner': 'owner',
                            'owners': [{
                                '__typename': 'Owner',
                                'id': 'ownerId',
                                'issuer': 'issuer'
                            }],
                            'version': '1',
                            'createdAtEpochMs': 1.0,
                            'updatedAtEpochMs': 1.0,
                            'emailAddressId': 'emailAddressId',
                            'folderName': folderName,
                            'size': 0.0,
                            'unseenCount': 0.0,
                            'ttl': 1.0
                        }],
                        'nextToken': 'dummyNextToken'
                    }
                }
            """.trimIndent(),
        )
    }

    private val queryResponseWithEmptyList by before {
        JSONObject(
            """
                {
                    'listEmailFoldersForEmailAddressId': {
                        'items': []
                    }
                }
            """.trimIndent(),
        )
    }

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            on {
                query<String>(
                    argThat { this.query.equals(ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(queryResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
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
            GraphQLClient(mockApiCategory),
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
            mockApiCategory,
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

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT
                val input = it.variables["input"] as ListEmailFoldersForEmailAddressIdRequest
                input.emailAddressId shouldBe "emailAddressId"
                input.limit shouldBe Optional.Present(10)
                input.nextToken shouldBe Optional.absent()
            },
            any(),
            any(),
        )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should return results when populating nextToken`() = runTest {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(queryResponseWithNextToken.toString(), null),
                )
                mock<GraphQLOperation<String>>()
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

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT
                val queryInput = it.variables["input"] as ListEmailFoldersForEmailAddressIdRequest
                queryInput.emailAddressId shouldBe "emailAddressId"
                queryInput.limit shouldBe Optional.Present(1)
                queryInput.nextToken shouldBe Optional.Present("dummyNextToken")
            },
            any(),
            any(),
        )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should return empty list output when query result data is empty`() = runTest {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(queryResponseWithEmptyList.toString(), null),
                )
                mock<GraphQLOperation<String>>()
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

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT
                val queryInput = it.variables["input"] as ListEmailFoldersForEmailAddressIdRequest
                queryInput.emailAddressId shouldBe "emailAddressId"
                queryInput.limit shouldBe Optional.Present(10)
                queryInput.nextToken shouldBe Optional.absent()
            },
            any(),
            any(),
        )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should return empty list output when query response is null`() = runTest {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            }.thenAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(null, null),
                )
                mock<GraphQLOperation<String>>()
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

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
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
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            }.thenAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(null, listOf(testError)),
                )
                mock<GraphQLOperation<String>>()
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
                client.listEmailFoldersForEmailAddressId(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT
                val input = it.variables["input"] as ListEmailFoldersForEmailAddressIdRequest
                input.emailAddressId shouldBe "emailAddressId"
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
            any(),
            any(),
        )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should throw when unknown error occurs`() = runTest {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT) },
                    any(),
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

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT
                val input = it.variables["input"] as ListEmailFoldersForEmailAddressIdRequest
                input.emailAddressId shouldBe "emailAddressId"
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
            any(),
            any(),
        )
    }

    @Test
    fun `listEmailFoldersForEmailAddressId() should not block coroutine cancellation exception`() = runTest {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doThrow
                CancellationException("Mock Cancellation Exception")
        }

        shouldThrow<CancellationException> {
            client.listEmailFoldersForEmailAddressId(input)
        }

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT
                val input = it.variables["input"] as ListEmailFoldersForEmailAddressIdRequest
                input.emailAddressId shouldBe "emailAddressId"
                input.limit shouldBe Optional.Present(10)
                input.nextToken shouldBe Optional.absent()
            },
            any(),
            any(),
        )
    }
}
