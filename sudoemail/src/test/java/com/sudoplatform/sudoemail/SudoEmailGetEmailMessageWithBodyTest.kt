/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudoemail.graphql.GetEmailMessageQuery
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageWithBodyInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudoemail.util.SimplifiedEmailMessage
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.shouldBe
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
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException
import java.util.zip.GZIPOutputStream

/**
 * Test the correct operation of [SudoEmailClient.getEmailMessageWithBody]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetEmailMessageWithBodyTest : BaseTests() {

    private val input by before {
        GetEmailMessageWithBodyInput(
            id = "emailMessageId",
            emailAddressId = "emailAddressId",
        )
    }

    private val mockRfc822Metadata: ObjectMetadata = ObjectMetadata()

    private val emailMessage = SimplifiedEmailMessage(
        listOf("from@bar.com"),
        listOf("to@bar.com"),
        listOf("cc@bar.com"),
        listOf("bcc@bar.com"),
        "email message subject",
        "email message body",
        false,
    )

    private val queryResponse by before {
        JSONObject(
            """
                {
                    'getEmailMessage': {
                        '__typename': 'SealedEmailMessage',
                        'id': 'id',
                        'owner': 'owner',
                        'owners': [],
                        'emailAddressId': 'emailAddressId',
                        'version': 1,
                        'createdAtEpochMs': 1.0,
                        'updatedAtEpochMs': 1.0,
                        'sortDateEpochMs': 1.0,
                        'folderId': 'folderId',
                        'previousFolderId': 'previousFolderId',
                        'direction': 'INBOUND',
                        'seen': false,
                        'repliedTo': false,
                        'forwarded': false,
                        'state': 'DELIVERED',
                        'clientRefId': 'clientRefId',
                        'rfc822Header': {
                            'algorithm': 'algorithm',
                            'keyId': 'keyId',
                            'plainTextType': 'plainText',
                            'base64EncodedSealedData': '${mockSeal(unsealedHeaderDetailsString)}'
                         },
                        'size': 1.0,
                        'encryptionStatus': 'UNENCRYPTED'
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
                    argThat { this.query.equals(GetEmailMessageQuery.OPERATION_DOCUMENT) },
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

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking { download(anyString()) } doReturn mockSeal("foobar").toByteArray(Charsets.UTF_8)
            onBlocking { getObjectMetadata(anyString()) } doReturn mockRfc822Metadata
        }
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>().stub {
            on { parseInternetMessageData(any()) } doReturn emailMessage
        }
    }

    private val mockSealingService by before {
        DefaultSealingService(
            mockServiceKeyManager,
            mockLogger,
        )
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>().stub {
            onBlocking { decrypt(any()) } doReturn ByteArray(42)
        }
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
    fun `getEmailMessageWithBody() should return results when no error present`() =
        runTest {
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailMessageWithBody(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result?.id shouldBe "emailMessageId"
            result?.body shouldBe emailMessage.body

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailMessageQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockS3Client).download(anyString())
            verify(mockS3Client).getObjectMetadata(anyString())
            verify(mockEmailMessageProcessor).parseInternetMessageData(any())
        }

    @Test
    fun `getEmailMessageWithBody() should decompress data when appropriate`() =
        runTest {
            mockRfc822Metadata.contentEncoding =
                "sudoplatform-compression, sudoplatform-crypto, sudoplatform-binary-data"
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).bufferedWriter(Charsets.UTF_8)
                .use { it.write(unsealedHeaderDetailsString) }
            val compressedBytes = bos.toByteArray()
            val encodedBytes = com.amazonaws.util.Base64.encode(compressedBytes)

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
                client.getEmailMessageWithBody(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result?.id shouldBe "emailMessageId"
            result?.body shouldBe emailMessage.body

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailMessageQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
            verify(mockS3Client).download(anyString())
            verify(mockS3Client).getObjectMetadata(anyString())
            verify(mockEmailMessageProcessor).parseInternetMessageData(any())
        }

    @Test
    fun `getEmailMessageWithBody() should return null result when query response is null`() =
        runTest {
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(GetEmailMessageQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(null, null),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailMessageWithBody(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe null

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailMessageQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
        }

    @Test
    fun `getEmailMessageWithBody() should throw when http error occurs`() = runTest {
        val testError = GraphQLResponse.Error(
            "mock",
            null,
            null,
            mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
        )
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(GetEmailMessageQuery.OPERATION_DOCUMENT) },
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
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.getEmailMessageWithBody(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe GetEmailMessageQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
    }

    @Test
    fun `getEmailMessageWithBody() should throw when unknown error occurs`() = runTest {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(GetEmailMessageQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.getEmailMessageWithBody(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe GetEmailMessageQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
    }

    @Test
    fun `getEmailMessageWithBody() should not suppress CancellationException`() =
        runTest {
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(GetEmailMessageQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<CancellationException> {
                    client.getEmailMessageWithBody(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailMessageQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
        }
}
