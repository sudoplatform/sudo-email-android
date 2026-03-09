/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudouser.SudoPlatformSignInCallback
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [DefaultSudoEmailClient.ensureSignedIn]
 * using mocks and spies.
 *
 * Tests cover:
 * - No callback scenario (no check performed)
 * - Callback set with user signed in (callback not invoked)
 * - Callback set with user not signed in (callback invoked)
 * - Callback exception propagation
 * - Concurrent calls to ensureSignedIn
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailEnsureSignedInTest : BaseTests() {
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

    private val configurationDataEntity by before {
        EntityDataFactory.getConfigurationDataEntity()
    }

    private val mockConfigurationDataService by before {
        mock<ConfigurationDataService>().stub {
            onBlocking {
                getConfigurationData()
            } doReturn configurationDataEntity
        }
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
    fun `ensureSignedIn should not check sign-in status when no callback is set`() =
        runTest {
            // Setup: Mock user client to return signed in status
            mockUserClient.stub {
                onBlocking { isSignedIn() } doReturn true
            }

            // No callback set - should not call isSignedIn()
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getConfigurationData()
                }
            deferredResult.start()
            deferredResult.await()

            // Verify isSignedIn was NOT called (no sign-in check performed)
            verify(mockConfigurationDataService).getConfigurationData()
            // Note: mockUserClient.isSignedIn() should not be called
        }

    @Test
    fun `ensureSignedIn should not invoke callback when user is signed in`() =
        runTest {
            var callbackInvoked = false

            // Setup: Mock user client to return signed in status
            mockUserClient.stub {
                onBlocking { isSignedIn() } doReturn true
            }

            // Set callback
            client.setSignInCallback(
                object : SudoPlatformSignInCallback {
                    override suspend fun signIn() {
                        callbackInvoked = true
                    }
                },
            )

            // Execute operation
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getConfigurationData()
                }
            deferredResult.start()
            deferredResult.await()

            // Verify callback was NOT invoked
            callbackInvoked shouldBe false
            verify(mockUserClient).isSignedIn()
            verify(mockConfigurationDataService).getConfigurationData()
        }

    @Test
    fun `ensureSignedIn should invoke callback when user is not signed in`() =
        runTest {
            var callbackInvoked = false

            // Setup: Mock user client to return not signed in
            mockUserClient.stub {
                onBlocking { isSignedIn() } doReturn false
            }

            // Set callback
            client.setSignInCallback(
                object : SudoPlatformSignInCallback {
                    override suspend fun signIn() {
                        callbackInvoked = true
                    }
                },
            )

            // Execute operation
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getConfigurationData()
                }
            deferredResult.start()
            deferredResult.await()

            // Verify callback WAS invoked
            callbackInvoked shouldBe true
            verify(mockUserClient).isSignedIn()
            verify(mockConfigurationDataService).getConfigurationData()
        }

    @Test
    fun `ensureSignedIn should propagate callback exceptions`() =
        runTest {
            val testException = RuntimeException("Sign-in failed")

            // Setup: Mock user client to return not signed in
            mockUserClient.stub {
                onBlocking { isSignedIn() } doReturn false
            }

            // Set callback that throws exception
            client.setSignInCallback(
                object : SudoPlatformSignInCallback {
                    override suspend fun signIn(): Unit = throw testException
                },
            )

            // Execute operation and expect exception
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<RuntimeException> {
                        client.getConfigurationData()
                    }
                }
            deferredResult.start()
            val thrownException = deferredResult.await()

            // Verify the correct exception was thrown
            thrownException shouldBe testException
            verify(mockUserClient).isSignedIn()
            // Note: getConfigurationData should NOT be called because callback threw
        }

    @Test
    fun `ensureSignedIn should handle concurrent calls safely`() =
        runTest {
            var callbackInvocationCount = 0

            // Setup: Mock user client to return not signed in
            mockUserClient.stub {
                onBlocking { isSignedIn() } doReturn false
            }

            // Set callback
            client.setSignInCallback(
                object : SudoPlatformSignInCallback {
                    override suspend fun signIn() {
                        callbackInvocationCount++
                    }
                },
            )

            // Launch multiple concurrent operations
            val job1 =
                launch(StandardTestDispatcher(testScheduler)) {
                    client.getConfigurationData()
                }
            val job2 =
                launch(StandardTestDispatcher(testScheduler)) {
                    client.getConfigurationData()
                }
            val job3 =
                launch(StandardTestDispatcher(testScheduler)) {
                    client.getConfigurationData()
                }

            // Wait for all jobs to complete
            job1.join()
            job2.join()
            job3.join()

            // Verify callback was invoked for each operation
            callbackInvocationCount shouldBe 3
            verify(mockUserClient, times(3)).isSignedIn()
            verify(mockConfigurationDataService, times(3)).getConfigurationData()
        }

    @Test
    fun `ensureSignedIn should handle callback being set to null`() =
        runTest {
            var callbackInvoked = false

            // Setup: Mock user client to return not signed in
            mockUserClient.stub {
                onBlocking { isSignedIn() } doReturn false
            }

            // Set callback
            client.setSignInCallback(
                object : SudoPlatformSignInCallback {
                    override suspend fun signIn() {
                        callbackInvoked = true
                    }
                },
            )

            // Clear callback
            client.setSignInCallback(null)

            // Execute operation
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getConfigurationData()
                }
            deferredResult.start()
            deferredResult.await()

            // Verify callback was NOT invoked (it was cleared)
            callbackInvoked shouldBe false
            verify(mockConfigurationDataService).getConfigurationData()
            // Note: isSignedIn should not be called when callback is null
        }

    @Test
    fun `ensureSignedIn should handle callback that completes successfully after sign-in`() =
        runTest {
            var callbackInvoked = false
            var signInAttempted = false

            // Setup: User is not signed in
            mockUserClient.stub {
                onBlocking { isSignedIn() } doReturn false
            }

            // Set callback that simulates successful sign-in
            client.setSignInCallback(
                object : SudoPlatformSignInCallback {
                    override suspend fun signIn() {
                        callbackInvoked = true
                        signInAttempted = true
                        // Simulate sign-in process completing
                    }
                },
            )

            // Execute operation
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getConfigurationData()
                }
            deferredResult.start()
            deferredResult.await()

            // Verify callback was invoked and operation completed
            callbackInvoked shouldBe true
            signInAttempted shouldBe true
            verify(mockUserClient).isSignedIn()
            verify(mockConfigurationDataService).getConfigurationData()
        }
}
