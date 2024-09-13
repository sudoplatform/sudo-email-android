/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudoemail.graphql.GetEmailAddressQuery
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Before
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
import java.util.Date
import java.util.UUID

/**
 * Test the correct operation of [SudoEmailClient.getDraftEmailMessage]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetDraftEmailMessageTest : BaseTests() {

    private val mockUserMetadata = listOf(
        "keyId" to "keyId",
        "algorithm" to "algorithm",
    ).toMap()

    private val mockS3ObjectMetadata = ObjectMetadata()

    private val mockDraftId = UUID.randomUUID()
    private val input by before {
        GetDraftEmailMessageInput(mockDraftId.toString(), "emailAddressId")
    }

    private val emailAddressQueryResponse by before {
        JSONObject(
            """
                {
                    'getEmailAddress': {
                        '__typename': 'EmailAddress',
                        'id': 'emailAddressId',
                        'owner': 'owner',
                        'owners': [{
                            '__typename': 'Owner',
                            'id': 'ownerId',
                            'issuer': 'issuer'
                        }],
                        'identityId': 'identityId',
                        'keyRingId': 'keyRingId',
                        'keyIds': [],
                        'version': '1',
                        'createdAtEpochMs': 1.0,
                        'updatedAtEpochMs': 1.0,
                        'lastReceivedAtEpochMs': 1.0,
                        'emailAddress': 'example@sudoplatform.com',
                        'size': 0.0,
                        'numberOfEmailMessages': 0,
                        'folders': [{
                            '__typename': 'EmailFolder',
                            'id': 'folderId',
                            'owner': 'owner',
                            'owners': [{
                                '__typename': 'Owner',
                                'id': 'ownerId',
                                'issuer': 'issuer'
                            }],
                            'version': 1,
                            'createdAtEpochMs': 1.0,
                            'updatedAtEpochMs': 1.0,
                            'emailAddressId': 'emailAddressId',
                            'folderName': 'folderName',
                            'size': 0.0,
                            'unseenCount': 0.0,
                            'ttl': 1.0
                        }]
                    }
                }
            """.trimIndent(),
        )
    }

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { decryptWithPrivateKey(anyString(), any(), any()) } doReturn ByteArray(42)
            on {
                decryptWithSymmetricKey(
                    any<ByteArray>(),
                    any<ByteArray>(),
                )
            } doReturn unsealedHeaderDetailsString.toByteArray()
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

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            on {
                query<String>(
                    argThat { this.query.equals(GetEmailAddressQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(emailAddressQueryResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
            }
        }
    }

    private val timestamp by before {
        Date()
    }

    private val mockUploadResponse by before {
        "42"
    }

    private val mockDownloadResponse by before {
        mockSeal("42").toByteArray(Charsets.UTF_8)
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking {
                upload(any(), any(), anyOrNull())
            } doReturn mockUploadResponse
            onBlocking {
                download(any())
            } doReturn mockDownloadResponse
            onBlocking {
                getObjectMetadata(any())
            } doReturn mockS3ObjectMetadata
        }
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on { unsealString(any(), any()) } doReturn unsealedHeaderDetailsString.toByteArray()
        }
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>()
    }

    private val client by before {
        DefaultSudoEmailClient(
            context,
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

    @Before
    fun init() {
        mockS3ObjectMetadata.lastModified = timestamp
        mockS3ObjectMetadata.userMetadata = mockUserMetadata
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockUserClient,
            mockKeyManager,
            mockApiCategory,
            mockS3Client,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `getDraftEmailMessage() should log and throw an error if sender address not found`() =
        runTest {
            val error = GraphQLResponse.Error(
                "mock",
                null,
                null,
                mapOf("errorType" to "AddressNotFound"),
            )
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(GetEmailAddressQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(null, listOf(error)),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                    client.getDraftEmailMessage(input)
                }
            }

            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
        }

    @Test
    fun `getDraftEmailMessage() should log and throw an error if draft message is not found`() =
        runTest {
            val error = AmazonS3Exception("Not found")
            error.errorCode = "404 Not Found"
            mockS3Client.stub {
                onBlocking {
                    download(anyString())
                } doThrow error
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                    client.getDraftEmailMessage(input)
                }
            }

            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockS3Client).getObjectMetadata(
                check {
                    it shouldContain mockDraftId.toString()
                },
            )
            verify(mockS3Client).download(
                check {
                    it shouldContain mockDraftId.toString()
                },
            )
        }

    @Test
    fun `getDraftEmailMessage() should throw error if no keyId is found in s3Object`() = runTest {
        val mockBadObjectUserMetadata = listOf("algorithm" to "algorithm").toMap()
        val mockBadObjectMetadata = ObjectMetadata()
        mockBadObjectMetadata.lastModified = timestamp
        mockBadObjectMetadata.userMetadata = mockBadObjectUserMetadata

        mockS3Client.stub {
            onBlocking {
                getObjectMetadata(any())
            } doReturn mockBadObjectMetadata
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                client.getDraftEmailMessage(input)
            }
        }

        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
        verify(mockS3Client).getObjectMetadata(
            check {
                it shouldContain mockDraftId.toString()
            },
        )
    }

    @Test
    fun `getDraftEmailMessage() should return proper data if no errors`() = runTest {
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.getDraftEmailMessage(input)
        }

        deferredResult.start()
        val result = deferredResult.await()

        result.id shouldBe mockDraftId.toString()
        result.emailAddressId shouldBe "emailAddressId"
        result.updatedAt shouldBe timestamp

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
        verify(mockS3Client).getObjectMetadata(
            check {
                it shouldContain mockDraftId.toString()
            },
        )
        verify(mockS3Client).download(
            check {
                it shouldContain mockDraftId.toString()
            },
        )
        verify(mockSealingService).unsealString(anyString(), any())
    }
}
