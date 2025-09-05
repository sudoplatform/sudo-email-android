/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailAddressMetadataInput
import com.sudoplatform.sudoemail.types.transformers.Unsealer
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
import org.mockito.ArgumentMatchers.eq
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
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [SudoEmailClient.updateEmailAddressMetadata]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailUpdateEmailAddressMetadataTest : BaseTests() {
    private val input by before {
        UpdateEmailAddressMetadataInput(
            "emailAddressId",
            "John Doe",
        )
    }

    private val mutationResponse by before {
        DataFactory.updateEmailAddressMetadataMutationResponse("emailAddressId")
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
                updateEmailAddressMetadataMutation(
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
        mock<ServiceKeyManager>().stub {
            on { getCurrentSymmetricKeyId() } doReturn "symmetricKeyId"
            on { encryptWithSymmetricKeyId(anyString(), any(), eq(null)) } doReturn ByteArray(42)
        }
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
        verifyNoMoreInteractions(mockContext, mockUserClient, mockKeyManager, mockApiClient, mockS3Client)
    }

    @Test
    fun `updateEmailAddressMetadata() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.updateEmailAddressMetadata(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe "emailAddressId"
            verify(mockApiClient).updateEmailAddressMetadataMutation(
                check { input ->
                    input.id shouldBe "emailAddressId"
                },
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        }

    @Test
    fun `updateEmailAddressMetadata() should throw when response is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    updateEmailAddressMetadataMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, null)
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.UpdateFailedException> {
                        client.updateEmailAddressMetadata(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).updateEmailAddressMetadataMutation(
                check { input ->
                    input.id shouldBe "emailAddressId"
                },
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        }

    @Test
    fun `updateEmailAddressMetadata() should throw when unsealing fails`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    updateEmailAddressMetadataMutation(
                        any(),
                    )
                } doThrow
                    Unsealer.UnsealerException.UnsupportedAlgorithmException(
                        "Mock Unsealer Exception",
                    )
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.UnsealingException> {
                        client.updateEmailAddressMetadata(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).updateEmailAddressMetadataMutation(
                check { input ->
                    input.id shouldBe "emailAddressId"
                },
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        }

    @Test
    fun `updateEmailAddressMetadata() should throw when http error occurs`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "mock",
                    null,
                    null,
                    mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
                )
            mockApiClient.stub {
                onBlocking {
                    updateEmailAddressMetadataMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                        client.updateEmailAddressMetadata(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).updateEmailAddressMetadataMutation(
                check { input ->
                    input.id shouldBe "emailAddressId"
                },
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        }

    @Test
    fun `updateEmailAddressMetadata() should throw when unknown error occurs()`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    updateEmailAddressMetadataMutation(
                        any(),
                    )
                } doThrow RuntimeException("Mock Runtime Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.UnknownException> {
                        client.updateEmailAddressMetadata(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).updateEmailAddressMetadataMutation(
                check { input ->
                    input.id shouldBe "emailAddressId"
                },
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        }

    @Test
    fun `updateEmailAddressMetadata() should not block coroutine cancellation exception`() =
        runTest {
            mockServiceKeyManager.stub {
                on { getCurrentSymmetricKeyId() } doThrow CancellationException("mock")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<CancellationException> {
                        client.updateEmailAddressMetadata(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        }
}
