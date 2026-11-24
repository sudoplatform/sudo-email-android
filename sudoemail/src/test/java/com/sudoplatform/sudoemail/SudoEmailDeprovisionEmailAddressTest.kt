/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.EmailMessageBodyCache
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.deprovisionEmailAddress]
 * using mocks and spies.
 */
class SudoEmailDeprovisionEmailAddressTest : BaseTests() {
    private val sealedEmailAddress by before {
        EntityDataFactory.getSealedEmailAddressEntity()
    }

    private val sealedEmailAddressWithKeys by before {
        sealedEmailAddress.copy(keyRingId = "keyRingId", keyIds = listOf("keyId1", "keyId2"))
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking {
                deprovision(
                    any(),
                )
            } doAnswer {
                sealedEmailAddress
            }
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

    private val mockEmailMessageBodyCache by before {
        mock<EmailMessageBodyCache>()
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
            emailAddressService = mockEmailAddressService,
            emailMessageBodyCache = mockEmailMessageBodyCache,
            enforceSingletonKeyPair = false,
            allowCallerManagedKeys = false,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockContext,
            mockUserClient,
            mockKeyManager,
            mockEmailAddressService,
            mockEmailMessageBodyCache,
            mockS3Client,
        )
    }

    @Test
    fun `deprovisionEmailAddress() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deprovisionEmailAddress(mockEmailAddressId)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            with(result) {
                id shouldBe mockEmailAddressId
                owner shouldBe mockOwner
                owners.first().id shouldBe mockOwner
                owners.first().issuer shouldBe "issuer"
                emailAddress shouldBe "example@sudoplatform.com"
                size shouldBe 0.0
                numberOfEmailMessages shouldBe 0
                version shouldBe 1
                createdAt shouldBe Date(1L)
                updatedAt shouldBe Date(1L)
                lastReceivedAt shouldBe Date(1L)
                displayName shouldBe null
            }

            verify(mockEmailAddressService).deprovision(
                check { input ->
                    input.emailAddressId shouldBe mockEmailAddressId
                },
            )
            verify(mockEmailMessageBodyCache).flush(
                check { input ->
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sudoId shouldBe null
                },
            )
        }

    @Test
    fun `deprovisionEmailAddress() should throw when service throws`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking {
                    deprovision(
                        any(),
                    )
                } doThrow SudoEmailClient.EmailAddressException.DeprovisionFailedException("Error")
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.DeprovisionFailedException> {
                        client.deprovisionEmailAddress(mockEmailAddressId)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockEmailAddressService).deprovision(
                any(),
            )
        }

    @Test
    fun `deprovisionEmailAddress() should keep the key pairs when the key registrations can't be listed`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { deprovision(any()) } doReturn sealedEmailAddressWithKeys
                onBlocking { listPublicKeyRegistrations() } doThrow
                    SudoEmailClient.EmailAddressException.FailedException("Mock")
            }

            client.deprovisionEmailAddress(mockEmailAddressId).id shouldBe mockEmailAddressId

            verify(mockEmailAddressService).deprovision(any())
            verify(mockEmailAddressService).listPublicKeyRegistrations()
            verify(mockKeyManager).getPassword(DefaultServiceKeyManager.SINGLETON_KEY_ID_NAME)
            verify(mockEmailMessageBodyCache).flush(any())
        }

    @Test
    fun `deprovisionEmailAddress() should throw KeyPairRemovalFailedException after trying every key pair`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { deprovision(any()) } doReturn sealedEmailAddressWithKeys
                onBlocking { listPublicKeyRegistrations() } doReturn emptyList()
            }
            mockKeyManager.stub {
                on { deleteKeyPair("keyId1") } doThrow RuntimeException("Mock")
            }

            val error =
                shouldThrow<SudoEmailClient.EmailAddressException.KeyPairRemovalFailedException> {
                    client.deprovisionEmailAddress(mockEmailAddressId)
                }

            error.emailAddress.id shouldBe mockEmailAddressId
            error.keyIds shouldBe listOf("keyId1")
            verify(mockEmailAddressService).deprovision(any())
            verify(mockEmailAddressService).listPublicKeyRegistrations()
            verify(mockKeyManager, times(3)).getPassword(DefaultServiceKeyManager.SINGLETON_KEY_ID_NAME)
            verify(mockKeyManager).deleteKeyPair("keyId1")
            verify(mockKeyManager).deleteKeyPair("keyId2")
            verify(mockEmailMessageBodyCache).flush(any())
        }
}
