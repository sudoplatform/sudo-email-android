/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.EmailDomain
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
 * Test the correct operation of [SudoEmailClient.listEmailDomains]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListEmailDomainsTest : BaseTests() {
    private val emailDomains =
        listOf(
            EmailDomain("foo.com", false, emptyMap()),
            EmailDomain("bar.com", true, mapOf("key" to "value")),
        )

    private val mockConfigurationDataService by before {
        mock<ConfigurationDataService>().stub {
            onBlocking {
                listEmailDomains()
            } doReturn emailDomains
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
    fun `listEmailDomains() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailDomains()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isEmpty() shouldBe false
            result.size shouldBe 2
            result[0].domain shouldBe "foo.com"
            result[0].isMaskDomain shouldBe false
            result[0].metadata shouldBe emptyMap()
            result[1].domain shouldBe "bar.com"
            result[1].isMaskDomain shouldBe true
            result[1].metadata shouldBe mapOf("key" to "value")

            verify(mockConfigurationDataService).listEmailDomains()
        }

    @Test
    fun `listEmailDomains() should return empty list output when query result data is empty`() =
        runTest {
            mockConfigurationDataService.stub {
                onBlocking {
                    listEmailDomains()
                } doAnswer {
                    emptyList()
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listEmailDomains()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.isEmpty() shouldBe true
            result.size shouldBe 0

            verify(mockConfigurationDataService).listEmailDomains()
        }

    @Test
    fun `listEmailDomains() should throw when response has error`() =
        runTest {
            mockConfigurationDataService.stub {
                onBlocking {
                    listEmailDomains()
                } doThrow SudoEmailClient.EmailConfigurationException.FailedException("Mock Error")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailConfigurationException.FailedException> {
                        client.listEmailDomains()
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockConfigurationDataService).listEmailDomains()
        }
}
