/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.s3.types.S3ClientListOutput
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.maps.shouldContain
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.listDraftEmailMessagesForEmailAddressId]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListDraftEmailMessagesForEmailAddressIdTest : BaseTests() {

    private val mockUserMetadata = listOf(
        "key-id" to "keyId",
        "algorithm" to "algorithm",
    ).toMap()

    private val mockUserMetadataWithLegacyKeyId = listOf(
        "keyId" to "keyId",
        "algorithm" to "algorithm",
    ).toMap()

    private val mockS3ObjectMetadata = ObjectMetadata()

    private val mockEmailAddressIdInput by before {
        "emailAddressId"
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
            } doReturn DataFactory.unsealedHeaderDetailsString.toByteArray()
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

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                getEmailAddressQuery(
                    any(),
                )
            } doAnswer {
                DataFactory.getEmailAddressQueryResponse()
            }
        }
    }

    private val timestamp by before {
        Date()
    }

    private val mockDownloadResponse by before {
        mockSeal("42").toByteArray(Charsets.UTF_8)
    }

    private val mockListObjectsResponse: List<S3ClientListOutput> by before {
        listOf(S3ClientListOutput("id1", Date()), S3ClientListOutput("id2", Date()))
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking {
                download(any())
            } doReturn mockDownloadResponse
            onBlocking {
                getObjectMetadata(any())
            } doReturn mockS3ObjectMetadata
            onBlocking {
                list(any())
            } doReturn mockListObjectsResponse
        }
    }

    private val mockS3TransientClient by before {
        mock<S3Client>().stub {
            onBlocking {
                list(
                    any(),
                )
            } doReturn emptyList()
        }
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on { unsealString(any(), any()) } doReturn DataFactory.unsealedHeaderDetailsString.toByteArray()
        }
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>()
    }

    private val mockNotificationHandler by before {
        mock<SudoEmailNotificationHandler>()
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
            mockNotificationHandler,
            mockS3TransientClient,
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
            mockApiClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
            mockNotificationHandler,
        )
    }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should return results when no error present`() =
        runTest {
            val emailAddressId = "emailAddressId"

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listDraftEmailMessagesForEmailAddressId(emailAddressId)
            }

            deferredResult.start()
            val result = deferredResult.await()

            result.size shouldBe 2
            result[0].id shouldBe "id1"
            result[0].emailAddressId shouldBe emailAddressId
            result[0].updatedAt.time shouldBe timestamp.time
            result[0].rfc822Data shouldNotBe null
            result[1].id shouldBe "id2"
            result[1].emailAddressId shouldBe emailAddressId
            result[1].updatedAt.time shouldBe timestamp.time
            result[1].rfc822Data shouldNotBe null

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockS3Client, times(2)).getObjectMetadata(anyString())
            verify(mockS3Client, times(2)).download(anyString())
            verify(mockS3Client).list(
                check {
                    it shouldContain emailAddressId
                },
            )
            verify(mockSealingService, times(2)).unsealString(anyString(), any())
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should throw an error if sender address not found`() =
        runTest {
            val error = GraphQLResponse.Error(
                "mock",
                null,
                null,
                mapOf("errorType" to "AddressNotFound"),
            )
            mockApiClient.stub {
                onBlocking {
                    getEmailAddressQuery(
                        any(),
                    )
                } doAnswer {
                    GraphQLResponse(null, listOf(error))
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                    client.listDraftEmailMessagesForEmailAddressId(mockEmailAddressIdInput)
                }
            }

            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should return an empty list if no drafts found`() =
        runTest {
            val emailAddressId = "emailAddressId"

            mockS3Client.stub {
                onBlocking {
                    list(anyString())
                } doReturn emptyList()
            }

            mockS3TransientClient.stub {
                onBlocking {
                    list(anyString())
                } doReturn emptyList()
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.listDraftEmailMessagesForEmailAddressId(emailAddressId)
            }

            deferredResult.start()
            val result = deferredResult.await()

            result.size shouldBe 0

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockS3Client).list(
                check {
                    it shouldContain emailAddressId
                },
            )
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should throw an error if draft message is not found`() =
        runTest {
            val emailAddressId = "emailAddressId"

            val error = AmazonS3Exception("Not found")
            error.errorCode = "404 Not Found"
            mockS3Client.stub {
                onBlocking {
                    download(anyString())
                } doThrow error
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                    client.listDraftEmailMessagesForEmailAddressId(emailAddressId)
                }
            }

            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockS3Client).getObjectMetadata(anyString())
            verify(mockS3Client).download(anyString())
            verify(mockS3Client).list(
                check {
                    it shouldContain emailAddressId
                },
            )
        }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should migrate any messages found in transient bucket`() = runTest {
        val emailAddressId = "emailAddressId"

        mockS3Client.stub {
            onBlocking {
                list(anyString())
            } doReturn emptyList()
            onBlocking {
                upload(any<ByteArray>(), anyString(), any())
            } doReturn mockListObjectsResponse[0].key
        }

        mockS3TransientClient.stub {
            onBlocking {
                list(
                    any(),
                )
            } doReturn mockListObjectsResponse
        }

        val timestamp = Date()
        val mockObjectMetadata = ObjectMetadata()
        mockObjectMetadata.userMetadata = mapOf(
            "keyId" to "mockKeyId",
            "algorithm" to SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
        )
        mockObjectMetadata.lastModified = timestamp
        mockS3TransientClient.stub {
            onBlocking {
                getObjectMetadata(anyString())
            } doReturn mockObjectMetadata
            onBlocking {
                download(anyString())
            } doReturn mockDownloadResponse
            onBlocking {
                delete(anyString())
            } doReturn Unit
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listDraftEmailMessagesForEmailAddressId(emailAddressId)
        }

        deferredResult.start()
        val result = deferredResult.await()

        result.size shouldBe 2
        result[0].id shouldBe "id1"
        result[0].emailAddressId shouldBe emailAddressId
        result[0].updatedAt.time shouldBe timestamp.time
        result[0].rfc822Data shouldNotBe null
        result[1].id shouldBe "id2"
        result[1].emailAddressId shouldBe emailAddressId
        result[1].updatedAt.time shouldBe timestamp.time
        result[1].rfc822Data shouldNotBe null

        verify(mockApiClient).getEmailAddressQuery(
            any(),
        )
        verify(mockS3Client).list(
            check {
                it shouldContain emailAddressId
            },
        )
        verify(mockS3TransientClient, times(mockListObjectsResponse.size)).getObjectMetadata(
            check {
                it shouldContain emailAddressId
            },
        )
        verify(mockS3TransientClient, times(mockListObjectsResponse.size)).download(
            check {
                it shouldContain emailAddressId
            },
        )
        verify(mockS3Client, times(mockListObjectsResponse.size)).upload(
            check {
                it shouldBe mockDownloadResponse
            },
            check {
                it shouldContain emailAddressId
            },
            check {
                it shouldContain Pair("key-id", "mockKeyId")
                it shouldContain Pair("algorithm", SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString())
            },
        )
        verify(mockS3TransientClient, times(mockListObjectsResponse.size)).delete(
            check {
                it shouldContain emailAddressId
            },
        )
        verify(mockSealingService, times(2)).unsealString(anyString(), any())
    }

    @Test
    fun `listDraftEmailMessagesForEmailAddressId() should migrate messages with legacy key id metadata key`() = runTest {
        val emailAddressId = "emailAddressId"
        mockS3ObjectMetadata.userMetadata = mockUserMetadataWithLegacyKeyId
        mockS3Client.stub {
            onBlocking {
                getObjectMetadata(anyString())
            } doReturn mockS3ObjectMetadata
            onBlocking {
                updateObjectMetadata(anyString(), any())
            } doReturn Unit
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listDraftEmailMessagesForEmailAddressId(emailAddressId)
        }

        deferredResult.start()
        val result = deferredResult.await()

        result.size shouldBe 2
        result[0].id shouldBe "id1"
        result[0].emailAddressId shouldBe emailAddressId
        result[0].updatedAt.time shouldBe timestamp.time
        result[0].rfc822Data shouldNotBe null
        result[1].id shouldBe "id2"
        result[1].emailAddressId shouldBe emailAddressId
        result[1].updatedAt.time shouldBe timestamp.time
        result[1].rfc822Data shouldNotBe null

        verify(mockApiClient).getEmailAddressQuery(
            any(),
        )
        verify(mockS3Client).list(
            check {
                it shouldContain emailAddressId
            },
        )
        verify(mockS3Client, times(mockListObjectsResponse.size)).getObjectMetadata(
            check {
                it shouldContain emailAddressId
            },
        )
        verify(mockS3Client, times(mockListObjectsResponse.size)).download(
            check {
                it shouldContain emailAddressId
            },
        )
        verify(mockS3Client, times(mockListObjectsResponse.size)).updateObjectMetadata(
            check {
                it shouldContain emailAddressId
            },
            check {
                it["key-id"] shouldBe "keyId"
            },
        )
        verify(mockSealingService, times(2)).unsealString(anyString(), any())
    }
}
