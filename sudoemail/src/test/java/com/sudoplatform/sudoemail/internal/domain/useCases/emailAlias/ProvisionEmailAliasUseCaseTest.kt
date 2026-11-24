/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasEntityRealAddressType
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasEntityStatus
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.SealedEmailAliasEntity
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.keys.KeyPair
import com.sudoplatform.sudoemail.keys.ProvisioningKeyProvider
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the key pair choice and rejection cleanup of [ProvisionEmailAliasUseCase] using mocks.
 */
@RunWith(RobolectricTestRunner::class)
class ProvisionEmailAliasUseCaseTest : BaseTests() {
    private val generatedKeyPair = keyPair("generatedKeyId")
    private val suppliedKeyPair = keyPair("suppliedKeyId")
    private val singletonKeyPair = keyPair("singletonKeyId")

    private fun keyPair(keyId: String) =
        KeyPair(
            keyId = keyId,
            keyRingId = "keyRingId",
            publicKey = "publicKey".toByteArray(),
            privateKey = "privateKey".toByteArray(),
        )

    private val sealedEmailAlias =
        SealedEmailAliasEntity(
            id = "mockEmailAliasId",
            owner = "mockOwner",
            owners = listOf(OwnerEntity(id = "mockOwner", issuer = "issuer")),
            identityId = "mockIdentityId",
            aliasAddress = "mask@example.com",
            realAddress = "real@example.com",
            realAddressType = EmailAliasEntityRealAddressType.EXTERNAL,
            status = EmailAliasEntityStatus.ENABLED,
            inboundReceived = 0,
            inboundDelivered = 0,
            outboundReceived = 0,
            outboundDelivered = 0,
            spamCount = 0,
            virusCount = 0,
            expiresAt = null,
            version = 1,
            createdAt = Date(1),
            updatedAt = Date(2),
            sealedMetadata = null,
        )

    private val mockEmailAliasService by before {
        mock<EmailAliasService>().stub {
            onBlocking { provision(any()) } doReturn sealedEmailAlias
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { generateKeyPair() } doReturn generatedKeyPair
            on { getKeyPairWithId("suppliedKeyId") } doReturn suppliedKeyPair
            on { getSingletonKeyPair() } doReturn singletonKeyPair
        }
    }

    private val mockSealingService by before {
        mock<SealingService>()
    }

    private fun useCase(
        enforceSingletonKeyPair: Boolean = false,
        allowCallerManagedKeys: Boolean = true,
    ) = ProvisionEmailAliasUseCase(
        emailAliasService = mockEmailAliasService,
        serviceKeyManager = mockServiceKeyManager,
        sealingService = mockSealingService,
        logger = mockLogger,
        keyProvider =
            ProvisioningKeyProvider.forEmailAlias(
                serviceKeyManager = mockServiceKeyManager,
                logger = mockLogger,
                enforceSingletonKeyPair = enforceSingletonKeyPair,
                allowCallerManagedKeys = allowCallerManagedKeys,
            ),
    )

    private fun input(keyId: String? = null) =
        ProvisionEmailAliasUseCaseInput(
            aliasAddress = "mask@example.com",
            realAddress = "real@example.com",
            ownershipProofToken = "ownershipProofToken",
            keyId = keyId,
        )

    private fun rejectProvisioning(error: Throwable) {
        mockEmailAliasService.stub {
            onBlocking { provision(any()) } doThrow error
        }
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockEmailAliasService, mockServiceKeyManager, mockSealingService)
    }

    @Test
    fun `execute() should provision with a generated key pair and keep it`() =
        runTest {
            useCase().execute(input()).id shouldBe "mockEmailAliasId"

            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockEmailAliasService).provision(check { it.keyPair shouldBe generatedKeyPair })
        }

    @Test
    fun `execute() should remove the generated key pair when the alias already exists`() =
        runTest {
            rejectProvisioning(SudoEmailClient.EmailAliasException.EmailAliasAlreadyExistsException("Mock"))

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasAlreadyExistsException> {
                useCase().execute(input())
            }

            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockEmailAliasService).provision(any())
            verify(mockServiceKeyManager).removeKeyPair("generatedKeyId")
        }

    @Test
    fun `execute() should keep the generated key pair when the failure doesn't prove the service rejected it`() =
        runTest {
            rejectProvisioning(SudoEmailClient.EmailAliasException.FailedException("Mock 5xx"))

            shouldThrow<SudoEmailClient.EmailAliasException.FailedException> {
                useCase().execute(input())
            }

            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockEmailAliasService).provision(any())
        }

    @Test
    fun `execute() should throw the rejection even when removing the generated key pair fails`() =
        runTest {
            rejectProvisioning(SudoEmailClient.EmailAliasException.EmailAliasAlreadyExistsException("Mock"))
            val removalFailure = DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException("Mock")
            mockServiceKeyManager.stub {
                on { removeKeyPair("generatedKeyId") } doThrow removalFailure
            }

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasAlreadyExistsException> {
                useCase().execute(input())
            }

            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockEmailAliasService).provision(any())
            verify(mockServiceKeyManager).removeKeyPair("generatedKeyId")
        }

    @Test
    fun `execute() should keep a supplied key pair when the service rejects the provisioning`() =
        runTest {
            rejectProvisioning(SudoEmailClient.EmailAliasException.EmailAliasAlreadyExistsException("Mock"))

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasAlreadyExistsException> {
                useCase().execute(input(keyId = "suppliedKeyId"))
            }

            verify(mockServiceKeyManager).getKeyPairWithId("suppliedKeyId")
            verify(mockEmailAliasService).provision(check { it.keyPair shouldBe suppliedKeyPair })
        }

    @Test
    fun `execute() should keep the singleton key pair when the service rejects the provisioning`() =
        runTest {
            rejectProvisioning(SudoEmailClient.EmailAliasException.EmailAliasAlreadyExistsException("Mock"))

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasAlreadyExistsException> {
                useCase(enforceSingletonKeyPair = true).execute(input())
            }

            verify(mockServiceKeyManager).getSingletonKeyPair()
            verify(mockEmailAliasService).provision(check { it.keyPair shouldBe singletonKeyPair })
        }

    @Test
    fun `execute() should throw SingletonKeyPairNotFoundException when the enforced singleton isn't on this device`() =
        runTest {
            mockServiceKeyManager.stub {
                on { getSingletonKeyPair() } doReturn null
            }

            shouldThrow<SudoEmailClient.EmailAliasException.SingletonKeyPairNotFoundException> {
                useCase(enforceSingletonKeyPair = true).execute(input())
            }

            verify(mockServiceKeyManager).getSingletonKeyPair()
        }

    @Test
    fun `execute() should reject a supplied key pair when the client doesn't allow caller managed keys`() =
        runTest {
            shouldThrow<SudoEmailClient.EmailAliasException.InvalidArgumentException> {
                useCase(allowCallerManagedKeys = false).execute(input(keyId = "suppliedKeyId"))
            }
        }
}
