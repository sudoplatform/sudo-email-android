/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.configuration

import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [GraphQLConfigurationDataService]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GraphQLConfigurationDataServiceTest : BaseTests() {
    private val domains = listOf("foo.com", "bar.com")

    private val getEmailDomainsQueryResponse by before {
        DataFactory.getEmailDomainsQueryResponse(domains)
    }

    private val getConfiguredEmailDomainsQueryResponse by before {
        DataFactory.getConfiguredEmailDomainsQueryResponse(domains)
    }

    private val getEmailConfigQueryResponse by before {
        DataFactory.getEmailConfigQueryResponse()
    }

    override val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                getSupportedEmailDomainsQuery()
            } doAnswer {
                getEmailDomainsQueryResponse
            }
            onBlocking {
                getConfiguredEmailDomainsQuery()
            } doAnswer {
                getConfiguredEmailDomainsQueryResponse
            }
            onBlocking {
                getEmailConfigQuery()
            } doAnswer {
                getEmailConfigQueryResponse
            }
        }
    }

    private val instanceUnderTest by before {
        GraphQLConfigurationDataService(
            mockApiClient,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockApiClient,
        )
    }

    @Test
    fun `getConfigurationData() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    instanceUnderTest.getConfigurationData()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            with(result) {
                deleteEmailMessagesLimit shouldBe 10
                updateEmailMessagesLimit shouldBe 5
                emailMessageMaxInboundMessageSize shouldBe 200
                emailMessageMaxOutboundMessageSize shouldBe 100
                emailMessageRecipientsLimit shouldBe 5
                encryptedEmailMessageRecipientsLimit shouldBe 10
                prohibitedFileExtensions shouldBe listOf(".js", ".exe", ".lib")
            }

            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `getConfigurationData() should throw when unknown error occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getEmailConfigQuery()
                } doThrow RuntimeException("Mock Runtime Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailConfigurationException.UnknownException> {
                        instanceUnderTest.getConfigurationData()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `getConfigurationData() should throw when no config data is returned`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getEmailConfigQuery()
                }.thenAnswer {
                    GraphQLResponse(null, null)
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailConfigurationException.FailedException> {
                        instanceUnderTest.getConfigurationData()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `getConfigurationData() should throw when query response contains errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    null,
                    null,
                    null,
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailConfigQuery()
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailConfigurationException.FailedException> {
                        instanceUnderTest.getConfigurationData()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `getConfigurationData() should not block coroutine cancellation exception`() =
        runBlocking<Unit> {
            mockApiClient.stub {
                onBlocking {
                    getEmailConfigQuery()
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            shouldThrow<CancellationException> {
                instanceUnderTest.getConfigurationData()
            }

            verify(mockApiClient).getEmailConfigQuery()
        }

    @Test
    fun `getConfiguredEmailDomains() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    instanceUnderTest.getConfiguredEmailDomains()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isEmpty() shouldBe false
            result.size shouldBe 2
            result shouldContainExactlyInAnyOrder listOf("bar.com", "foo.com")

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
        }

    @Test
    fun `getConfiguredEmailDomains() should return empty list output when query result data is empty`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getConfiguredEmailDomainsQuery()
                } doAnswer {
                    DataFactory.getConfiguredEmailDomainsQueryResponse()
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    instanceUnderTest.getConfiguredEmailDomains()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isEmpty() shouldBe true
            result.size shouldBe 0

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
        }

    @Test
    fun `getConfiguredEmailDomains() should return empty list output when query response is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getConfiguredEmailDomainsQuery()
                }.thenAnswer {
                    GraphQLResponse(null, null)
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    instanceUnderTest.getConfiguredEmailDomains()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isEmpty() shouldBe true
            result.size shouldBe 0

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
        }

    @Test
    fun `getConfiguredEmailDomains() should throw when response has error`() =
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
                    getConfiguredEmailDomainsQuery()
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                        instanceUnderTest.getConfiguredEmailDomains()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
        }

    @Test
    fun `getConfiguredEmailDomains() should throw when http error occurs`() =
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
                    getConfiguredEmailDomainsQuery()
                }.thenAnswer {
                    GraphQLResponse(null, listOf(testError))
                }
            }
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.FailedException> {
                        instanceUnderTest.getConfiguredEmailDomains()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
        }

    @Test
    fun `getConfiguredEmailDomains() should throw when unknown error occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getConfiguredEmailDomainsQuery()
                } doThrow RuntimeException("Mock Runtime Exception")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.UnknownException> {
                        instanceUnderTest.getConfiguredEmailDomains()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
        }

    @Test
    fun `getConfiguredEmailDomains() should not block coroutine cancellation exception`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getConfiguredEmailDomainsQuery()
                } doThrow CancellationException("Mock Cancellation Exception")
            }

            shouldThrow<CancellationException> {
                instanceUnderTest.getConfiguredEmailDomains()
            }

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
        }
}
