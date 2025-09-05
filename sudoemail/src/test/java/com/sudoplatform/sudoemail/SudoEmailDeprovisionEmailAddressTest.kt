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
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.mockito.ArgumentMatchers
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
import java.net.HttpURLConnection
import java.util.Date
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [SudoEmailClient.deprovisionEmailAddress]
 * using mocks and spies.
 */
class SudoEmailDeprovisionEmailAddressTest : BaseTests() {
    private val mutationResponse by before {
        DataFactory.deprovisionEmailAddressMutationResponse()
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
                deprovisionEmailAddressMutation(
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
        DefaultServiceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking { upload(any(), ArgumentMatchers.anyString(), anyOrNull()) } doReturn "42"
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
    fun `deprovisionEmailAddress() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deprovisionEmailAddress("emailAddressId")
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            with(result) {
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
                alias shouldBe null
            }

            verify(mockApiClient).deprovisionEmailAddressMutation(
                any(),
            )
        }

    @Test
    fun `deprovisionEmailAddress() should throw when mutation response is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailAddressMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, null)
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.DeprovisionFailedException> {
                        client.deprovisionEmailAddress("emailAddressId")
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deprovisionEmailAddressMutation(
                any(),
            )
        }

    @Test
    fun `deprovisionEmailAddress() should throw when response has an email address not found error`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "EmailValidation"),
                )
            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailAddressMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
                        client.deprovisionEmailAddress("emailAddressId")
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deprovisionEmailAddressMutation(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                },
            )
        }

    @Test
    fun `deprovisionEmailAddress() should throw when response has an unauthorized address error`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "UnauthorizedAddress"),
                )
            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailAddressMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.UnauthorizedEmailAddressException> {
                        client.deprovisionEmailAddress("emailAddressId")
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deprovisionEmailAddressMutation(
                check { input ->
                    input.emailAddressId shouldBe "emailAddressId"
                },
            )
        }

    @Test
    fun `deprovisionEmailAddress() should throw when http error occurs`() =
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
                    deprovisionEmailAddressMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                        client.deprovisionEmailAddress("emailAddressId")
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deprovisionEmailAddressMutation(
                any(),
            )
        }

    @Test
    fun `deprovisionEmailAddress() should throw when unknown error occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailAddressMutation(
                        any(),
                    )
                } doThrow RuntimeException("Mock Runtime Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.UnknownException> {
                        client.deprovisionEmailAddress("emailAddressId")
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deprovisionEmailAddressMutation(
                any(),
            )
        }

    @Test
    fun `deprovisionEmailAddress() should not suppress CancellationException`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailAddressMutation(
                        any(),
                    )
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<CancellationException> {
                        client.deprovisionEmailAddress("emailAddressId")
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deprovisionEmailAddressMutation(
                any(),
            )
        }
}
