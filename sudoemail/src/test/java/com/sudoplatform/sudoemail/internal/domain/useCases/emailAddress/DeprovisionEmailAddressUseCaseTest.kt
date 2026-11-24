/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PublicKeyRegistrationEntity
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [DeprovisionEmailAddressUseCase] using mocks.
 */
@RunWith(RobolectricTestRunner::class)
class DeprovisionEmailAddressUseCaseTest : BaseTests() {
    private val ownKeyRingId = "ownKeyRingId"

    private val deprovisioned by before {
        EntityDataFactory
            .getSealedEmailAddressEntity()
            .copy(keyRingId = ownKeyRingId, keyIds = listOf("keyId1", "keyId2"))
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { deprovision(any()) } doReturn deprovisioned
            onBlocking { listPublicKeyRegistrations() } doReturn emptyList()
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>()
    }

    private fun useCase(
        enforceSingletonKeyPair: Boolean = false,
        allowCallerManagedKeys: Boolean = false,
    ) = DeprovisionEmailAddressUseCase(
        emailAddressService = mockEmailAddressService,
        serviceKeyManager = mockServiceKeyManager,
        logger = mockLogger,
        enforceSingletonKeyPair = enforceSingletonKeyPair,
        allowCallerManagedKeys = allowCallerManagedKeys,
    )

    @After
    fun fini() {
        verifyNoMoreInteractions(mockEmailAddressService, mockServiceKeyManager)
    }

    @Test
    fun `execute() should remove no key pairs when the client allows caller managed keys`() =
        runTest {
            useCase(allowCallerManagedKeys = true).execute(mockEmailAddressId) shouldBe deprovisioned

            verify(mockEmailAddressService).deprovision(any())
        }

    @Test
    fun `execute() should remove no key pairs when the client enforces a singleton key pair`() =
        runTest {
            useCase(enforceSingletonKeyPair = true).execute(mockEmailAddressId) shouldBe deprovisioned

            verify(mockEmailAddressService).deprovision(any())
        }

    @Test
    fun `execute() should remove every key pair no other email address or email alias uses`() =
        runTest {
            useCase().execute(mockEmailAddressId) shouldBe deprovisioned

            verify(mockEmailAddressService).deprovision(any())
            verify(mockServiceKeyManager).getSingletonKeyId()
            verify(mockEmailAddressService).listPublicKeyRegistrations()
            verify(mockServiceKeyManager).removeKeyPair("keyId1")
            verify(mockServiceKeyManager).removeKeyPair("keyId2")
        }

    @Test
    fun `execute() should ignore the deprovisioned email address's own key registrations`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { listPublicKeyRegistrations() } doReturn
                    listOf(
                        PublicKeyRegistrationEntity(keyId = "keyId1", keyRingId = ownKeyRingId),
                        PublicKeyRegistrationEntity(keyId = "keyId2", keyRingId = ownKeyRingId),
                    )
            }

            useCase().execute(mockEmailAddressId)

            verify(mockEmailAddressService).deprovision(any())
            verify(mockServiceKeyManager).getSingletonKeyId()
            verify(mockEmailAddressService).listPublicKeyRegistrations()
            verify(mockServiceKeyManager).removeKeyPair("keyId1")
            verify(mockServiceKeyManager).removeKeyPair("keyId2")
        }

    @Test
    fun `execute() should keep a key pair another email address or email alias uses`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { listPublicKeyRegistrations() } doReturn
                    listOf(PublicKeyRegistrationEntity(keyId = "keyId1", keyRingId = "otherKeyRingId"))
            }

            useCase().execute(mockEmailAddressId)

            verify(mockEmailAddressService).deprovision(any())
            verify(mockServiceKeyManager).getSingletonKeyId()
            verify(mockEmailAddressService).listPublicKeyRegistrations()
            verify(mockServiceKeyManager).removeKeyPair("keyId2")
        }

    @Test
    fun `execute() should never remove the singleton key pair`() =
        runTest {
            mockServiceKeyManager.stub {
                on { getSingletonKeyId() } doReturn "keyId1"
            }

            useCase().execute(mockEmailAddressId)

            verify(mockEmailAddressService).deprovision(any())
            verify(mockServiceKeyManager).getSingletonKeyId()
            verify(mockEmailAddressService).listPublicKeyRegistrations()
            verify(mockServiceKeyManager).removeKeyPair("keyId2")
        }

    @Test
    fun `execute() should keep every key pair when the singleton key id can't be read`() =
        runTest {
            mockServiceKeyManager.stub {
                on { getSingletonKeyId() } doThrow DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException("Mock")
            }

            useCase().execute(mockEmailAddressId) shouldBe deprovisioned

            verify(mockEmailAddressService).deprovision(any())
            verify(mockServiceKeyManager).getSingletonKeyId()
        }

    @Test
    fun `execute() should keep every key pair when the key registrations can't be listed`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { listPublicKeyRegistrations() } doThrow SudoEmailClient.EmailAddressException.FailedException("Mock")
            }

            useCase().execute(mockEmailAddressId) shouldBe deprovisioned

            verify(mockEmailAddressService).deprovision(any())
            verify(mockServiceKeyManager).getSingletonKeyId()
            verify(mockEmailAddressService).listPublicKeyRegistrations()
        }

    @Test
    fun `execute() should try every key pair and report the ones it couldn't remove`() =
        runTest {
            mockServiceKeyManager.stub {
                on { removeKeyPair("keyId1") } doThrow DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException("Mock")
            }

            val error =
                shouldThrow<DeprovisionedKeyPairRemovalException> {
                    useCase().execute(mockEmailAddressId)
                }

            error.deprovisioned shouldBe deprovisioned
            error.keyIds shouldBe listOf("keyId1")
            verify(mockEmailAddressService).deprovision(any())
            verify(mockServiceKeyManager).getSingletonKeyId()
            verify(mockEmailAddressService).listPublicKeyRegistrations()
            verify(mockServiceKeyManager).removeKeyPair("keyId1")
            verify(mockServiceKeyManager).removeKeyPair("keyId2")
        }
}
