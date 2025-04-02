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
import com.sudoplatform.sudoemail.graphql.UpdateEmailAddressMetadataMutation
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailAddressMetadataInput
import com.sudoplatform.sudoemail.types.transformers.Unsealer
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
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
import org.mockito.ArgumentMatchers.eq
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
import java.util.concurrent.CancellationException
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailAddressMetadataInput as UpdateEmailAddressMetadataRequest

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
        JSONObject(
            """
                {
                    'updateEmailAddressMetadata': 'emailAddressId'
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
                mutate<String>(
                    argThat { this.query.equals(UpdateEmailAddressMetadataMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(mutationResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
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
        verifyNoMoreInteractions(mockContext, mockUserClient, mockKeyManager, mockApiCategory, mockS3Client)
    }

    @Test
    fun `updateEmailAddressMetadata() should return results when no error present`() = runTest {
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.updateEmailAddressMetadata(input)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldBe "emailAddressId"
        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe UpdateEmailAddressMetadataMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as UpdateEmailAddressMetadataRequest
                input.id shouldBe "emailAddressId"
            },
            any(),
            any(),
        )
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `updateEmailAddressMetadata() should throw when response is null`() = runTest {
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(UpdateEmailAddressMetadataMutation.OPERATION_DOCUMENT) },
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
            shouldThrow<SudoEmailClient.EmailAddressException.UpdateFailedException> {
                client.updateEmailAddressMetadata(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe UpdateEmailAddressMetadataMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as UpdateEmailAddressMetadataRequest
                input.id shouldBe "emailAddressId"
            },
            any(),
            any(),
        )
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `updateEmailAddressMetadata() should throw when unsealing fails`() = runTest {
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(UpdateEmailAddressMetadataMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doThrow Unsealer.UnsealerException.UnsupportedAlgorithmException(
                "Mock Unsealer Exception",
            )
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.UnsealingException> {
                client.updateEmailAddressMetadata(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe UpdateEmailAddressMetadataMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as UpdateEmailAddressMetadataRequest
                input.id shouldBe "emailAddressId"
            },
            any(),
            any(),
        )
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `updateEmailAddressMetadata() should throw when http error occurs`() = runTest {
        val testError = GraphQLResponse.Error(
            "mock",
            null,
            null,
            mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
        )
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(UpdateEmailAddressMetadataMutation.OPERATION_DOCUMENT) },
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
            shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                client.updateEmailAddressMetadata(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe UpdateEmailAddressMetadataMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as UpdateEmailAddressMetadataRequest
                input.id shouldBe "emailAddressId"
            },
            any(),
            any(),
        )
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `updateEmailAddressMetadata() should throw when unknown error occurs()`() = runTest {
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(UpdateEmailAddressMetadataMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.UnknownException> {
                client.updateEmailAddressMetadata(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe UpdateEmailAddressMetadataMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as UpdateEmailAddressMetadataRequest
                input.id shouldBe "emailAddressId"
            },
            any(),
            any(),
        )
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `updateEmailAddressMetadata() should not block coroutine cancellation exception`() = runTest {
        mockServiceKeyManager.stub {
            on { getCurrentSymmetricKeyId() } doThrow CancellationException("mock")
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<CancellationException> {
                client.updateEmailAddressMetadata(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }
}
