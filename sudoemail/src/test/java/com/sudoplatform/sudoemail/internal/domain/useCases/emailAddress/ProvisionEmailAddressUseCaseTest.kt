/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.keys.KeyPair
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [ProvisionEmailAddressUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class ProvisionEmailAddressUseCaseTest : BaseTests() {
    private val sealedString = "sealedString".toByteArray()
    private val symmetricKeyId = "symmetricKeyId"

    private val sealedEmailAddress by before {
        EntityDataFactory.getSealedEmailAddressEntity()
    }

    private val keyPair by before {
        KeyPair(
            keyId = "keyId",
            publicKey = "publicKey".toByteArray(),
            keyRingId = "keyRingId",
            privateKey = "privateKey".toByteArray(),
        )
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { provision(any()) } doReturn sealedEmailAddress
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { getCurrentSymmetricKeyId() } doReturn symmetricKeyId
            on { getKeyPairWithId(anyString()) } doReturn keyPair
            on { generateKeyPair() } doReturn keyPair
        }
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on { sealString(any(), any()) } doReturn sealedString
        }
    }

    private val useCase by before {
        ProvisionEmailAddressUseCase(
            mockEmailAddressService,
            mockServiceKeyManager,
            mockSealingService,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockEmailAddressService,
            mockServiceKeyManager,
            mockSealingService,
        )
    }

    @Test
    fun `execute() should return unsealed email address when no error present`() =
        runTest {
            val input =
                ProvisionEmailAddressUseCaseInput(
                    emailAddress = "example@sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            with(result) {
                id shouldBe mockEmailAddressId
                owner shouldBe mockOwner
                emailAddress shouldBe "example@sudoplatform.com"
                alias shouldBe null
                folders.size shouldBe 1
            }

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockEmailAddressService).provision(
                check { request ->
                    request.emailAddress shouldBe "example@sudoplatform.com"
                    request.ownershipProofToken shouldBe "ownershipProofToken"
                    request.alias shouldBe null
                    request.keyPair shouldBe keyPair
                },
            )
        }

    @Test
    fun `execute() should seal alias when provided`() =
        runTest {
            val input =
                ProvisionEmailAddressUseCaseInput(
                    emailAddress = "example@sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                    alias = "My Email",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.alias shouldBe "My Email"

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockSealingService).sealString(symmetricKeyId, input.alias!!.toByteArray())
            verify(mockEmailAddressService).provision(
                check { request ->
                    request.emailAddress shouldBe "example@sudoplatform.com"
                    request.ownershipProofToken shouldBe "ownershipProofToken"
                    request.alias shouldBe
                        SealedAttributeInput(
                            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                            keyId = symmetricKeyId,
                            plainTextType = "string",
                            base64EncodedSealedData = String(Base64.encode(sealedString), Charsets.UTF_8),
                        )
                    request.keyPair shouldBe keyPair
                },
            )
        }

    @Test
    fun `execute() should use existing key pair when keyId provided`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { provision(any()) } doReturn sealedEmailAddress
            }

            val input =
                ProvisionEmailAddressUseCaseInput(
                    emailAddress = "example@sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                    keyId = "existingKeyId",
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockServiceKeyManager).getKeyPairWithId("existingKeyId")
            verify(mockEmailAddressService).provision(
                check { request ->
                    request.keyPair shouldBe keyPair
                },
            )
        }

    @Test
    fun `execute() should generate symmetric key if one doesn't exist`() =
        runTest {
            mockServiceKeyManager.stub {
                on { getCurrentSymmetricKeyId() } doReturn null
                on { generateNewCurrentSymmetricKey() } doReturn "newSymmetricKeyId"
            }
            mockEmailAddressService.stub {
                onBlocking { provision(any()) } doReturn sealedEmailAddress
            }

            val input =
                ProvisionEmailAddressUseCaseInput(
                    emailAddress = "example@sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockServiceKeyManager).generateNewCurrentSymmetricKey()
            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockEmailAddressService).provision(any())
        }

    @Test
    fun `execute() should throw KeyNotFoundException when keyId is invalid`() =
        runTest {
            mockServiceKeyManager.stub {
                on { getKeyPairWithId(anyString()) } doReturn null
            }

            val input =
                ProvisionEmailAddressUseCaseInput(
                    emailAddress = "example@sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                    keyId = "invalidKeyId",
                )

            shouldThrow<KeyNotFoundException> {
                useCase.execute(input)
            }

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockServiceKeyManager).getKeyPairWithId("invalidKeyId")
        }

    @Test
    fun `execute() should throw when key generation fails`() =
        runTest {
            mockServiceKeyManager.stub {
                on { generateKeyPair() } doThrow DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException("Mock")
            }

            val input =
                ProvisionEmailAddressUseCaseInput(
                    emailAddress = "example@sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                )

            shouldThrow<SudoEmailClient.EmailAddressException.PublicKeyException> {
                useCase.execute(input)
            }

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockServiceKeyManager).generateKeyPair()
        }

    @Test
    fun `execute() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { provision(any()) } doThrow NotAuthorizedException("Mock")
            }

            val input =
                ProvisionEmailAddressUseCaseInput(
                    emailAddress = "example@sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                )

            shouldThrow<SudoEmailClient.EmailAddressException.AuthenticationException> {
                useCase.execute(input)
            }

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockEmailAddressService).provision(any())
        }

    @Test
    fun `execute() should transform folders correctly`() =
        runTest {
            val sealedFolder =
                EntityDataFactory.getSealedEmailFolderEntity(
                    id = mockFolderId,
                    folderName = "INBOX",
                )
            val sealedAddress =
                EntityDataFactory.getSealedEmailAddressEntity(
                    folders = listOf(sealedFolder),
                )

            mockServiceKeyManager.stub {
                on { getCurrentSymmetricKeyId() } doReturn "symmetricKeyId"
                on { generateKeyPair() } doReturn keyPair
            }
            mockEmailAddressService.stub {
                onBlocking { provision(any()) } doReturn sealedAddress
            }

            val input =
                ProvisionEmailAddressUseCaseInput(
                    emailAddress = "example@sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.folders.size shouldBe 1
            with(result.folders[0]) {
                id shouldBe mockFolderId
                folderName shouldBe "INBOX"
            }

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockServiceKeyManager).generateKeyPair()
            verify(mockEmailAddressService).provision(any())
        }
}
