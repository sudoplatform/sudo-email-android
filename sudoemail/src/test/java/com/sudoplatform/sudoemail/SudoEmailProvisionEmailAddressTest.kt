/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.apollographql.apollo3.api.Optional
import com.sudoplatform.sudoemail.graphql.ProvisionEmailAddressMutation
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
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
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
import com.sudoplatform.sudoemail.graphql.type.ProvisionEmailAddressInput as ProvisionEmailAddressRequest

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
        JSONObject(
            """
                {
                    'provisionEmailAddress': {
                        '__typename': 'EmailAddress',
                        'id': 'emailAddressId',
                        'owner': 'owner',
                        'owners': [{
                            '__typename': 'Owner',
                            'id': 'ownerId',
                            'issuer': 'issuer'
                        }],
                        'identityId': 'identityId',
                        'keyRingId': 'keyRingId',
                        'keyIds': [],
                        'version': '1',
                        'createdAtEpochMs': 1.0,
                        'updatedAtEpochMs': 1.0,
                        'emailAddress': 'example@sudoplatform.com',
                        'size': 0.0,
                        'numberOfEmailMessages': 0,
                        'folders': [{
                            '__typename': 'EmailFolder',
                            'id': 'folderId',
                            'owner': 'owner',
                            'owners': [{
                                '__typename': 'Owner',
                                'id': 'ownerId',
                                'issuer': 'issuer'
                            }],
                            'version': 1,
                            'createdAtEpochMs': 1.0,
                            'updatedAtEpochMs': 1.0,
                            'emailAddressId': 'emailAddressId',
                            'folderName': 'folderName',
                            'size': 0.0,
                            'unseenCount': 0.0,
                            'ttl': 1.0
                        }]
                    }
                }
            """.trimIndent(),
        )
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

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(ProvisionEmailAddressMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(mutationResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
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
            GraphQLClient(mockApiCategory),
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
            mockApiCategory,
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

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe ProvisionEmailAddressMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as ProvisionEmailAddressRequest
                input.emailAddress shouldBe "example@sudoplatform.com"
                input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                input.alias shouldBe Optional.absent()
            },
            any(),
            any(),
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `provisionEmailAddress() should throw when email mutation response is null`() = runTest {
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(ProvisionEmailAddressMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            }.thenAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(null, null),
                )
                mock<GraphQLOperation<String>>()
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.ProvisionFailedException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe ProvisionEmailAddressMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as ProvisionEmailAddressRequest
                input.emailAddress shouldBe "example@sudoplatform.com"
                input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                input.alias shouldBe Optional.absent()
            },
            any(),
            any(),
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
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(ProvisionEmailAddressMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            }.thenAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(null, listOf(testError)),
                )
                mock<GraphQLOperation<String>>()
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe ProvisionEmailAddressMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as ProvisionEmailAddressRequest
                input.emailAddress shouldBe "example@sudoplatform.com"
                input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                input.alias shouldBe Optional.absent()
            },
            any(),
            any(),
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
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(ProvisionEmailAddressMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            }.thenAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(null, listOf(testError)),
                )
                mock<GraphQLOperation<String>>()
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.PublicKeyException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe ProvisionEmailAddressMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as ProvisionEmailAddressRequest
                input.emailAddress shouldBe "example@sudoplatform.com"
                input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                input.alias shouldBe Optional.absent()
            },
            any(),
            any(),
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
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(ProvisionEmailAddressMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            }.thenAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(null, listOf(testError)),
                )
                mock<GraphQLOperation<String>>()
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.InsufficientEntitlementsException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe ProvisionEmailAddressMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as ProvisionEmailAddressRequest
                input.emailAddress shouldBe "example@sudoplatform.com"
                input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                input.alias shouldBe Optional.absent()
            },
            any(),
            any(),
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
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(ProvisionEmailAddressMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            }.thenAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(null, listOf(testError)),
                )
                mock<GraphQLOperation<String>>()
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.InsufficientEntitlementsException> {
                client.provisionEmailAddress(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe ProvisionEmailAddressMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as ProvisionEmailAddressRequest
                input.emailAddress shouldBe "example@sudoplatform.com"
                input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                input.alias shouldBe Optional.absent()
            },
            any(),
            any(),
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

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe ProvisionEmailAddressMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as ProvisionEmailAddressRequest
                input.emailAddress shouldBe "example@sudoplatform.com"
                input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                input.alias shouldBe Optional.absent()
            },
            any(),
            any(),
        )
        verify(mockServiceKeyManager).generateKeyPair()
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        verify(mockServiceKeyManager).generateNewCurrentSymmetricKey()
    }
}
