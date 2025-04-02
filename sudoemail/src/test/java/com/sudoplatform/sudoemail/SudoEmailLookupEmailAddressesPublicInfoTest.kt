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
import com.sudoplatform.sudoemail.graphql.LookupEmailAddressesPublicInfoQuery
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.PublicKeyFormat
import com.sudoplatform.sudoemail.types.inputs.LookupEmailAddressesPublicInfoInput
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
import java.util.concurrent.CancellationException
import com.sudoplatform.sudoemail.graphql.type.LookupEmailAddressesPublicInfoInput as LookupEmailAddressesPublicInfoRequest

/**
 * Test the correct operation of [SudoEmailClient.lookupEmailAddressesPublicInfo]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailLookupEmailAddressesPublicInfoTest : BaseTests() {

    private val input by before {
        LookupEmailAddressesPublicInfoInput(
            listOf("emailAddress"),
        )
    }

    private val queryResponse by before {
        JSONObject(
            """
                {
                    'lookupEmailAddressesPublicInfo': {
                        'items': [{
                            '__typename': 'EmailAddressPublicInfo',
                            'emailAddress': 'emailAddress',
                            'keyId': 'keyId',
                            'publicKey': 'publicKey',
                            'publicKeyDetails': {
                                '__typename': 'EmailAddressPublicKey',
                                'publicKey': 'publicKey',
                                'keyFormat': 'RSA_PUBLIC_KEY',
                                'algorithm': 'algorithm'
                            }
                        }],
                        'nextToken': null
                    }
                }
            """.trimIndent(),
        )
    }

    private val queryResponseWithEmptyList by before {
        JSONObject(
            """
                {
                    'lookupEmailAddressesPublicInfo': {
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
                    argThat { this.query.equals(LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT) },
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
    fun `lookupEmailAddressesPublicInfo() should return results when no error present`() = runTest {
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.lookupEmailAddressesPublicInfo(input)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldNotBe null
        result.count() shouldBe 1
        with(result[0]) {
            emailAddress shouldBe "emailAddress"
            keyId shouldBe "keyId"
            publicKey shouldBe "publicKey"
            publicKeyDetails.publicKey shouldBe "publicKey"
            publicKeyDetails.keyFormat shouldBe PublicKeyFormat.RSA_PUBLIC_KEY
            publicKeyDetails.algorithm shouldBe "algorithm"
        }

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT
                val input = it.variables["input"] as LookupEmailAddressesPublicInfoRequest
                input.emailAddresses shouldBe listOf("emailAddress")
            },
            any(),
            any(),
        )
    }

    @Test
    fun `lookupEmailAddressesPublicInfo() should return empty result when query result data is empty`() = runTest {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT) },
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

        val input = LookupEmailAddressesPublicInfoInput(emptyList())
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.lookupEmailAddressesPublicInfo(input)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldBe emptyList()

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT
                val queryInput = it.variables["input"] as LookupEmailAddressesPublicInfoRequest
                queryInput.emailAddresses shouldBe emptyList()
            },
            any(),
            any(),
        )
    }

    @Test
    fun `lookupEmailAddressesPublicInfo() should throw when http error occurs`() = runTest {
        val testError = GraphQLResponse.Error(
            "mock",
            null,
            null,
            mapOf("httpStatus" to HttpURLConnection.HTTP_FORBIDDEN),
        )
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT) },
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
                client.lookupEmailAddressesPublicInfo(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT
                val input = it.variables["input"] as LookupEmailAddressesPublicInfoRequest
                input.emailAddresses shouldBe listOf("emailAddress")
            },
            any(),
            any(),
        )
    }

    @Test
    fun `lookupEmailAddressesPublicInfo() should throw when unknown error occurs`() = runTest {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doThrow
                RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.UnknownException> {
                client.lookupEmailAddressesPublicInfo(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT
                val input = it.variables["input"] as LookupEmailAddressesPublicInfoRequest
                input.emailAddresses shouldBe listOf("emailAddress")
            },
            any(),
            any(),
        )
    }

    @Test
    fun `lookupEmailAddressesPublicInfo() should not block coroutine cancellation exception`() = runTest {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query.equals(LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doThrow
                CancellationException("Mock Cancellation Exception")
        }

        shouldThrow<CancellationException> {
            client.lookupEmailAddressesPublicInfo(input)
        }

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT
                val input = it.variables["input"] as LookupEmailAddressesPublicInfoRequest
                input.emailAddresses shouldBe listOf("emailAddress")
            },
            any(),
            any(),
        )
    }
}
