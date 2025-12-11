/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.getConfiguredEmailDomains]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetConfiguredEmailDomainsTest : BaseTests() {
    private val domains = listOf("foo.com", "bar.com")

    private val mockConfigurationDataService by before {
        mock<ConfigurationDataService>().stub {
            onBlocking {
                getConfiguredEmailDomains()
            } doReturn domains
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

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>()
    }

    private val client by before {
        DefaultSudoEmailClient(
            context = mockContext,
            serviceKeyManager = mockServiceKeyManager,
            apiClient = mockApiClient,
            sudoUserClient = mockUserClient,
            logger = mockLogger,
            region = "region",
            emailBucket = "identityBucket",
            transientBucket = "transientBucket",
            notificationHandler = null,
            s3TransientClient = mockS3Client,
            s3EmailClient = mockS3Client,
            configurationDataService = mockConfigurationDataService,
            useCaseFactory = mockUseCaseFactory,
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
            mockConfigurationDataService,
        )
    }

    @Test
    fun `getConfiguredEmailDomains() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getConfiguredEmailDomains()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isEmpty() shouldBe false
            result.size shouldBe 2
            result shouldContainExactlyInAnyOrder listOf("bar.com", "foo.com")

            verify(mockConfigurationDataService).getConfiguredEmailDomains()
        }

    @Test
    fun `getConfiguredEmailDomains() should return empty list output when query result data is empty`() =
        runTest {
            mockConfigurationDataService.stub {
                onBlocking {
                    getConfiguredEmailDomains()
                } doAnswer {
                    emptyList()
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getConfiguredEmailDomains()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isEmpty() shouldBe true
            result.size shouldBe 0

            verify(mockConfigurationDataService).getConfiguredEmailDomains()
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
            mockConfigurationDataService.stub {
                onBlocking {
                    getConfiguredEmailDomains()
                } doThrow SudoEmailClient.EmailConfigurationException.FailedException("Mock Error")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailConfigurationException.FailedException> {
                        client.getConfiguredEmailDomains()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockConfigurationDataService).getConfiguredEmailDomains()
        }
}
