/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amazonaws.services.s3.model.ObjectMetadata
import com.apollographql.apollo3.api.Optional
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddress
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.s3.types.S3ClientListOutput
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
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
 * Test the correct operation of [SudoEmailClient.listDraftEmailMessageMetadata]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListDraftEmailMessageMetadataTest : BaseTests() {

    private val listEmailAddressesQueryResponse by before {
        DataFactory.listEmailAddressesQueryResponse(
            listOf(
                DataFactory.EmailAddressQueryResponseData(
                    DataFactory.getEmailAddressWithoutFolder(),
                    listOf(EmailAddress.Folder("__typename", DataFactory.getEmailFolder())),
                ),
            ),
        )
    }

    private val listEmailAddressesQueryResponseWithEmptyList by before {
        DataFactory.listEmailAddressesQueryResponse(
            emptyList(),
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
            on { encryptWithSymmetricKey(anyString(), any()) } doReturn ByteArray(42)
        }
    }

    private val mockServiceKeyManager by before {
        DefaultServiceKeyManager("keyRingServiceName", mockUserClient, mockKeyManager, mockLogger)
    }

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                listEmailAddressesQuery(
                    any(),
                )
            } doAnswer {
                listEmailAddressesQueryResponse
            }
            onBlocking {
                getEmailAddressQuery(
                    any(),
                )
            } doAnswer {
                DataFactory.getEmailAddressQueryResponse()
            }
        }
    }

    private val mockListObjectsResponse: List<S3ClientListOutput> by before {
        listOf(S3ClientListOutput("key1", Date()), S3ClientListOutput("key2", Date()))
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking {
                list(
                    any(),
                )
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
        DefaultSealingService(mockServiceKeyManager, mockLogger)
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
            mockS3TransientClient,
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
    fun `listDraftEmailMessageMetadata() should throw an error if an unknown error occurs`() = runTest {
        mockApiClient.stub {
            onBlocking {
                listEmailAddressesQuery(
                    any(),
                )
            } doThrow
                RuntimeException("Mock Runtime Exception")
        }
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.listDraftEmailMessageMetadata()
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiClient).listEmailAddressesQuery(
            check { input ->
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }

    @Test
    fun `listDraftEmailMessageMetadata() should return an empty list if no addresses found for user`() = runTest {
        mockApiClient.stub {
            onBlocking {
                listEmailAddressesQuery(
                    any(),
                )
            } doAnswer {
                listEmailAddressesQueryResponseWithEmptyList
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listDraftEmailMessageMetadata()
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldNotBe null
        result.size shouldBe 0

        verify(mockApiClient).listEmailAddressesQuery(
            check { input ->
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
    }

    @Test
    fun `listDraftEmailMessageMetadata() should return an empty list if no drafts found`() = runTest {
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
            client.listDraftEmailMessageMetadata()
        }
        deferredResult.start()
        val result = deferredResult.await()

        result.size shouldBe 0

        verify(mockApiClient).listEmailAddressesQuery(
            check { input ->
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
        verify(mockApiClient).getEmailAddressQuery(
            any(),
        )
        verify(mockS3Client).list(
            check {
                it shouldContain emailAddressId
            },
        )
        verify(mockS3TransientClient).list(
            check {
                it shouldContain emailAddressId
            },
        )
    }

    @Test
    fun `listDraftEmailMessageMetadata() should return a list of metadata for the user`() = runTest {
        val emailAddressId = "emailAddressId"

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listDraftEmailMessageMetadata()
        }
        deferredResult.start()
        val result = deferredResult.await()

        result.size shouldBe 2
        result[0].id shouldBe mockListObjectsResponse[0].key
        result[0].emailAddressId shouldBe emailAddressId
        result[1].id shouldBe mockListObjectsResponse[1].key
        result[1].emailAddressId shouldBe emailAddressId

        verify(mockApiClient).listEmailAddressesQuery(
            check { input ->
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
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
    fun `listDraftEmailMessageMetadata() should migrate any messages found in transient bucket`() = runTest {
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
        val mockSealedData = "mockSealedData".toByteArray()
        mockS3TransientClient.stub {
            onBlocking {
                getObjectMetadata(anyString())
            } doReturn mockObjectMetadata
            onBlocking {
                download(anyString())
            } doReturn mockSealedData
            onBlocking {
                delete(anyString())
            } doReturn Unit
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listDraftEmailMessageMetadata()
        }

        deferredResult.start()
        val result = deferredResult.await()

        result.size shouldBe 2
        result[0].id shouldBe mockListObjectsResponse[0].key
        result[0].emailAddressId shouldBe emailAddressId
        result[1].id shouldBe mockListObjectsResponse[1].key
        result[1].emailAddressId shouldBe emailAddressId

        verify(mockApiClient).listEmailAddressesQuery(
            check { input ->
                input.limit shouldBe Optional.present(10)
                input.nextToken shouldBe Optional.absent()
            },
        )
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
                it shouldBe mockSealedData
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
    }
}
