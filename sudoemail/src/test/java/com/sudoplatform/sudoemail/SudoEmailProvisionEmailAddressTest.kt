/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.util.Base64
import com.apollographql.apollo.api.Response
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.ProvisionEmailAddressMutation
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddress
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.fragment.EmailFolder
import com.sudoplatform.sudoemail.graphql.type.ProvisionEmailAddressPublicKeyInput
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date
import com.sudoplatform.sudoemail.graphql.type.ProvisionEmailAddressInput as ProvisionEmailAddressRequest

/**
 * Test the correct operation of [SudoEmailClient.provisionEmailAddress]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailProvisionEmailAddressTest : BaseTests() {

    private val input by before {
        ProvisionEmailAddressRequest.builder()
            .emailAddress("example@sudoplatform.com")
            .key(
                ProvisionEmailAddressPublicKeyInput.builder()
                    .algorithm("RSAEncryptionOAEPAESCBC")
                    .keyId("keyId")
                    .publicKey(Base64.encodeAsString(*"publicKey".toByteArray()))
                    .build(),
            )
            .ownershipProofTokens(listOf("ownershipProofToken"))
            .build()
    }

    private val owners by before {
        listOf(EmailAddressWithoutFolders.Owner("typename", "ownerId", "issuer"))
    }

    private val folderOwners by before {
        listOf(EmailFolder.Owner("typename", "ownerId", "issuer"))
    }

    private val folders by before {
        listOf(
            EmailAddress.Folder(
                "typename",
                EmailAddress.Folder.Fragments(
                    EmailFolder(
                        "EmailFolder",
                        "folderId",
                        "owner",
                        folderOwners,
                        1,
                        1.0,
                        1.0,
                        "emailAddressId",
                        "folderName",
                        0.0,
                        0.0,
                        1.0,
                    ),
                ),
            ),
        )
    }

    private val mutationResult by before {
        ProvisionEmailAddressMutation.ProvisionEmailAddress(
            "typename",
            ProvisionEmailAddressMutation.ProvisionEmailAddress.Fragments(
                EmailAddress(
                    "typename",
                    folders,
                    EmailAddress.Fragments(
                        EmailAddressWithoutFolders(
                            "typename",
                            "emailAddressId",
                            "owner",
                            owners,
                            "identityId",
                            "keyRingId",
                            emptyList(),
                            1,
                            1.0,
                            1.0,
                            null,
                            "example@sudoplatform.com",
                            0.0,
                            0,
                            null,
                        ),
                    ),
                ),
            ),
        )
    }

    private val mutationResponse by before {
        Response.builder<ProvisionEmailAddressMutation.Data>(ProvisionEmailAddressMutation(input))
            .data(ProvisionEmailAddressMutation.Data(mutationResult))
            .build()
    }

    private val provisionHolder = CallbackHolder<ProvisionEmailAddressMutation.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "subject"
            on { getRefreshToken() } doReturn "refreshToken"
        }
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<ProvisionEmailAddressMutation>()) } doReturn provisionHolder.mutationOperation
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
            mockAppSyncClient,
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

    @Before
    fun init() {
        provisionHolder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockContext,
            mockUserClient,
            mockKeyManager,
            mockServiceKeyManager,
            mockAppSyncClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `provisionEmailAddress() should return results when no error present`() = runBlocking<Unit> {
        provisionHolder.callback shouldBe null

        val input = ProvisionEmailAddressInput(
            "example@sudoplatform.com",
            "ownershipProofToken",
        )
        val deferredResult = async(Dispatchers.IO) {
            client.provisionEmailAddress(input)
        }
        deferredResult.start()

        delay(100L)
        provisionHolder.callback shouldNotBe null
        provisionHolder.callback?.onResponse(mutationResponse)

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
            lastReceivedAt shouldBe null
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

        verify(mockAppSyncClient).mutate<
            ProvisionEmailAddressMutation.Data,
            ProvisionEmailAddressMutation,
            ProvisionEmailAddressMutation.Variables,
            >(
            check {
                it.variables().input().emailAddress() shouldBe "example@sudoplatform.com"
                it.variables().input().ownershipProofTokens() shouldBe listOf("ownershipProofToken")
                it.variables().input().alias() shouldBe null
            },
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw when email mutation response is null`() = runBlocking<Unit> {
        provisionHolder.callback shouldBe null

        val nullProvisionResponse by before {
            Response.builder<ProvisionEmailAddressMutation.Data>(ProvisionEmailAddressMutation(input))
                .data(null)
                .build()
        }

        val input = ProvisionEmailAddressInput(
            "example@sudoplatform.com",
            "ownershipProofToken",
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.ProvisionFailedException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()

        delay(100L)
        provisionHolder.callback shouldNotBe null
        provisionHolder.callback?.onResponse(nullProvisionResponse)

        verify(mockAppSyncClient).mutate<
            ProvisionEmailAddressMutation.Data,
            ProvisionEmailAddressMutation,
            ProvisionEmailAddressMutation.Variables,
            >(
            check {
                it.variables().input().emailAddress() shouldBe "example@sudoplatform.com"
                it.variables().input().ownershipProofTokens() shouldBe listOf("ownershipProofToken")
                it.variables().input().alias() shouldBe null
            },
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw when response has illegal format error`() = runBlocking<Unit> {
        provisionHolder.callback shouldBe null

        val errorProvisionResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "EmailValidation"),
            )
            Response.builder<ProvisionEmailAddressMutation.Data>(ProvisionEmailAddressMutation(input))
                .errors(listOf(error))
                .build()
        }

        val input = ProvisionEmailAddressInput(
            "example@sudoplatform.com",
            "ownershipProofToken",
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()

        delay(100L)
        provisionHolder.callback shouldNotBe null
        provisionHolder.callback?.onResponse(errorProvisionResponse)

        verify(mockAppSyncClient).mutate<
            ProvisionEmailAddressMutation.Data,
            ProvisionEmailAddressMutation,
            ProvisionEmailAddressMutation.Variables,
            >(
            check {
                it.variables().input().emailAddress() shouldBe "example@sudoplatform.com"
                it.variables().input().ownershipProofTokens() shouldBe listOf("ownershipProofToken")
                it.variables().input().alias() shouldBe null
            },
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw when response has an invalid key ring error`() = runBlocking<Unit> {
        provisionHolder.callback shouldBe null

        val errorProvisionResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "InvalidKeyRingId"),
            )
            Response.builder<ProvisionEmailAddressMutation.Data>(ProvisionEmailAddressMutation(input))
                .errors(listOf(error))
                .build()
        }

        val input = ProvisionEmailAddressInput(
            "example@sudoplatform.com",
            "ownershipProofToken",
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.PublicKeyException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()

        delay(100L)
        provisionHolder.callback shouldNotBe null
        provisionHolder.callback?.onResponse(errorProvisionResponse)

        verify(mockAppSyncClient).mutate<
            ProvisionEmailAddressMutation.Data,
            ProvisionEmailAddressMutation,
            ProvisionEmailAddressMutation.Variables,
            >(
            check {
                it.variables().input().emailAddress() shouldBe "example@sudoplatform.com"
                it.variables().input().ownershipProofTokens() shouldBe listOf("ownershipProofToken")
                it.variables().input().alias() shouldBe null
            },
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw if key manager returns null key pair`() = runBlocking<Unit> {
        mockServiceKeyManager.stub {
            on { getKeyPairWithId(anyString()) } doReturn null
        }

        val input = ProvisionEmailAddressInput(
            "example@sudoplatform.com",
            "ownershipProofToken",
            keyId = "invalidKeyId",
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<KeyNotFoundException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockServiceKeyManager).getKeyPairWithId(anyString())
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw when response has an insufficient entitlements error`() = runBlocking<Unit> {
        provisionHolder.callback shouldBe null

        val errorProvisionResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "InsufficientEntitlementsError"),
            )
            Response.builder<ProvisionEmailAddressMutation.Data>(ProvisionEmailAddressMutation(input))
                .errors(listOf(error))
                .build()
        }

        val input = ProvisionEmailAddressInput(
            "example@sudoplatform.com",
            "ownershipProofToken",
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.InsufficientEntitlementsException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()

        delay(100L)
        provisionHolder.callback shouldNotBe null
        provisionHolder.callback?.onResponse(errorProvisionResponse)

        verify(mockAppSyncClient).mutate<
            ProvisionEmailAddressMutation.Data,
            ProvisionEmailAddressMutation,
            ProvisionEmailAddressMutation.Variables,
            >(
            check {
                it.variables().input().emailAddress() shouldBe "example@sudoplatform.com"
                it.variables().input().ownershipProofTokens() shouldBe listOf("ownershipProofToken")
                it.variables().input().alias() shouldBe null
            },
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw when response has a policy failed error`() = runBlocking<Unit> {
        provisionHolder.callback shouldBe null

        val errorProvisionResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "PolicyFailed"),
            )
            Response.builder<ProvisionEmailAddressMutation.Data>(ProvisionEmailAddressMutation(input))
                .errors(listOf(error))
                .build()
        }

        val input = ProvisionEmailAddressInput(
            "example@sudoplatform.com",
            "ownershipProofToken",
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.InsufficientEntitlementsException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()

        delay(100L)
        provisionHolder.callback shouldNotBe null
        provisionHolder.callback?.onResponse(errorProvisionResponse)

        verify(mockAppSyncClient).mutate<
            ProvisionEmailAddressMutation.Data,
            ProvisionEmailAddressMutation,
            ProvisionEmailAddressMutation.Variables,
            >(
            check {
                it.variables().input().emailAddress() shouldBe "example@sudoplatform.com"
                it.variables().input().ownershipProofTokens() shouldBe listOf("ownershipProofToken")
                it.variables().input().alias() shouldBe null
            },
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw when key registration fails`() = runBlocking<Unit> {
        mockServiceKeyManager.stub {
            on { generateKeyPair() } doThrow DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException("Mock")
        }

        val input = ProvisionEmailAddressInput(
            "example@sudoplatform.com",
            "ownershipProofToken",
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.PublicKeyException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should generate symmetric key if one doesn't exist`() = runBlocking<Unit> {
        provisionHolder.callback shouldBe null

        mockServiceKeyManager.stub {
            on { getCurrentSymmetricKeyId() } doReturn null
            on { generateNewCurrentSymmetricKey() } doReturn "symmetricKeyId"
        }

        val input = ProvisionEmailAddressInput(
            "example@sudoplatform.com",
            "ownershipProofToken",
        )
        val deferredResult = async(Dispatchers.IO) {
            client.provisionEmailAddress(input)
        }
        deferredResult.start()

        delay(100L)
        provisionHolder.callback shouldNotBe null
        provisionHolder.callback?.onResponse(mutationResponse)

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
            lastReceivedAt shouldBe null
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

        verify(mockAppSyncClient).mutate<
            ProvisionEmailAddressMutation.Data,
            ProvisionEmailAddressMutation,
            ProvisionEmailAddressMutation.Variables,
            >(
            check {
                it.variables().input().emailAddress() shouldBe "example@sudoplatform.com"
                it.variables().input().ownershipProofTokens() shouldBe listOf("ownershipProofToken")
                it.variables().input().alias() shouldBe null
            },
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        verify(mockServiceKeyManager).generateNewCurrentSymmetricKey()
    }
}
