/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amplifyframework.api.graphql.GraphQLResponse
import com.apollographql.apollo3.api.Optional
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.keys.KeyPair
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.inputs.ProvisionEmailAddressInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudouser.SudoUserClient
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
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.provisionEmailAddress]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailProvisionEmailAddressTest : BaseTests() {

    private val input by before {
        ProvisionEmailAddressInput(
            "example@sudoplatform.com",
            "ownershipProofToken",
        )
    }

    private val mutationResponse by before {
        DataFactory.provisionEmailAddressMutationResponse()
    }

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "subject"
            on { getRefreshToken() } doReturn "refreshToken"
        }
    }

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                provisionEmailAddressMutation(
                    any(),
                )
            } doAnswer {
                mutationResponse
            }
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { getPassword(anyString()) } doReturn ByteArray(42)
            on { getPublicKeyData(anyString()) } doReturn ByteArray(42)
            on { getPrivateKeyData(anyString()) } doReturn ByteArray(42)
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { generateKeyPair() } doReturn KeyPair(
                keyId = "keyId",
                keyRingId = "keyRingId",
                publicKey = ByteArray(42),
                privateKey = ByteArray(42),
            )
            on { getCurrentSymmetricKeyId() } doReturn "symmetricKeyId"
        }
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
            mockServiceKeyManager,
            mockApiClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `provisionEmailAddress() should return results when no error present`() = runTest {
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.provisionEmailAddress(input)
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
            folders.size shouldBe 1
            with(folders[0]) {
                id shouldBe "folderId"
                owner shouldBe "owner"
                owners.first().id shouldBe "ownerId"
                owners.first().issuer shouldBe "issuer"
                emailAddressId shouldBe "emailAddressId"
                folderName shouldBe "folderName"
                size shouldBe 0.0
                unseenCount shouldBe 0.0
                version shouldBe 1
                createdAt shouldBe Date(1L)
                updatedAt shouldBe Date(1L)
            }
        }

        verify(mockApiClient).provisionEmailAddressMutation(
            check { input ->
                input.emailAddress shouldBe "example@sudoplatform.com"
                input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                input.alias shouldBe Optional.absent()
            },
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw when email mutation response is null`() = runTest {
        mockApiClient.stub {
            onBlocking {
                provisionEmailAddressMutation(
                    any(),
                )
            }.thenAnswer {
                GraphQLResponse(null, null)
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.ProvisionFailedException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiClient).provisionEmailAddressMutation(
            check { input ->
                input.emailAddress shouldBe "example@sudoplatform.com"
                input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                input.alias shouldBe Optional.absent()
            },
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw when response has illegal format error`() = runTest {
        val testError = GraphQLResponse.Error(
            "Test generated error",
            emptyList(),
            emptyList(),
            mapOf("errorType" to "EmailValidation"),
        )
        mockApiClient.stub {
            onBlocking {
                provisionEmailAddressMutation(
                    any(),
                )
            }.thenAnswer {
                GraphQLResponse(null, listOf(testError))
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiClient).provisionEmailAddressMutation(
            check { input ->
                input.emailAddress shouldBe "example@sudoplatform.com"
                input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                input.alias shouldBe Optional.absent()
            },
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw when response has an invalid key ring error`() = runTest {
        val testError = GraphQLResponse.Error(
            "Test generated error",
            emptyList(),
            emptyList(),
            mapOf("errorType" to "InvalidKeyRingId"),
        )
        mockApiClient.stub {
            onBlocking {
                provisionEmailAddressMutation(
                    any(),
                )
            }.thenAnswer {
                GraphQLResponse(null, listOf(testError))
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.PublicKeyException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiClient).provisionEmailAddressMutation(
            check { input ->
                input.emailAddress shouldBe "example@sudoplatform.com"
                input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                input.alias shouldBe Optional.absent()
            },
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw if key manager returns null key pair`() = runTest {
        mockServiceKeyManager.stub {
            on { getKeyPairWithId(anyString()) } doReturn null
        }

        val input = ProvisionEmailAddressInput(
            "example@sudoplatform.com",
            "ownershipProofToken",
            keyId = "invalidKeyId",
        )
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<KeyNotFoundException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockServiceKeyManager).getKeyPairWithId(anyString())
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw when response has an insufficient entitlements error`() = runTest {
        val testError = GraphQLResponse.Error(
            "mock",
            emptyList(),
            emptyList(),
            mapOf("errorType" to "InsufficientEntitlementsError"),
        )
        mockApiClient.stub {
            onBlocking {
                provisionEmailAddressMutation(
                    any(),
                )
            }.thenAnswer {
                GraphQLResponse(null, listOf(testError))
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.InsufficientEntitlementsException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiClient).provisionEmailAddressMutation(
            check { input ->
                input.emailAddress shouldBe "example@sudoplatform.com"
                input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                input.alias shouldBe Optional.absent()
            },
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw when response has a policy failed error`() = runTest {
        val testError = GraphQLResponse.Error(
            "mock",
            emptyList(),
            emptyList(),
            mapOf("errorType" to "PolicyFailed"),
        )
        mockApiClient.stub {
            onBlocking {
                provisionEmailAddressMutation(
                    any(),
                )
            }.thenAnswer {
                GraphQLResponse(null, listOf(testError))
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.InsufficientEntitlementsException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiClient).provisionEmailAddressMutation(
            check { input ->
                input.emailAddress shouldBe "example@sudoplatform.com"
                input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                input.alias shouldBe Optional.absent()
            },
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw when key registration fails`() = runTest {
        mockServiceKeyManager.stub {
            on { generateKeyPair() } doThrow DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException("Mock")
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.PublicKeyException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should generate symmetric key if one doesn't exist`() = runTest {
        mockServiceKeyManager.stub {
            on { getCurrentSymmetricKeyId() } doReturn null
            on { generateNewCurrentSymmetricKey() } doReturn "symmetricKeyId"
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.provisionEmailAddress(input)
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
            folders.size shouldBe 1
            with(folders[0]) {
                id shouldBe "folderId"
                owner shouldBe "owner"
                owners.first().id shouldBe "ownerId"
                owners.first().issuer shouldBe "issuer"
                emailAddressId shouldBe "emailAddressId"
                folderName shouldBe "folderName"
                size shouldBe 0.0
                unseenCount shouldBe 0.0
                version shouldBe 1
                createdAt shouldBe Date(1L)
                updatedAt shouldBe Date(1L)
            }
        }

        verify(mockApiClient).provisionEmailAddressMutation(
            check { input ->
                input.emailAddress shouldBe "example@sudoplatform.com"
                input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                input.alias shouldBe Optional.absent()
            },
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        verify(mockServiceKeyManager).generateNewCurrentSymmetricKey()
    }
}
