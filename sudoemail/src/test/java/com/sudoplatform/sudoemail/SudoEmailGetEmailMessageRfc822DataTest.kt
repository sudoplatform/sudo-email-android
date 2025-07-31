/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.util.Base64
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageRfc822DataInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException
import java.util.zip.GZIPOutputStream

/**
 * Test the correct operation of [SudoEmailClient.getEmailMessageRfc822Data]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetEmailMessageRfc822DataTest : BaseTests() {

    private val input by before {
        GetEmailMessageRfc822DataInput(
            id = "emailMessageId",
            emailAddressId = "emailAddressId",
        )
    }

    private val mockRfc822Metadata: ObjectMetadata = ObjectMetadata()

    private val queryResponse by before {
        DataFactory.getEmailMessageQueryResponse(
            mockSeal(DataFactory.unsealedHeaderDetailsString),
        )
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
                getEmailMessageQuery(
                    any(),
                )
            } doAnswer {
                queryResponse
            }
        }
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

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking { download(anyString()) } doReturn mockSeal("foobar").toByteArray(Charsets.UTF_8)
            onBlocking { getObjectMetadata(anyString()) } doReturn mockRfc822Metadata
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
    fun `getEmailMessageRfc822Data() should return results when no error present`() =
        runTest {
            val input = GetEmailMessageRfc822DataInput(
                id = "emailMessageId",
                emailAddressId = "emailAddressId",
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailMessageRfc822Data(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result?.id shouldBe "emailMessageId"
            result?.rfc822Data shouldBe DataFactory.unsealedHeaderDetailsString.toByteArray()

            verify(mockApiClient).getEmailMessageQuery(
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockS3Client).download(anyString())
            verify(mockS3Client).getObjectMetadata(anyString())
        }

    @Test
    fun `getEmailMessageRfc822Data() should decompress data when appropriate`() =
        runTest {
            mockRfc822Metadata.contentEncoding =
                "sudoplatform-compression, sudoplatform-crypto, sudoplatform-binary-data"
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).bufferedWriter(Charsets.UTF_8)
                .use { it.write(DataFactory.unsealedHeaderDetailsString) }
            val compressedBytes = bos.toByteArray()
            val encodedBytes = Base64.encode(compressedBytes)

            mockS3Client.stub {
                onBlocking { getObjectMetadata(anyString()) } doReturn mockRfc822Metadata
            }

            mockKeyManager.stub {
                on {
                    decryptWithSymmetricKey(
                        any<ByteArray>(),
                        any<ByteArray>(),
                    )
                } doReturn encodedBytes
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailMessageRfc822Data(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result?.id shouldBe "emailMessageId"
            result?.rfc822Data?.toString(Charsets.UTF_8) shouldBe DataFactory.unsealedHeaderDetailsString

            verify(mockApiClient).getEmailMessageQuery(
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockS3Client).download(anyString())
            verify(mockS3Client).getObjectMetadata(anyString())
        }

    @Test
    fun `getEmailMessageRfc822Data() should return null result when query response is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getEmailMessageQuery(
                        any(),
                    )
                } doAnswer {
                    GraphQLResponse(null, null)
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailMessageRfc822Data(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe null

            verify(mockApiClient).getEmailMessageQuery(
                any(),
            )
        }

    @Test
    fun `getEmailMessageRfc822Data() should throw when http error occurs`() = runTest {
        val testError = GraphQLResponse.Error(
            "mock",
            null,
            null,
            mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
        )
        mockApiClient.stub {
            onBlocking {
                getEmailMessageQuery(
                    any(),
                )
            }.thenAnswer {
                GraphQLResponse(null, listOf(testError))
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.getEmailMessageRfc822Data(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiClient).getEmailMessageQuery(
            any(),
        )
    }

    @Test
    fun `getEmailMessageRfc822Data() should throw when unknown error occurs`() = runTest {
        mockApiClient.stub {
            onBlocking {
                getEmailMessageQuery(
                    any(),
                )
            } doThrow RuntimeException("Mock Cancellation Exception")
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.getEmailMessageRfc822Data(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiClient).getEmailMessageQuery(
            any(),
        )
    }

    @Test
    fun `getEmailMessageRfc822Data() should not suppress CancellationException`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getEmailMessageQuery(
                        any(),
                    )
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<CancellationException> {
                    client.getEmailMessageRfc822Data(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailMessageQuery(
                any(),
            )
        }
}
