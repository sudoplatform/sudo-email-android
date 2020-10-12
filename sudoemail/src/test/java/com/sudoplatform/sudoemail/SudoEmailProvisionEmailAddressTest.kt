/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.util.Base64
import com.apollographql.apollo.api.Response
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.CreatePublicKeyForEmailMutation
import com.sudoplatform.sudoemail.graphql.GetKeyRingForEmailQuery
import com.sudoplatform.sudoemail.graphql.ProvisionEmailAddressMutation
import com.sudoplatform.sudoemail.graphql.type.CreatePublicKeyInput
import com.sudoplatform.sudoemail.graphql.type.ProvisionEmailAddressInput
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.keys.DefaultPublicKeyService
import com.sudoplatform.sudoemail.keys.PublicKeyService
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudoprofiles.S3Client
import com.sudoplatform.sudoprofiles.Sudo
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
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
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [SudoEmailClient.provisionEmailAddress] using mocks and spies.
 *
 * @since 2020-08-05
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailProvisionEmailAddressTest : BaseTests() {

    private val input by before {
        ProvisionEmailAddressInput.builder()
            .emailAddress("example@sudoplatform.com")
            .keyRingId("keyRingId")
            .ownershipProofTokens(listOf("ownerProofs"))
            .build()
    }

    private val keyRingQueryRequest by before {
        GetKeyRingForEmailQuery.builder()
            .keyRingId("keyRingId")
            .build()
    }

    private val keyRingQueryResult by before {
        val item = GetKeyRingForEmailQuery.Item(
            "typename",
            "id",
            "keyId",
            "keyRingId",
            "algorithm",
            Base64.encodeAsString(*"publicKey".toByteArray()),
            "owner",
            1,
            1.0,
            1.0
        )
        GetKeyRingForEmailQuery.GetKeyRingForEmail(
            "typename",
            listOf(item),
            "nextToken"
        )
    }

    private val publicKeyRequest by before {
        CreatePublicKeyInput.builder()
            .keyId("keyId")
            .keyRingId("keyRingId")
            .algorithm("algorithm")
            .publicKey(Base64.encodeAsString(*"publicKey".toByteArray()))
            .build()
    }

    private val publicKeyResult by before {
        CreatePublicKeyForEmailMutation.CreatePublicKeyForEmail(
            "typename",
            "id",
            "keyId",
            "keyRingId",
            "algorithm",
            Base64.encodeAsString(*"publicKey".toByteArray()),
            "owner",
            1,
            1.0,
            1.0
        )
    }

    private val owners by before {
        listOf(ProvisionEmailAddressMutation.Owner("typename", "ownerId", "issuer"))
    }

    private val mutationResult by before {
        ProvisionEmailAddressMutation.ProvisionEmailAddress(
            "typename",
            "emailAddressId",
            "userId",
            "sudoId",
            "identityId",
            "keyRingId",
            owners,
            1,
            1.0,
            1.0,
            "example@sudoplatform.com"
        )
    }

    private val mutationResponse by before {
        Response.builder<ProvisionEmailAddressMutation.Data>(ProvisionEmailAddressMutation(input))
            .data(ProvisionEmailAddressMutation.Data(mutationResult))
            .build()
    }

    private val keyRingResponse by before {
        Response.builder<GetKeyRingForEmailQuery.Data>(keyRingQueryRequest)
            .data(GetKeyRingForEmailQuery.Data(keyRingQueryResult))
            .build()
    }

    private val publicKeyResponse by before {
        Response.builder<CreatePublicKeyForEmailMutation.Data>(CreatePublicKeyForEmailMutation(publicKeyRequest))
            .data(CreatePublicKeyForEmailMutation.Data(publicKeyResult))
            .build()
    }

    private val provisionHolder = CallbackHolder<ProvisionEmailAddressMutation.Data>()
    private val keyRingHolder = CallbackHolder<GetKeyRingForEmailQuery.Data>()
    private val publicKeyHolder = CallbackHolder<CreatePublicKeyForEmailMutation.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "subject"
            on { getRefreshToken() } doReturn "refreshToken"
        }
    }

    private val mockSudoClient by before {
        mock<SudoProfilesClient>().stub {
            onBlocking { getOwnershipProof(any<Sudo>(), anyString()) } doReturn "jwt"
        }
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<ProvisionEmailAddressMutation>()) } doReturn provisionHolder.mutationOperation
            on { mutate(any<CreatePublicKeyForEmailMutation>()) } doReturn publicKeyHolder.mutationOperation
            on { query(any<GetKeyRingForEmailQuery>()) } doReturn keyRingHolder.queryOperation
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { getPassword(anyString()) } doReturn ByteArray(42)
            on { getPublicKeyData(anyString()) } doReturn ByteArray(42)
            on { getPrivateKeyData(anyString()) } doReturn ByteArray(42)
        }
    }

    private val mockDeviceKeyManager by before {
        DefaultDeviceKeyManager(
            mockContext,
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger
        )
    }

    private val publicKeyService by before {
        DefaultPublicKeyService(
            mockDeviceKeyManager,
            mockAppSyncClient,
            mockLogger
        )
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking { upload(any(), anyString()) } doReturn "42"
        }
    }

    private val client by before {
        DefaultSudoEmailClient(
            mockContext,
            mockAppSyncClient,
            mockUserClient,
            mockSudoClient,
            mockLogger,
            mockDeviceKeyManager,
            publicKeyService,
            "region",
            "identityBucket",
            "transientBucket",
            mockS3Client,
            mockS3Client
        )
    }

    @Before
    fun init() {
        provisionHolder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockContext, mockUserClient, mockSudoClient, mockKeyManager, mockAppSyncClient, mockS3Client)
    }

    @Test
    fun `provisionEmailAddress() should return results when no error present`() = runBlocking<Unit> {

        provisionHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.provisionEmailAddress("example@sudoplatform.com", "sudoId")
        }
        deferredResult.start()

        delay(100L)
        keyRingHolder.callback shouldNotBe null
        keyRingHolder.callback?.onResponse(keyRingResponse)

        delay(100L)
        publicKeyHolder.callback shouldNotBe null
        publicKeyHolder.callback?.onResponse(publicKeyResponse)

        delay(100L)
        provisionHolder.callback shouldNotBe null
        provisionHolder.callback?.onResponse(mutationResponse)

        val result = deferredResult.await()
        result shouldNotBe null
        with(result) {
            id shouldBe "emailAddressId"
            emailAddress shouldBe "example@sudoplatform.com"
            userId shouldBe "userId"
            sudoId shouldBe "sudoId"
            owners.first().id shouldBe "ownerId"
            owners.first().issuer shouldBe "issuer"
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
        }

        verify(mockAppSyncClient).mutate(any<ProvisionEmailAddressMutation>())
        verify(mockAppSyncClient).query(any<GetKeyRingForEmailQuery>())
        verify(mockAppSyncClient).mutate(any<CreatePublicKeyForEmailMutation>())
        verify(mockSudoClient).getOwnershipProof(any<Sudo>(), anyString())
        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
        verify(mockKeyManager).getPrivateKeyData(anyString())
        verify(mockUserClient, times(2)).getSubject()
    }

    @Test
    fun `provisionEmailAddress() should throw when public key response is null`() = runBlocking<Unit> {

        provisionHolder.callback shouldBe null
        keyRingHolder.callback shouldBe null
        publicKeyHolder.callback shouldBe null

        val nullPublicKeyResponse by before {
            Response.builder<CreatePublicKeyForEmailMutation.Data>(CreatePublicKeyForEmailMutation(publicKeyRequest))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.PublicKeyException> {
                client.provisionEmailAddress("example@sudoplatform.com", "sudoId")
            }
        }
        deferredResult.start()
        delay(100L)

        keyRingHolder.callback shouldNotBe null
        keyRingHolder.callback?.onResponse(keyRingResponse)

        delay(100L)
        publicKeyHolder.callback shouldNotBe null
        publicKeyHolder.callback?.onResponse(nullPublicKeyResponse)

        verify(mockAppSyncClient).query(any<GetKeyRingForEmailQuery>())
        verify(mockAppSyncClient).mutate(any<CreatePublicKeyForEmailMutation>())
        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
        verify(mockKeyManager).getPrivateKeyData(anyString())
        verify(mockUserClient).getSubject()
    }

    @Test
    fun `provisionEmailAddress() should throw when email mutation response is null`() = runBlocking<Unit> {

        provisionHolder.callback shouldBe null
        keyRingHolder.callback shouldBe null
        publicKeyHolder.callback shouldBe null

        val nullProvisionResponse by before {
            Response.builder<ProvisionEmailAddressMutation.Data>(ProvisionEmailAddressMutation(input))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.ProvisionFailedException> {
                client.provisionEmailAddress("example@sudoplatform.com", "sudoId")
            }
        }
        deferredResult.start()
        delay(100L)

        keyRingHolder.callback shouldNotBe null
        keyRingHolder.callback?.onResponse(keyRingResponse)

        delay(100L)
        publicKeyHolder.callback shouldNotBe null
        publicKeyHolder.callback?.onResponse(publicKeyResponse)

        delay(100L)
        provisionHolder.callback shouldNotBe null
        provisionHolder.callback?.onResponse(nullProvisionResponse)

        verify(mockAppSyncClient).query(any<GetKeyRingForEmailQuery>())
        verify(mockAppSyncClient).mutate(any<CreatePublicKeyForEmailMutation>())
        verify(mockAppSyncClient).mutate(any<ProvisionEmailAddressMutation>())
        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
        verify(mockKeyManager).getPrivateKeyData(anyString())
        verify(mockUserClient, times(2)).getSubject()
        verify(mockSudoClient).getOwnershipProof(any<Sudo>(), anyString())
    }

    @Test
    fun `provisionEmailAddress() should throw when response has illegal format error`() = runBlocking<Unit> {

        provisionHolder.callback shouldBe null
        keyRingHolder.callback shouldBe null
        publicKeyHolder.callback shouldBe null

        val errorProvisionResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "EmailValidation")
            )
            Response.builder<ProvisionEmailAddressMutation.Data>(ProvisionEmailAddressMutation(input))
                .errors(listOf(error))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
                client.provisionEmailAddress("example@sudoplatform.com", "sudoId")
            }
        }
        deferredResult.start()
        delay(100L)

        keyRingHolder.callback shouldNotBe null
        keyRingHolder.callback?.onResponse(keyRingResponse)

        delay(100L)
        publicKeyHolder.callback shouldNotBe null
        publicKeyHolder.callback?.onResponse(publicKeyResponse)

        delay(100L)
        provisionHolder.callback shouldNotBe null
        provisionHolder.callback?.onResponse(errorProvisionResponse)

        verify(mockAppSyncClient).query(any<GetKeyRingForEmailQuery>())
        verify(mockAppSyncClient).mutate(any<CreatePublicKeyForEmailMutation>())
        verify(mockAppSyncClient).mutate(any<ProvisionEmailAddressMutation>())
        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
        verify(mockKeyManager).getPrivateKeyData(anyString())
        verify(mockUserClient, times(2)).getSubject()
        verify(mockSudoClient).getOwnershipProof(any<Sudo>(), anyString())
    }

    @Test
    fun `provisionEmailAddress() should throw when response has an invalid key ring error`() = runBlocking<Unit> {

        provisionHolder.callback shouldBe null
        keyRingHolder.callback shouldBe null
        publicKeyHolder.callback shouldBe null

        val errorProvisionResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "InvalidKeyRingId")
            )
            Response.builder<ProvisionEmailAddressMutation.Data>(ProvisionEmailAddressMutation(input))
                .errors(listOf(error))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.PublicKeyException> {
                client.provisionEmailAddress("example@sudoplatform.com", "sudoId")
            }
        }
        deferredResult.start()
        delay(100L)

        keyRingHolder.callback shouldNotBe null
        keyRingHolder.callback?.onResponse(keyRingResponse)

        delay(100L)
        publicKeyHolder.callback shouldNotBe null
        publicKeyHolder.callback?.onResponse(publicKeyResponse)

        delay(100L)
        provisionHolder.callback shouldNotBe null
        provisionHolder.callback?.onResponse(errorProvisionResponse)

        verify(mockAppSyncClient).query(any<GetKeyRingForEmailQuery>())
        verify(mockAppSyncClient).mutate(any<CreatePublicKeyForEmailMutation>())
        verify(mockAppSyncClient).mutate(any<ProvisionEmailAddressMutation>())
        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
        verify(mockKeyManager).getPrivateKeyData(anyString())
        verify(mockUserClient, times(2)).getSubject()
        verify(mockSudoClient).getOwnershipProof(any<Sudo>(), anyString())
    }

    @Test
    fun `provisionEmailAddress() should throw when response has an entitlement exceeded error`() = runBlocking<Unit> {

        provisionHolder.callback shouldBe null
        keyRingHolder.callback shouldBe null
        publicKeyHolder.callback shouldBe null

        val errorProvisionResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "EntitlementExceededError")
            )
            Response.builder<ProvisionEmailAddressMutation.Data>(ProvisionEmailAddressMutation(input))
                .errors(listOf(error))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.EntitlementExceededException> {
                client.provisionEmailAddress("example@sudoplatform.com", "sudoId")
            }
        }
        deferredResult.start()
        delay(100L)

        keyRingHolder.callback shouldNotBe null
        keyRingHolder.callback?.onResponse(keyRingResponse)

        delay(100L)
        publicKeyHolder.callback shouldNotBe null
        publicKeyHolder.callback?.onResponse(publicKeyResponse)

        delay(100L)
        provisionHolder.callback shouldNotBe null
        provisionHolder.callback?.onResponse(errorProvisionResponse)

        verify(mockAppSyncClient).query(any<GetKeyRingForEmailQuery>())
        verify(mockAppSyncClient).mutate(any<CreatePublicKeyForEmailMutation>())
        verify(mockAppSyncClient).mutate(any<ProvisionEmailAddressMutation>())
        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
        verify(mockKeyManager).getPrivateKeyData(anyString())
        verify(mockUserClient, times(2)).getSubject()
        verify(mockSudoClient).getOwnershipProof(any<Sudo>(), anyString())
    }

    @Test
    fun `provisionEmailAddress() should throw when response has a policy failed error`() = runBlocking<Unit> {

        provisionHolder.callback shouldBe null
        keyRingHolder.callback shouldBe null
        publicKeyHolder.callback shouldBe null

        val errorProvisionResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "PolicyFailed")
            )
            Response.builder<ProvisionEmailAddressMutation.Data>(ProvisionEmailAddressMutation(input))
                .errors(listOf(error))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.EntitlementExceededException> {
                client.provisionEmailAddress("example@sudoplatform.com", "sudoId")
            }
        }
        deferredResult.start()
        delay(100L)

        keyRingHolder.callback shouldNotBe null
        keyRingHolder.callback?.onResponse(keyRingResponse)

        delay(100L)
        publicKeyHolder.callback shouldNotBe null
        publicKeyHolder.callback?.onResponse(publicKeyResponse)

        delay(100L)
        provisionHolder.callback shouldNotBe null
        provisionHolder.callback?.onResponse(errorProvisionResponse)

        verify(mockAppSyncClient).query(any<GetKeyRingForEmailQuery>())
        verify(mockAppSyncClient).mutate(any<CreatePublicKeyForEmailMutation>())
        verify(mockAppSyncClient).mutate(any<ProvisionEmailAddressMutation>())
        verify(mockKeyManager).getPassword(anyString())
        verify(mockKeyManager).getPublicKeyData(anyString())
        verify(mockKeyManager).getPrivateKeyData(anyString())
        verify(mockUserClient, times(2)).getSubject()
        verify(mockSudoClient).getOwnershipProof(any<Sudo>(), anyString())
    }

    @Test
    fun `provisionEmailAddress() should not block coroutine cancellation exception`() = runBlocking<Unit> {

        mockKeyManager.stub {
            on { getPassword(anyString()) } doThrow CancellationException("mock")
        }

        shouldThrow<CancellationException> {
            client.provisionEmailAddress("example@sudoplatform.com", "sudoId")
        }
        verify(mockKeyManager).getPassword(anyString())
    }

    @Test
    fun `provisionEmailAddress() should throw when key registration fails`() = runBlocking<Unit> {

        mockKeyManager.stub {
            on { getPassword(anyString()) } doThrow PublicKeyService.PublicKeyServiceException.KeyCreateException("mock")
        }

        shouldThrow<SudoEmailClient.EmailAddressException.PublicKeyException> {
            client.provisionEmailAddress("example@sudoplatform.com", "sudoId")
        }
        verify(mockKeyManager).getPassword(anyString())
    }
}
