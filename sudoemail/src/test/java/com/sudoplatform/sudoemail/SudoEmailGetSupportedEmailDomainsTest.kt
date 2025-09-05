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
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
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
import org.mockito.kotlin.anyOrNull
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
 * Test the correct operation of [SudoEmailClient.getSupportedEmailDomains]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetSupportedEmailDomainsTest : BaseTests() {
    private val domains = listOf("foo.com", "bar.com")

    private val queryResponse by before {
        DataFactory.getEmailDomainsQueryResponse(domains)
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
                getSupportedEmailDomainsQuery()
            } doAnswer {
                queryResponse
            }
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { getPassword(anyString()) } doReturn ByteArray(42)
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
    fun `getSupportedEmailDomains() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getSupportedEmailDomains()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isEmpty() shouldBe false
            result.size shouldBe 2
            result shouldContainExactlyInAnyOrder listOf("bar.com", "foo.com")

            verify(mockApiClient).getSupportedEmailDomainsQuery()
        }

    @Test
    fun `getSupportedEmailDomains() should return empty list output when query result data is empty`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getSupportedEmailDomainsQuery()
                } doAnswer {
                    DataFactory.getEmailDomainsQueryResponse(emptyList())
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getSupportedEmailDomains()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isEmpty() shouldBe true
            result.size shouldBe 0

            verify(mockApiClient).getSupportedEmailDomainsQuery()
        }

    @Test
    fun `getSupportedEmailDomains() should return empty list output when query response is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getSupportedEmailDomainsQuery()
                }.thenAnswer {
                    GraphQLResponse(null, null)
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getSupportedEmailDomains()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isEmpty() shouldBe true
            result.size shouldBe 0

            verify(mockApiClient).getSupportedEmailDomainsQuery()
        }

    @Test
    fun `getSupportedEmailDomains() should throw when response has error`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "DilithiumCrystalsOutOfAlignment"),
                )
            mockApiClient.stub {
                onBlocking {
                    getSupportedEmailDomainsQuery()
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                        client.getSupportedEmailDomains()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getSupportedEmailDomainsQuery()
        }

    @Test
    fun `getSupportedEmailDomains() should throw when http error occurs`() =
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
                    getSupportedEmailDomainsQuery()
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                        client.getSupportedEmailDomains()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getSupportedEmailDomainsQuery()
        }

    @Test
    fun `getSupportedEmailDomains() should throw when unknown error occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getSupportedEmailDomainsQuery()
                } doThrow RuntimeException("Mock Runtime Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.UnknownException> {
                        client.getSupportedEmailDomains()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getSupportedEmailDomainsQuery()
        }

    @Test
    fun `getSupportedEmailDomains() should not block coroutine cancellation exception`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getSupportedEmailDomainsQuery()
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            shouldThrow<CancellationException> {
                client.getSupportedEmailDomains()
            }

            verify(mockApiClient).getSupportedEmailDomainsQuery()
        }
}
