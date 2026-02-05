/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailAddress

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amplifyframework.api.graphql.GraphQLResponse
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.data.DataFactory.getEmailFolder
import com.sudoplatform.sudoemail.graphql.GetEmailAddressQuery
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddress
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.fragment.SealedAttribute
import com.sudoplatform.sudoemail.graphql.type.ProvisionEmailAddressInput
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.CheckEmailAddressAvailabilityRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.DeprovisionEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.GetEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesForSudoIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.LookupEmailAddressesPublicInfoRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ProvisionEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PublicKeyFormatEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.UpdateEmailAddressMetadataRequest
import com.sudoplatform.sudoemail.keys.KeyPair
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
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

/**
 * Test the correct operation of [GraphQLEmailAddressService]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GraphQLEmailAddressServiceTest : BaseTests() {
    override val mockApiClient by before {
        mock<ApiClient>()
    }

    private val instanceUnderTest by before {
        GraphQLEmailAddressService(
            mockApiClient,
            mockLogger,
        )
    }

    private val keyPair by before {
        val mockKeyPair = mock<KeyPair>()
        mockKeyPair.stub {
            on { keyId } doReturn mockKeyId
            on { keyRingId } doReturn "keyRingId"
            on { publicKey } doReturn "publicKey".toByteArray()
            on { privateKey } doReturn "privateKey".toByteArray()
            on { getKeyFormat() } doReturn PublicKeyFormatEntity.RSA_PUBLIC_KEY
        }
        mockKeyPair
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockApiClient,
        )
    }

    /** Begin ProvisionEmailAddressTests */

    @Test
    fun `provisionEmailAddress() should return sealed email address when successful`() =
        runTest {
            val provisionEmailAddressMutationResponse = DataFactory.provisionEmailAddressMutationResponse()
            mockApiClient.stub {
                onBlocking {
                    provisionEmailAddressMutation(any<ProvisionEmailAddressInput>())
                } doReturn provisionEmailAddressMutationResponse
            }
            val request =
                ProvisionEmailAddressRequest(
                    emailAddress = "example@internal.sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                    alias = null,
                    keyPair = keyPair,
                )

            val result = instanceUnderTest.provision(request)

            result shouldNotBe null
            result.emailAddress shouldBe "example@internal.sudoplatform.com"

            verify(mockApiClient).provisionEmailAddressMutation(
                check { input ->
                    input.emailAddress shouldBe "example@internal.sudoplatform.com"
                    input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                    input.alias shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `provisionEmailAddress() should include alias when provided`() =
        runTest {
            val sealedAlias =
                SealedAttributeInput(
                    algorithm = "algorithm",
                    keyId = mockKeyId,
                    plainTextType = "string",
                    base64EncodedSealedData = "base64EncodedSealedData",
                )
            val mutationResponse =
                DataFactory.provisionEmailAddressMutationResponse(
                    EmailAddress(
                        "EmailAddress",
                        folders =
                            listOf(
                                EmailAddress.Folder(
                                    "__typename",
                                    getEmailFolder(),
                                ),
                            ),
                        DataFactory.getEmailAddressWithoutFolder(
                            alias =
                                EmailAddressWithoutFolders.Alias(
                                    "SealedAttribute",
                                    sealedAttribute =
                                        SealedAttribute(
                                            "algorithm",
                                            mockKeyId,
                                            "string",
                                            "base64EncodedSealedData",
                                        ),
                                ),
                        ),
                    ),
                )
            mockApiClient.stub {
                onBlocking {
                    provisionEmailAddressMutation(any<ProvisionEmailAddressInput>())
                } doReturn mutationResponse
            }

            val request =
                ProvisionEmailAddressRequest(
                    emailAddress = "example@internal.sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                    alias = sealedAlias,
                    keyPair = keyPair,
                )

            val result = instanceUnderTest.provision(request)

            result shouldNotBe null
            result.sealedAlias shouldNotBe null
            result.sealedAlias!!.keyId shouldBe sealedAlias.keyId
            result.sealedAlias.base64EncodedSealedData shouldBe sealedAlias.base64EncodedSealedData

            verify(mockApiClient).provisionEmailAddressMutation(
                check { input ->
                    input.emailAddress shouldBe "example@internal.sudoplatform.com"
                    input.alias shouldNotBe Optional.absent()
                },
            )
        }

    @Test
    fun `provisionEmailAddress() should encode public key correctly`() =
        runTest {
            val mutationResponse = DataFactory.provisionEmailAddressMutationResponse()
            mockApiClient.stub {
                onBlocking {
                    provisionEmailAddressMutation(any())
                } doReturn mutationResponse
            }

            val request =
                ProvisionEmailAddressRequest(
                    emailAddress = "example@internal.sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                    alias = null,
                    keyPair = keyPair,
                )

            val result = instanceUnderTest.provision(request)

            result shouldNotBe null

            verify(mockApiClient).provisionEmailAddressMutation(
                check { input ->
                    input.key.keyId shouldBe mockKeyId
                    input.key.algorithm shouldBe "RSAEncryptionOAEPAESCBC"
                },
            )
        }

    @Test
    fun `provisionEmailAddress() should throw when mutation response is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    provisionEmailAddressMutation(any())
                } doReturn GraphQLResponse(null, null)
            }

            val request =
                ProvisionEmailAddressRequest(
                    emailAddress = "example@internal.sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                    alias = null,
                    keyPair = keyPair,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.ProvisionFailedException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionEmailAddressMutation(
                check { input ->
                    input.emailAddress shouldBe "example@internal.sudoplatform.com"
                    input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                    input.alias shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `provisionEmailAddress() should throw when response has illegal format error`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "EmailValidation"),
                )
            mockApiClient.stub {
                onBlocking {
                    provisionEmailAddressMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                ProvisionEmailAddressRequest(
                    emailAddress = "example@internal.sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                    alias = null,
                    keyPair = keyPair,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionEmailAddressMutation(
                check { input ->
                    input.emailAddress shouldBe "example@internal.sudoplatform.com"
                    input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                    input.alias shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `provisionEmailAddress() should throw when response has an invalid key ring error`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "InvalidKeyRingId"),
                )
            mockApiClient.stub {
                onBlocking {
                    provisionEmailAddressMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                ProvisionEmailAddressRequest(
                    emailAddress = "example@internal.sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                    alias = null,
                    keyPair = keyPair,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.PublicKeyException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionEmailAddressMutation(
                check { input ->
                    input.emailAddress shouldBe "example@internal.sudoplatform.com"
                    input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                    input.alias shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `provisionEmailAddress() should throw when response has an insufficient entitlements error`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "mock",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "InsufficientEntitlementsError"),
                )
            mockApiClient.stub {
                onBlocking {
                    provisionEmailAddressMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                ProvisionEmailAddressRequest(
                    emailAddress = "example@internal.sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                    alias = null,
                    keyPair = keyPair,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.InsufficientEntitlementsException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionEmailAddressMutation(
                check { input ->
                    input.emailAddress shouldBe "example@internal.sudoplatform.com"
                    input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                    input.alias shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `provisionEmailAddress() should throw when response has a policy failed error`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "mock",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "PolicyFailed"),
                )
            mockApiClient.stub {
                onBlocking {
                    provisionEmailAddressMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                ProvisionEmailAddressRequest(
                    emailAddress = "example@internal.sudoplatform.com",
                    ownershipProofToken = "ownershipProofToken",
                    alias = null,
                    keyPair = keyPair,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.InsufficientEntitlementsException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionEmailAddressMutation(
                check { input ->
                    input.emailAddress shouldBe "example@internal.sudoplatform.com"
                    input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                    input.alias shouldBe Optional.absent()
                },
            )
        }

    /** Begin CheckEmailAddressAvailabilityTests */
    @Test
    fun `checkEmailAddressAvailability() should return list of addresses when successful`() =
        runTest {
            val addresses = listOf("test@example.com", "user@example.com")
            val queryResponse = DataFactory.checkEmailAddressAvailabilityQueryResponse(addresses)
            mockApiClient.stub {
                onBlocking {
                    checkEmailAddressAvailabilityQuery(any())
                } doReturn queryResponse
            }

            val request =
                CheckEmailAddressAvailabilityRequest(
                    localParts = listOf("test", "user"),
                    domains = listOf("example.com"),
                )

            val result = instanceUnderTest.checkAvailability(request)

            result shouldNotBe null
            result.size shouldBe 2
            result shouldBe addresses

            verify(mockApiClient).checkEmailAddressAvailabilityQuery(
                check { input ->
                    input.localParts shouldBe listOf("test", "user")
                    input.domains shouldBe Optional.present(listOf("example.com"))
                },
            )
        }

    @Test
    fun `checkEmailAddressAvailability() should return empty list when no addresses available`() =
        runTest {
            val queryResponse = DataFactory.checkEmailAddressAvailabilityQueryResponse(emptyList())
            mockApiClient.stub {
                onBlocking {
                    checkEmailAddressAvailabilityQuery(any())
                } doReturn queryResponse
            }

            val request =
                CheckEmailAddressAvailabilityRequest(
                    localParts = listOf("test"),
                    domains = null,
                )

            val result = instanceUnderTest.checkAvailability(request)

            result shouldNotBe null
            result.size shouldBe 0

            verify(mockApiClient).checkEmailAddressAvailabilityQuery(
                check { input ->
                    input.localParts shouldBe listOf("test")
                    input.domains shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `checkEmailAddressAvailability() should pass domains as Optional absent when null`() =
        runTest {
            val addresses = listOf("test@sudoplatform.com")
            val queryResponse = DataFactory.checkEmailAddressAvailabilityQueryResponse(addresses)
            mockApiClient.stub {
                onBlocking {
                    checkEmailAddressAvailabilityQuery(any())
                } doReturn queryResponse
            }

            val request =
                CheckEmailAddressAvailabilityRequest(
                    localParts = listOf("test"),
                    domains = null,
                )

            val result = instanceUnderTest.checkAvailability(request)

            result shouldBe addresses

            verify(mockApiClient).checkEmailAddressAvailabilityQuery(
                check { input ->
                    input.domains shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `checkEmailAddressAvailability() should handle multiple local parts`() =
        runTest {
            val addresses = listOf("test1@example.com", "test2@example.com", "test3@example.com")
            val queryResponse = DataFactory.checkEmailAddressAvailabilityQueryResponse(addresses)
            mockApiClient.stub {
                onBlocking {
                    checkEmailAddressAvailabilityQuery(any())
                } doReturn queryResponse
            }

            val request =
                CheckEmailAddressAvailabilityRequest(
                    localParts = listOf("test1", "test2", "test3"),
                    domains = listOf("example.com"),
                )

            val result = instanceUnderTest.checkAvailability(request)

            result.size shouldBe 3
            result shouldBe addresses

            verify(mockApiClient).checkEmailAddressAvailabilityQuery(
                check { input ->
                    input.localParts.size shouldBe 3
                },
            )
        }

    @Test
    fun `checkEmailAddressAvailability() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    checkEmailAddressAvailabilityQuery(any())
                } doThrow NotAuthorizedException("Mock")
            }

            val request =
                CheckEmailAddressAvailabilityRequest(
                    localParts = listOf("test"),
                    domains = listOf("example.com"),
                )

            shouldThrow<SudoEmailClient.EmailAddressException.AuthenticationException> {
                instanceUnderTest.checkAvailability(request)
            }

            verify(mockApiClient).checkEmailAddressAvailabilityQuery(any())
        }

    @Test
    fun `checkEmailAddressAvailability() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            mockApiClient.stub {
                onBlocking {
                    checkEmailAddressAvailabilityQuery(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                CheckEmailAddressAvailabilityRequest(
                    localParts = listOf("test"),
                    domains = listOf("example.com"),
                )

            shouldThrow<SudoEmailClient.EmailAddressException> {
                instanceUnderTest.checkAvailability(request)
            }

            verify(mockApiClient).checkEmailAddressAvailabilityQuery(any())
        }

    @Test
    fun `checkEmailAddressAvailability() should return empty list when data is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    checkEmailAddressAvailabilityQuery(any())
                } doReturn GraphQLResponse(null, null)
            }

            val request =
                CheckEmailAddressAvailabilityRequest(
                    localParts = listOf("test"),
                    domains = listOf("example.com"),
                )

            val result = instanceUnderTest.checkAvailability(request)

            result shouldBe emptyList()

            verify(mockApiClient).checkEmailAddressAvailabilityQuery(any())
        }

    /** Begin DeprovisionEmailAddressTests */
    @Test
    fun `deprovisionEmailAddress() should return sealed email address when successful`() =
        runTest {
            val mutationResponse = DataFactory.deprovisionEmailAddressMutationResponse()
            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailAddressMutation(any())
                } doReturn mutationResponse
            }

            val request =
                DeprovisionEmailAddressRequest(
                    emailAddressId = mockEmailAddressId,
                )

            val result = instanceUnderTest.deprovision(request)

            result shouldNotBe null
            result.id shouldBe mockEmailAddressId
            result.emailAddress shouldBe "example@internal.sudoplatform.com"

            verify(mockApiClient).deprovisionEmailAddressMutation(
                check { input ->
                    input.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `deprovisionEmailAddress() should throw DeprovisionFailedException when response data is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailAddressMutation(any())
                } doReturn GraphQLResponse(null, null)
            }

            val request =
                DeprovisionEmailAddressRequest(
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.DeprovisionFailedException> {
                instanceUnderTest.deprovision(request)
            }

            verify(mockApiClient).deprovisionEmailAddressMutation(
                check { input ->
                    input.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `deprovisionEmailAddress() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailAddressMutation(any())
                } doThrow NotAuthorizedException("Mock")
            }

            val request =
                DeprovisionEmailAddressRequest(
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.AuthenticationException> {
                instanceUnderTest.deprovision(request)
            }

            verify(mockApiClient).deprovisionEmailAddressMutation(any())
        }

    @Test
    fun `deprovisionEmailAddress() should throw when response has address not found error`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "AddressNotFound"),
                )
            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailAddressMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                DeprovisionEmailAddressRequest(
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                instanceUnderTest.deprovision(request)
            }

            verify(mockApiClient).deprovisionEmailAddressMutation(any())
        }

    @Test
    fun `deprovisionEmailAddress() should throw when response has unauthorized address error`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "UnauthorizedAddress"),
                )
            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailAddressMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                DeprovisionEmailAddressRequest(
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.UnauthorizedEmailAddressException> {
                instanceUnderTest.deprovision(request)
            }

            verify(mockApiClient).deprovisionEmailAddressMutation(any())
        }

    @Test
    fun `deprovisionEmailAddress() should throw generic exception for unknown errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "UnknownError"),
                )
            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailAddressMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                DeprovisionEmailAddressRequest(
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailAddressException> {
                instanceUnderTest.deprovision(request)
            }

            verify(mockApiClient).deprovisionEmailAddressMutation(any())
        }

    @Test
    fun `deprovisionEmailAddress() should use correct email address id in mutation input`() =
        runTest {
            val mutationResponse = DataFactory.deprovisionEmailAddressMutationResponse()
            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailAddressMutation(any())
                } doReturn mutationResponse
            }

            val customEmailAddressId = "custom-email-address-id-123"
            val request =
                DeprovisionEmailAddressRequest(
                    emailAddressId = customEmailAddressId,
                )

            val result = instanceUnderTest.deprovision(request)

            result shouldNotBe null

            verify(mockApiClient).deprovisionEmailAddressMutation(
                check { input ->
                    input.emailAddressId shouldBe customEmailAddressId
                },
            )
        }

    /** Begin UpdateEmailAddressMetadataTests */
    @Test
    fun `updateEmailAddressMetadata() should return email address id when successful`() =
        runTest {
            val emailAddressId = mockEmailAddressId
            val mutationResponse = DataFactory.updateEmailAddressMetadataMutationResponse(emailAddressId)
            mockApiClient.stub {
                onBlocking {
                    updateEmailAddressMetadataMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateEmailAddressMetadataRequest(
                    id = emailAddressId,
                    alias =
                        SealedAttributeInput(
                            algorithm = "algorithm",
                            keyId = mockKeyId,
                            plainTextType = "string",
                            base64EncodedSealedData = "base64EncodedSealedData",
                        ),
                )

            val result = instanceUnderTest.updateMetadata(request)

            result shouldBe emailAddressId

            verify(mockApiClient).updateEmailAddressMetadataMutation(
                check { input ->
                    input.id shouldBe emailAddressId
                    input.values.alias shouldNotBe Optional.absent()
                },
            )
        }

    @Test
    fun `updateEmailAddressMetadata() should handle clearing alias`() =
        runTest {
            val emailAddressId = mockEmailAddressId
            val mutationResponse = DataFactory.updateEmailAddressMetadataMutationResponse(emailAddressId)
            mockApiClient.stub {
                onBlocking {
                    updateEmailAddressMetadataMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateEmailAddressMetadataRequest(
                    id = emailAddressId,
                    alias = null,
                    clearAlias = true,
                )

            val result = instanceUnderTest.updateMetadata(request)

            result shouldBe emailAddressId

            verify(mockApiClient).updateEmailAddressMetadataMutation(
                check { input ->
                    input.id shouldBe emailAddressId
                    input.values.alias shouldBe Optional.present(null)
                },
            )
        }

    @Test
    fun `updateEmailAddressMetadata() should throw UpdateFailedException when response data is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    updateEmailAddressMetadataMutation(any())
                } doReturn GraphQLResponse(null, null)
            }

            val request =
                UpdateEmailAddressMetadataRequest(
                    id = mockEmailAddressId,
                    alias = null,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.UpdateFailedException> {
                instanceUnderTest.updateMetadata(request)
            }

            verify(mockApiClient).updateEmailAddressMetadataMutation(any())
        }

    @Test
    fun `updateEmailAddressMetadata() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    updateEmailAddressMetadataMutation(any())
                } doThrow NotAuthorizedException("Mock")
            }

            val request =
                UpdateEmailAddressMetadataRequest(
                    id = mockEmailAddressId,
                    alias = null,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.AuthenticationException> {
                instanceUnderTest.updateMetadata(request)
            }

            verify(mockApiClient).updateEmailAddressMetadataMutation(any())
        }

    @Test
    fun `updateEmailAddressMetadata() should throw when response has address not found error`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "AddressNotFound"),
                )
            mockApiClient.stub {
                onBlocking {
                    updateEmailAddressMetadataMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                UpdateEmailAddressMetadataRequest(
                    id = mockEmailAddressId,
                    alias = null,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                instanceUnderTest.updateMetadata(request)
            }

            verify(mockApiClient).updateEmailAddressMetadataMutation(any())
        }

    @Test
    fun `updateEmailAddressMetadata() should throw when response has unauthorized address error`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "UnauthorizedAddress"),
                )
            mockApiClient.stub {
                onBlocking {
                    updateEmailAddressMetadataMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                UpdateEmailAddressMetadataRequest(
                    id = mockEmailAddressId,
                    alias = null,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.UnauthorizedEmailAddressException> {
                instanceUnderTest.updateMetadata(request)
            }

            verify(mockApiClient).updateEmailAddressMetadataMutation(any())
        }

    @Test
    fun `updateEmailAddressMetadata() should throw generic exception for unknown errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "UnknownError"),
                )
            mockApiClient.stub {
                onBlocking {
                    updateEmailAddressMetadataMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                UpdateEmailAddressMetadataRequest(
                    id = mockEmailAddressId,
                    alias = null,
                )

            shouldThrow<SudoEmailClient.EmailAddressException> {
                instanceUnderTest.updateMetadata(request)
            }

            verify(mockApiClient).updateEmailAddressMetadataMutation(any())
        }

    @Test
    fun `updateEmailAddressMetadata() should use correct email address id in mutation input`() =
        runTest {
            val customEmailAddressId = "custom-email-address-id-123"
            val mutationResponse = DataFactory.updateEmailAddressMetadataMutationResponse(customEmailAddressId)
            mockApiClient.stub {
                onBlocking {
                    updateEmailAddressMetadataMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateEmailAddressMetadataRequest(
                    id = customEmailAddressId,
                    alias = null,
                )

            val result = instanceUnderTest.updateMetadata(request)

            result shouldBe customEmailAddressId

            verify(mockApiClient).updateEmailAddressMetadataMutation(
                check { input ->
                    input.id shouldBe customEmailAddressId
                },
            )
        }

    @Test
    fun `updateEmailAddressMetadata() should properly encode sealed alias when provided`() =
        runTest {
            val emailAddressId = mockEmailAddressId
            val mutationResponse = DataFactory.updateEmailAddressMetadataMutationResponse(emailAddressId)
            mockApiClient.stub {
                onBlocking {
                    updateEmailAddressMetadataMutation(any())
                } doReturn mutationResponse
            }

            val sealedAlias =
                SealedAttributeInput(
                    algorithm = "AES/CBC/PKCS7Padding",
                    keyId = "symmetricKeyId",
                    plainTextType = "string",
                    base64EncodedSealedData = "c2VhbGVkRGF0YQ==",
                )
            val request =
                UpdateEmailAddressMetadataRequest(
                    id = emailAddressId,
                    alias = sealedAlias,
                )

            val result = instanceUnderTest.updateMetadata(request)

            result shouldBe emailAddressId

            verify(mockApiClient).updateEmailAddressMetadataMutation(
                check { input ->
                    input.id shouldBe emailAddressId
                    input.values.alias shouldNotBe Optional.absent()
                    val aliasValue = (input.values.alias as Optional.Present).value
                    aliasValue?.algorithm shouldBe "AES/CBC/PKCS7Padding"
                    aliasValue?.keyId shouldBe "symmetricKeyId"
                    aliasValue?.plainTextType shouldBe "string"
                    aliasValue?.base64EncodedSealedData shouldBe "c2VhbGVkRGF0YQ=="
                },
            )
        }

    /** Begin GetEmailAddressTests */
    @Test
    fun `getEmailAddress() should return sealed email address when found`() =
        runTest {
            val queryResponse = DataFactory.getEmailAddressQueryResponse()
            mockApiClient.stub {
                onBlocking {
                    getEmailAddressQuery(any())
                } doReturn queryResponse
            }

            val request =
                GetEmailAddressRequest(
                    id = mockEmailAddressId,
                )

            val result = instanceUnderTest.get(request)

            result shouldNotBe null
            result!!.id shouldBe mockEmailAddressId
            result.emailAddress shouldBe "example@internal.sudoplatform.com"

            verify(mockApiClient).getEmailAddressQuery(
                check {
                    it shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `getEmailAddress() should return null when email address not found`() =
        runTest {
            val emptyResponse = GraphQLResponse<GetEmailAddressQuery.Data>(null, null)
            mockApiClient.stub {
                onBlocking {
                    getEmailAddressQuery(any())
                } doReturn emptyResponse
            }

            val request =
                GetEmailAddressRequest(
                    id = "nonExistentId",
                )

            val result = instanceUnderTest.get(request)

            result shouldBe null

            verify(mockApiClient).getEmailAddressQuery(
                check {
                    it shouldBe "nonExistentId"
                },
            )
        }

    @Test
    fun `getEmailAddress() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailAddressQuery(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                GetEmailAddressRequest(
                    id = mockEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailAddressException> {
                instanceUnderTest.get(request)
            }

            verify(mockApiClient).getEmailAddressQuery(any())
        }

    @Test
    fun `getEmailAddress() should throw when response has address not found error`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "AddressNotFound"),
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailAddressQuery(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                GetEmailAddressRequest(
                    id = mockEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                instanceUnderTest.get(request)
            }

            verify(mockApiClient).getEmailAddressQuery(any())
        }

    @Test
    fun `getEmailAddress() should throw when response has unauthorized address error`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "UnauthorizedAddress"),
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailAddressQuery(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                GetEmailAddressRequest(
                    id = mockEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.UnauthorizedEmailAddressException> {
                instanceUnderTest.get(request)
            }

            verify(mockApiClient).getEmailAddressQuery(any())
        }

    @Test
    fun `getEmailAddress() should use correct id in query input`() =
        runTest {
            val queryResponse = DataFactory.getEmailAddressQueryResponse()
            mockApiClient.stub {
                onBlocking {
                    getEmailAddressQuery(any())
                } doReturn queryResponse
            }

            val customId = "custom-email-address-id-123"
            val request =
                GetEmailAddressRequest(
                    id = customId,
                )

            val result = instanceUnderTest.get(request)

            result shouldNotBe null

            verify(mockApiClient).getEmailAddressQuery(
                check {
                    it shouldBe customId
                },
            )
        }

    @Test
    fun `getEmailAddress() should properly transform folders from response`() =
        runTest {
            val queryResponse = DataFactory.getEmailAddressQueryResponse()
            mockApiClient.stub {
                onBlocking {
                    getEmailAddressQuery(any())
                } doReturn queryResponse
            }

            val request =
                GetEmailAddressRequest(
                    id = mockEmailAddressId,
                )

            val result = instanceUnderTest.get(request)

            result shouldNotBe null
            result!!.folders.size shouldBe 1
            result.folders[0].id shouldBe mockFolderId

            verify(mockApiClient).getEmailAddressQuery(any())
        }

    /** Begin ListEmailAddressesTests */
    @Test
    fun `listEmailAddresses() should return list of sealed email addresses when successful`() =
        runTest {
            val items =
                listOf(
                    DataFactory.EmailAddressQueryResponseData(
                        folders =
                            listOf(
                                EmailAddress.Folder("__typename", getEmailFolder()),
                            ),
                        emailAddressWithoutFolders = DataFactory.getEmailAddressWithoutFolder(),
                    ),
                )
            val queryResponse = DataFactory.listEmailAddressesQueryResponse(items)
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailAddressesRequest(
                    limit = null,
                    nextToken = null,
                )

            val result = instanceUnderTest.list(request)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.items[0].emailAddress shouldBe "example@internal.sudoplatform.com"
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailAddressesQuery(
                check { input ->
                    input.limit shouldBe Optional.absent()
                    input.nextToken shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `listEmailAddresses() should return empty list when no addresses exist`() =
        runTest {
            val queryResponse = DataFactory.listEmailAddressesQueryResponse(emptyList())
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailAddressesRequest(
                    limit = null,
                    nextToken = null,
                )

            val result = instanceUnderTest.list(request)

            result shouldNotBe null
            result.items.size shouldBe 0
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailAddressesQuery(any())
        }

    @Test
    fun `listEmailAddresses() should handle limit parameter correctly`() =
        runTest {
            val items =
                listOf(
                    DataFactory.EmailAddressQueryResponseData(
                        folders =
                            listOf(
                                EmailAddress.Folder("__typename", getEmailFolder()),
                            ),
                        emailAddressWithoutFolders = DataFactory.getEmailAddressWithoutFolder(),
                    ),
                )
            val queryResponse = DataFactory.listEmailAddressesQueryResponse(items)
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailAddressesRequest(
                    limit = 10,
                    nextToken = null,
                )

            val result = instanceUnderTest.list(request)

            result shouldNotBe null
            result.items.size shouldBe 1

            verify(mockApiClient).listEmailAddressesQuery(
                check { input ->
                    input.limit shouldBe Optional.present(10)
                    input.nextToken shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `listEmailAddresses() should handle nextToken parameter correctly`() =
        runTest {
            val items =
                listOf(
                    DataFactory.EmailAddressQueryResponseData(
                        folders =
                            listOf(
                                EmailAddress.Folder("__typename", getEmailFolder()),
                            ),
                        emailAddressWithoutFolders = DataFactory.getEmailAddressWithoutFolder(),
                    ),
                )
            val queryResponse = DataFactory.listEmailAddressesQueryResponse(items, "nextToken123")
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailAddressesRequest(
                    limit = null,
                    nextToken = "previousToken456",
                )

            val result = instanceUnderTest.list(request)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.nextToken shouldBe "nextToken123"

            verify(mockApiClient).listEmailAddressesQuery(
                check { input ->
                    input.limit shouldBe Optional.absent()
                    input.nextToken shouldBe Optional.present("previousToken456")
                },
            )
        }

    @Test
    fun `listEmailAddresses() should handle both limit and nextToken parameters`() =
        runTest {
            val items =
                listOf(
                    DataFactory.EmailAddressQueryResponseData(
                        folders =
                            listOf(
                                EmailAddress.Folder("__typename", getEmailFolder()),
                            ),
                        emailAddressWithoutFolders = DataFactory.getEmailAddressWithoutFolder(),
                    ),
                )
            val queryResponse = DataFactory.listEmailAddressesQueryResponse(items, "newNextToken")
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailAddressesRequest(
                    limit = 50,
                    nextToken = "currentToken",
                )

            val result = instanceUnderTest.list(request)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.nextToken shouldBe "newNextToken"

            verify(mockApiClient).listEmailAddressesQuery(
                check { input ->
                    input.limit shouldBe Optional.present(50)
                    input.nextToken shouldBe Optional.present("currentToken")
                },
            )
        }

    @Test
    fun `listEmailAddresses() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesQuery(any())
                } doThrow NotAuthorizedException("Mock")
            }

            val request =
                ListEmailAddressesRequest(
                    limit = null,
                    nextToken = null,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.AuthenticationException> {
                instanceUnderTest.list(request)
            }

            verify(mockApiClient).listEmailAddressesQuery(any())
        }

    @Test
    fun `listEmailAddresses() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesQuery(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                ListEmailAddressesRequest(
                    limit = null,
                    nextToken = null,
                )

            shouldThrow<SudoEmailClient.EmailAddressException> {
                instanceUnderTest.list(request)
            }

            verify(mockApiClient).listEmailAddressesQuery(any())
        }

    @Test
    fun `listEmailAddresses() should handle multiple email addresses correctly`() =
        runTest {
            val items =
                listOf(
                    DataFactory.EmailAddressQueryResponseData(
                        folders =
                            listOf(
                                EmailAddress.Folder("__typename", getEmailFolder()),
                            ),
                        emailAddressWithoutFolders = DataFactory.getEmailAddressWithoutFolder(),
                    ),
                    DataFactory.EmailAddressQueryResponseData(
                        folders =
                            listOf(
                                EmailAddress.Folder("__typename", getEmailFolder()),
                            ),
                        emailAddressWithoutFolders =
                            DataFactory.getEmailAddressWithoutFolder(
                                id = "emailAddressId2",
                                emailAddress = "example2@sudoplatform.com",
                            ),
                    ),
                    DataFactory.EmailAddressQueryResponseData(
                        folders =
                            listOf(
                                EmailAddress.Folder("__typename", getEmailFolder()),
                            ),
                        emailAddressWithoutFolders =
                            DataFactory.getEmailAddressWithoutFolder(
                                id = "emailAddressId3",
                                emailAddress = "example3@sudoplatform.com",
                            ),
                    ),
                )
            val queryResponse = DataFactory.listEmailAddressesQueryResponse(items)
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailAddressesRequest(
                    limit = null,
                    nextToken = null,
                )

            val result = instanceUnderTest.list(request)

            result shouldNotBe null
            result.items.size shouldBe 3
            result.items[0].emailAddress shouldBe "example@internal.sudoplatform.com"
            result.items[1].emailAddress shouldBe "example2@sudoplatform.com"
            result.items[2].emailAddress shouldBe "example3@sudoplatform.com"

            verify(mockApiClient).listEmailAddressesQuery(any())
        }

    @Test
    fun `listEmailAddresses() should return empty list when data is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesQuery(any())
                } doReturn GraphQLResponse(null, null)
            }

            val request =
                ListEmailAddressesRequest(
                    limit = null,
                    nextToken = null,
                )

            val result = instanceUnderTest.list(request)

            result shouldNotBe null
            result.items.size shouldBe 0
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailAddressesQuery(any())
        }

    /** Begin ListEmailAddressesForSudoIdTests */
    @Test
    fun `listEmailAddressesForSudoId() should return list of sealed email addresses when successful`() =
        runTest {
            val items =
                listOf(
                    DataFactory.EmailAddressQueryResponseData(
                        folders =
                            listOf(
                                EmailAddress.Folder("__typename", getEmailFolder()),
                            ),
                        emailAddressWithoutFolders = DataFactory.getEmailAddressWithoutFolder(),
                    ),
                )
            val queryResponse = DataFactory.listEmailAddressesForSudoIdQueryResponse(items)
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesForSudoIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailAddressesForSudoIdRequest(
                    sudoId = "sudoId",
                    limit = null,
                    nextToken = null,
                )

            val result = instanceUnderTest.listForSudoId(request)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.items[0].emailAddress shouldBe "example@internal.sudoplatform.com"
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailAddressesForSudoIdQuery(
                check { input ->
                    input.sudoId shouldBe "sudoId"
                    input.limit shouldBe Optional.absent()
                    input.nextToken shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `listEmailAddressesForSudoId() should return empty list when no addresses exist for sudo`() =
        runTest {
            val queryResponse = DataFactory.listEmailAddressesForSudoIdQueryResponse(emptyList())
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesForSudoIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailAddressesForSudoIdRequest(
                    sudoId = "sudoId",
                    limit = null,
                    nextToken = null,
                )

            val result = instanceUnderTest.listForSudoId(request)

            result shouldNotBe null
            result.items.size shouldBe 0
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailAddressesForSudoIdQuery(any())
        }

    @Test
    fun `listEmailAddressesForSudoId() should handle limit parameter correctly`() =
        runTest {
            val items =
                listOf(
                    DataFactory.EmailAddressQueryResponseData(
                        folders =
                            listOf(
                                EmailAddress.Folder("__typename", getEmailFolder()),
                            ),
                        emailAddressWithoutFolders = DataFactory.getEmailAddressWithoutFolder(),
                    ),
                )
            val queryResponse = DataFactory.listEmailAddressesForSudoIdQueryResponse(items)
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesForSudoIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailAddressesForSudoIdRequest(
                    sudoId = "sudoId",
                    limit = 10,
                    nextToken = null,
                )

            val result = instanceUnderTest.listForSudoId(request)

            result shouldNotBe null
            result.items.size shouldBe 1

            verify(mockApiClient).listEmailAddressesForSudoIdQuery(
                check { input ->
                    input.sudoId shouldBe "sudoId"
                    input.limit shouldBe Optional.present(10)
                    input.nextToken shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `listEmailAddressesForSudoId() should handle nextToken parameter correctly`() =
        runTest {
            val items =
                listOf(
                    DataFactory.EmailAddressQueryResponseData(
                        folders =
                            listOf(
                                EmailAddress.Folder("__typename", getEmailFolder()),
                            ),
                        emailAddressWithoutFolders = DataFactory.getEmailAddressWithoutFolder(),
                    ),
                )
            val queryResponse = DataFactory.listEmailAddressesForSudoIdQueryResponse(items, "nextToken123")
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesForSudoIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailAddressesForSudoIdRequest(
                    sudoId = "sudoId",
                    limit = null,
                    nextToken = "previousToken456",
                )

            val result = instanceUnderTest.listForSudoId(request)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.nextToken shouldBe "nextToken123"

            verify(mockApiClient).listEmailAddressesForSudoIdQuery(
                check { input ->
                    input.sudoId shouldBe "sudoId"
                    input.limit shouldBe Optional.absent()
                    input.nextToken shouldBe Optional.present("previousToken456")
                },
            )
        }

    @Test
    fun `listEmailAddressesForSudoId() should handle both limit and nextToken parameters`() =
        runTest {
            val items =
                listOf(
                    DataFactory.EmailAddressQueryResponseData(
                        folders =
                            listOf(
                                EmailAddress.Folder("__typename", getEmailFolder()),
                            ),
                        emailAddressWithoutFolders = DataFactory.getEmailAddressWithoutFolder(),
                    ),
                )
            val queryResponse = DataFactory.listEmailAddressesForSudoIdQueryResponse(items, "newNextToken")
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesForSudoIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailAddressesForSudoIdRequest(
                    sudoId = "sudoId",
                    limit = 50,
                    nextToken = "currentToken",
                )

            val result = instanceUnderTest.listForSudoId(request)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.nextToken shouldBe "newNextToken"

            verify(mockApiClient).listEmailAddressesForSudoIdQuery(
                check { input ->
                    input.sudoId shouldBe "sudoId"
                    input.limit shouldBe Optional.present(50)
                    input.nextToken shouldBe Optional.present("currentToken")
                },
            )
        }

    @Test
    fun `listEmailAddressesForSudoId() should use correct sudoId in query input`() =
        runTest {
            val items =
                listOf(
                    DataFactory.EmailAddressQueryResponseData(
                        folders =
                            listOf(
                                EmailAddress.Folder("__typename", getEmailFolder()),
                            ),
                        emailAddressWithoutFolders = DataFactory.getEmailAddressWithoutFolder(),
                    ),
                )
            val queryResponse = DataFactory.listEmailAddressesForSudoIdQueryResponse(items)
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesForSudoIdQuery(any())
                } doReturn queryResponse
            }

            val customSudoId = "custom-sudo-id-12345"
            val request =
                ListEmailAddressesForSudoIdRequest(
                    sudoId = customSudoId,
                    limit = null,
                    nextToken = null,
                )

            val result = instanceUnderTest.listForSudoId(request)

            result shouldNotBe null

            verify(mockApiClient).listEmailAddressesForSudoIdQuery(
                check { input ->
                    input.sudoId shouldBe customSudoId
                },
            )
        }

    @Test
    fun `listEmailAddressesForSudoId() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesForSudoIdQuery(any())
                } doThrow NotAuthorizedException("Mock")
            }

            val request =
                ListEmailAddressesForSudoIdRequest(
                    sudoId = "sudoId",
                    limit = null,
                    nextToken = null,
                )

            shouldThrow<SudoEmailClient.EmailAddressException.AuthenticationException> {
                instanceUnderTest.listForSudoId(request)
            }

            verify(mockApiClient).listEmailAddressesForSudoIdQuery(any())
        }

    @Test
    fun `listEmailAddressesForSudoId() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesForSudoIdQuery(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                ListEmailAddressesForSudoIdRequest(
                    sudoId = "sudoId",
                    limit = null,
                    nextToken = null,
                )

            shouldThrow<SudoEmailClient.EmailAddressException> {
                instanceUnderTest.listForSudoId(request)
            }

            verify(mockApiClient).listEmailAddressesForSudoIdQuery(any())
        }

    @Test
    fun `listEmailAddressesForSudoId() should handle multiple email addresses correctly`() =
        runTest {
            val items =
                listOf(
                    DataFactory.EmailAddressQueryResponseData(
                        folders =
                            listOf(
                                EmailAddress.Folder("__typename", getEmailFolder()),
                            ),
                        emailAddressWithoutFolders = DataFactory.getEmailAddressWithoutFolder(),
                    ),
                    DataFactory.EmailAddressQueryResponseData(
                        folders =
                            listOf(
                                EmailAddress.Folder("__typename", getEmailFolder()),
                            ),
                        emailAddressWithoutFolders =
                            DataFactory.getEmailAddressWithoutFolder(
                                id = "emailAddressId2",
                                emailAddress = "example2@sudoplatform.com",
                            ),
                    ),
                )
            val queryResponse = DataFactory.listEmailAddressesForSudoIdQueryResponse(items)
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesForSudoIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailAddressesForSudoIdRequest(
                    sudoId = "sudoId",
                    limit = null,
                    nextToken = null,
                )

            val result = instanceUnderTest.listForSudoId(request)

            result shouldNotBe null
            result.items.size shouldBe 2
            result.items[0].emailAddress shouldBe "example@internal.sudoplatform.com"
            result.items[1].emailAddress shouldBe "example2@sudoplatform.com"

            verify(mockApiClient).listEmailAddressesForSudoIdQuery(any())
        }

    @Test
    fun `listEmailAddressesForSudoId() should return empty list when data is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listEmailAddressesForSudoIdQuery(any())
                } doReturn GraphQLResponse(null, null)
            }

            val request =
                ListEmailAddressesForSudoIdRequest(
                    sudoId = "sudoId",
                    limit = null,
                    nextToken = null,
                )

            val result = instanceUnderTest.listForSudoId(request)

            result shouldNotBe null
            result.items.size shouldBe 0
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailAddressesForSudoIdQuery(any())
        }

    /** Begin LookupEmailAddressesPublicInfoTests */
    @Test
    fun `lookupEmailAddressesPublicInfo() should return list of public info when successful`() =
        runTest {
            val queryResponse = DataFactory.lookupEmailAddressPublicInfoQueryResponse()
            mockApiClient.stub {
                onBlocking {
                    lookupEmailAddressesPublicInfoQuery(any())
                } doReturn queryResponse
            }

            val request =
                LookupEmailAddressesPublicInfoRequest(
                    emailAddresses = listOf("test@example.com"),
                )

            val result = instanceUnderTest.lookupPublicInfo(request)

            result shouldNotBe null
            result.size shouldBe 1
            result[0].emailAddress shouldBe "emailAddress"
            result[0].keyId shouldBe mockKeyId

            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(
                check { input ->
                    input.emailAddresses shouldBe listOf("test@example.com")
                },
            )
        }

    @Test
    fun `lookupEmailAddressesPublicInfo() should return empty list when no public info found`() =
        runTest {
            val queryResponse = DataFactory.lookupEmailAddressPublicInfoQueryResponse(emptyList())
            mockApiClient.stub {
                onBlocking {
                    lookupEmailAddressesPublicInfoQuery(any())
                } doReturn queryResponse
            }

            val request =
                LookupEmailAddressesPublicInfoRequest(
                    emailAddresses = listOf("nonexistent@example.com"),
                )

            val result = instanceUnderTest.lookupPublicInfo(request)

            result shouldNotBe null
            result.size shouldBe 0

            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(any())
        }

    @Test
    fun `lookupEmailAddressesPublicInfo() should handle multiple email addresses correctly`() =
        runTest {
            val publicInfoItems =
                listOf(
                    DataFactory.getEmailAddressPublicInfo(
                        emailAddress = "test1@example.com",
                        keyId = "keyId1",
                    ),
                    DataFactory.getEmailAddressPublicInfo(
                        emailAddress = "test2@example.com",
                        keyId = "keyId2",
                    ),
                    DataFactory.getEmailAddressPublicInfo(
                        emailAddress = "test3@example.com",
                        keyId = "keyId3",
                    ),
                )
            val queryResponse = DataFactory.lookupEmailAddressPublicInfoQueryResponse(publicInfoItems)
            mockApiClient.stub {
                onBlocking {
                    lookupEmailAddressesPublicInfoQuery(any())
                } doReturn queryResponse
            }

            val request =
                LookupEmailAddressesPublicInfoRequest(
                    emailAddresses = listOf("test1@example.com", "test2@example.com", "test3@example.com"),
                )

            val result = instanceUnderTest.lookupPublicInfo(request)

            result shouldNotBe null
            result.size shouldBe 3
            result[0].emailAddress shouldBe "test1@example.com"
            result[0].keyId shouldBe "keyId1"
            result[1].emailAddress shouldBe "test2@example.com"
            result[1].keyId shouldBe "keyId2"
            result[2].emailAddress shouldBe "test3@example.com"
            result[2].keyId shouldBe "keyId3"

            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(
                check { input ->
                    input.emailAddresses.size shouldBe 3
                },
            )
        }

    @Test
    fun `lookupEmailAddressesPublicInfo() should pass email addresses correctly in query input`() =
        runTest {
            val queryResponse = DataFactory.lookupEmailAddressPublicInfoQueryResponse()
            mockApiClient.stub {
                onBlocking {
                    lookupEmailAddressesPublicInfoQuery(any())
                } doReturn queryResponse
            }

            val emailAddressList = listOf("user1@example.com", "user2@example.com")
            val request =
                LookupEmailAddressesPublicInfoRequest(
                    emailAddresses = emailAddressList,
                )

            val result = instanceUnderTest.lookupPublicInfo(request)

            result shouldNotBe null

            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(
                check { input ->
                    input.emailAddresses shouldBe emailAddressList
                },
            )
        }

    @Test
    fun `lookupEmailAddressesPublicInfo() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    lookupEmailAddressesPublicInfoQuery(any())
                } doThrow NotAuthorizedException("Mock")
            }

            val request =
                LookupEmailAddressesPublicInfoRequest(
                    emailAddresses = listOf("test@example.com"),
                )

            shouldThrow<SudoEmailClient.EmailAddressException.AuthenticationException> {
                instanceUnderTest.lookupPublicInfo(request)
            }

            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(any())
        }

    @Test
    fun `lookupEmailAddressesPublicInfo() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            mockApiClient.stub {
                onBlocking {
                    lookupEmailAddressesPublicInfoQuery(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                LookupEmailAddressesPublicInfoRequest(
                    emailAddresses = listOf("test@example.com"),
                )

            shouldThrow<SudoEmailClient.EmailAddressException> {
                instanceUnderTest.lookupPublicInfo(request)
            }

            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(any())
        }

    @Test
    fun `lookupEmailAddressesPublicInfo() should return empty list when data is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    lookupEmailAddressesPublicInfoQuery(any())
                } doReturn GraphQLResponse(null, null)
            }

            val request =
                LookupEmailAddressesPublicInfoRequest(
                    emailAddresses = listOf("test@example.com"),
                )

            val result = instanceUnderTest.lookupPublicInfo(request)

            result shouldNotBe null
            result.size shouldBe 0

            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(any())
        }

    @Test
    fun `lookupEmailAddressesPublicInfo() should properly transform public key details`() =
        runTest {
            val publicInfoItems =
                listOf(
                    DataFactory.getEmailAddressPublicInfo(
                        emailAddress = "test@example.com",
                        keyId = "customKeyId",
                        publicKey = "customPublicKey",
                    ),
                )
            val queryResponse = DataFactory.lookupEmailAddressPublicInfoQueryResponse(publicInfoItems)
            mockApiClient.stub {
                onBlocking {
                    lookupEmailAddressesPublicInfoQuery(any())
                } doReturn queryResponse
            }

            val request =
                LookupEmailAddressesPublicInfoRequest(
                    emailAddresses = listOf("test@example.com"),
                )

            val result = instanceUnderTest.lookupPublicInfo(request)

            result shouldNotBe null
            result.size shouldBe 1
            result[0].emailAddress shouldBe "test@example.com"
            result[0].keyId shouldBe "customKeyId"
            result[0].publicKeyDetails shouldNotBe null

            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(any())
        }
}
