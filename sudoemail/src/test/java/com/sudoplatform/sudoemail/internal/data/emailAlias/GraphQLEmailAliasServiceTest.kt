/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailAlias

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amplifyframework.api.graphql.GraphQLResponse
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.graphql.DeprovisionEmailMaskMutation
import com.sudoplatform.sudoemail.graphql.ProvisionEmailMaskMutation
import com.sudoplatform.sudoemail.graphql.UpdateEmailMaskMutation
import com.sudoplatform.sudoemail.graphql.fragment.EmailMask
import com.sudoplatform.sudoemail.graphql.fragment.SealedAttribute
import com.sudoplatform.sudoemail.graphql.type.DeprovisionEmailMaskInput
import com.sudoplatform.sudoemail.graphql.type.DisableEmailMaskInput
import com.sudoplatform.sudoemail.graphql.type.EnableEmailMaskInput
import com.sudoplatform.sudoemail.graphql.type.ProvisionEmailMaskInput
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailMaskInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PublicKeyFormatEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.DeprovisionEmailAliasRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.DisableEmailAliasRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasEntityRealAddressType
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasEntityStatus
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EnableEmailAliasRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.ProvisionEmailAliasRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.UpdateEmailAliasRequest
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
import java.util.Date

/**
 * Test the correct operation of [GraphQLEmailAliasService]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GraphQLEmailAliasServiceTest : BaseTests() {
    override val mockApiClient by before {
        mock<ApiClient>()
    }

    private val instanceUnderTest by before {
        GraphQLEmailAliasService(
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

    /** Begin ProvisionEmailAliasTests */

    @Test
    fun `provision() should return sealed email alias when successful`() =
        runTest {
            val provisionEmailMaskMutationResponse = DataFactory.provisionEmailMaskMutationResponse()
            mockApiClient.stub {
                onBlocking {
                    provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
                } doReturn provisionEmailMaskMutationResponse
            }

            val request =
                ProvisionEmailAliasRequest(
                    aliasAddress = "mask@example.com",
                    realAddress = "real@example.com",
                    ownershipProofToken = "ownershipProofToken",
                    metadata = null,
                    expiresAt = null,
                    keyPair = keyPair,
                )

            val result = instanceUnderTest.provision(request)

            result shouldNotBe null
            result.aliasAddress shouldBe "mask@example.com"
            result.realAddress shouldBe "real@example.com"
            result.status shouldBe EmailAliasEntityStatus.ENABLED
            result.realAddressType shouldBe EmailAliasEntityRealAddressType.EXTERNAL

            verify(mockApiClient).provisionEmailMaskMutation(
                check { input ->
                    input.maskAddress shouldBe "mask@example.com"
                    input.realAddress shouldBe "real@example.com"
                    input.ownershipProofTokens shouldBe listOf("ownershipProofToken")
                    input.metadata shouldBe Optional.absent()
                    input.expiresAtEpochSec shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `provision() should include metadata when provided`() =
        runTest {
            val sealedMetadata =
                SealedAttributeInput(
                    algorithm = "algorithm",
                    keyId = mockKeyId,
                    plainTextType = "string",
                    base64EncodedSealedData = "base64EncodedSealedData",
                )

            val mockEmailMask =
                DataFactory.getEmailMask(
                    metadata =
                        EmailMask.Metadata(
                            __typename = "SealedAttribute",
                            sealedAttribute =
                                SealedAttribute(
                                    algorithm = "algorithm",
                                    keyId = mockKeyId,
                                    plainTextType = "string",
                                    base64EncodedSealedData = "base64EncodedSealedData",
                                ),
                        ),
                )

            val mutationResponse = DataFactory.provisionEmailMaskMutationResponse(mockEmailMask)
            mockApiClient.stub {
                onBlocking {
                    provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
                } doReturn mutationResponse
            }

            val request =
                ProvisionEmailAliasRequest(
                    aliasAddress = "mask@example.com",
                    realAddress = "real@example.com",
                    ownershipProofToken = "ownershipProofToken",
                    metadata = sealedMetadata,
                    expiresAt = null,
                    keyPair = keyPair,
                )

            val result = instanceUnderTest.provision(request)

            result shouldNotBe null
            result.sealedMetadata shouldNotBe null
            result.sealedMetadata!!.keyId shouldBe sealedMetadata.keyId
            result.sealedMetadata.base64EncodedSealedData shouldBe sealedMetadata.base64EncodedSealedData

            verify(mockApiClient).provisionEmailMaskMutation(
                check { input ->
                    input.metadata shouldNotBe Optional.absent()
                },
            )
        }

    @Test
    fun `provision() should include expiration when provided`() =
        runTest {
            val expirationDate = Date()
            val expirationEpochSec = (expirationDate.time / 1000).toInt()

            val mockEmailMask =
                DataFactory.getEmailMask(
                    expiresAtEpochSec = expirationEpochSec,
                )

            val mutationResponse = DataFactory.provisionEmailMaskMutationResponse(mockEmailMask)
            mockApiClient.stub {
                onBlocking {
                    provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
                } doReturn mutationResponse
            }

            val request =
                ProvisionEmailAliasRequest(
                    aliasAddress = "mask@example.com",
                    realAddress = "real@example.com",
                    ownershipProofToken = "ownershipProofToken",
                    metadata = null,
                    expiresAt = expirationDate,
                    keyPair = keyPair,
                )

            val result = instanceUnderTest.provision(request)

            result shouldNotBe null
            result.expiresAt shouldNotBe null

            // The conversion process: Date -> epoch seconds (Int) -> Date loses millisecond precision
            // So we need to compare the expected result after the same conversion process
            val expectedTimeAfterConversion = Date((expirationDate.time / 1000) * 1000)
            result.expiresAt!!.time shouldBe expectedTimeAfterConversion.time

            verify(mockApiClient).provisionEmailMaskMutation(
                check { input ->
                    input.expiresAtEpochSec shouldNotBe Optional.absent()
                },
            )
        }

    @Test
    fun `provision() should throw ProvisionFailedException when no data returned`() =
        runTest {
            val emptyResponse =
                GraphQLResponse<ProvisionEmailMaskMutation.Data>(
                    null,
                    null,
                )

            mockApiClient.stub {
                onBlocking {
                    provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
                } doReturn emptyResponse
            }

            val request =
                ProvisionEmailAliasRequest(
                    aliasAddress = "mask@example.com",
                    realAddress = "real@example.com",
                    ownershipProofToken = "ownershipProofToken",
                    keyPair = keyPair,
                )

            shouldThrow<SudoEmailClient.EmailAliasException.ProvisionFailedException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
        }

    @Test
    fun `provision() should throw AuthenticationException when NotAuthorizedException is thrown`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
                } doThrow NotAuthorizedException("Not authorized")
            }

            val request =
                ProvisionEmailAliasRequest(
                    aliasAddress = "mask@example.com",
                    realAddress = "real@example.com",
                    ownershipProofToken = "ownershipProofToken",
                    keyPair = keyPair,
                )

            shouldThrow<SudoEmailClient.EmailAliasException.AuthenticationException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
        }

    @Test
    fun `provision() should throw PublicKeyException when response has invalid keyring error`() =
        runTest {
            val errorResponse =
                GraphQLResponse<ProvisionEmailMaskMutation.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "Invalid keyring",
                            null,
                            null,
                            mapOf(
                                "errorType" to "sudoplatform.InvalidKeyRingId",
                            ),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking {
                    provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
                } doReturn errorResponse
            }

            val request =
                ProvisionEmailAliasRequest(
                    aliasAddress = "mask@example.com",
                    realAddress = "real@example.com",
                    ownershipProofToken = "ownershipProofToken",
                    keyPair = keyPair,
                )

            shouldThrow<SudoEmailClient.EmailAliasException.PublicKeyException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
        }

    @Test
    fun `provision() should throw EmailAliasAlreadyExistsException when response has already exists error`() =
        runTest {
            val errorResponse =
                GraphQLResponse<ProvisionEmailMaskMutation.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "Email mask already exists",
                            null,
                            null,
                            mapOf("errorType" to "sudoplatform.email.EmailMaskAlreadyExists"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking {
                    provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
                } doReturn errorResponse
            }

            val request =
                ProvisionEmailAliasRequest(
                    aliasAddress = "mask@example.com",
                    realAddress = "real@example.com",
                    ownershipProofToken = "ownershipProofToken",
                    keyPair = keyPair,
                )

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasAlreadyExistsException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
        }

    @Test
    fun `provision() should throw UnavailableEmailAddressException when response has address unavailable error`() =
        runTest {
            val errorResponse =
                GraphQLResponse<ProvisionEmailMaskMutation.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "Address unavailable",
                            null,
                            null,
                            mapOf("errorType" to "sudoplatform.email.AddressUnavailable"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking {
                    provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
                } doReturn errorResponse
            }

            val request =
                ProvisionEmailAliasRequest(
                    aliasAddress = "mask@example.com",
                    realAddress = "real@example.com",
                    ownershipProofToken = "ownershipProofToken",
                    keyPair = keyPair,
                )

            shouldThrow<SudoEmailClient.EmailAliasException.UnavailableEmailAddressException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
        }

    @Test
    fun `provision() should throw InsufficientEntitlementsException when response has insufficient entitlements error`() =
        runTest {
            val errorResponse =
                GraphQLResponse<ProvisionEmailMaskMutation.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "Insufficient entitlements",
                            null,
                            null,
                            mapOf("errorType" to "sudoplatform.InsufficientEntitlementsError"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking {
                    provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
                } doReturn errorResponse
            }

            val request =
                ProvisionEmailAliasRequest(
                    aliasAddress = "mask@example.com",
                    realAddress = "real@example.com",
                    ownershipProofToken = "ownershipProofToken",
                    keyPair = keyPair,
                )

            shouldThrow<SudoEmailClient.EmailAliasException.InsufficientEntitlementsException> {
                instanceUnderTest.provision(request)
            }

            verify(mockApiClient).provisionEmailMaskMutation(any<ProvisionEmailMaskInput>())
        }

    /** Begin DeprovisionEmailAliasTests */

    @Test
    fun `deprovision() should return sealed email alias when successful`() =
        runTest {
            val deprovisionEmailMaskMutationResponse = DataFactory.deprovisionEmailMaskMutationResponse()
            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailMaskMutation(any<DeprovisionEmailMaskInput>())
                } doReturn deprovisionEmailMaskMutationResponse
            }

            val request =
                DeprovisionEmailAliasRequest(
                    emailAliasId = "mockEmailMaskId",
                )

            val result = instanceUnderTest.deprovision(request)

            result shouldNotBe null
            result.id shouldBe "mockEmailMaskId"

            verify(mockApiClient).deprovisionEmailMaskMutation(
                check { input ->
                    input.emailMaskId shouldBe "mockEmailMaskId"
                },
            )
        }

    @Test
    fun `deprovision() should throw DeprovisionFailedException when no data returned`() =
        runTest {
            val emptyResponse =
                GraphQLResponse<DeprovisionEmailMaskMutation.Data>(
                    null,
                    null,
                )

            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailMaskMutation(any<DeprovisionEmailMaskInput>())
                } doReturn emptyResponse
            }

            val request =
                DeprovisionEmailAliasRequest(
                    emailAliasId = "mockEmailMaskId",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.DeprovisionFailedException> {
                instanceUnderTest.deprovision(request)
            }

            verify(mockApiClient).deprovisionEmailMaskMutation(any<DeprovisionEmailMaskInput>())
        }

    @Test
    fun `deprovision() should throw AuthenticationException when NotAuthorizedException is thrown`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailMaskMutation(any<DeprovisionEmailMaskInput>())
                } doThrow NotAuthorizedException("Not authorized")
            }

            val request =
                DeprovisionEmailAliasRequest(
                    emailAliasId = "mockEmailMaskId",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.AuthenticationException> {
                instanceUnderTest.deprovision(request)
            }

            verify(mockApiClient).deprovisionEmailMaskMutation(any<DeprovisionEmailMaskInput>())
        }

    @Test
    fun `deprovision() should throw EmailAliasNotFoundException when response has mask not found error`() =
        runTest {
            val errorResponse =
                GraphQLResponse<DeprovisionEmailMaskMutation.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "Email mask not found",
                            null,
                            null,
                            mapOf("errorType" to "sudoplatform.email.EmailMaskNotFound"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking {
                    deprovisionEmailMaskMutation(any<DeprovisionEmailMaskInput>())
                } doReturn errorResponse
            }

            val request =
                DeprovisionEmailAliasRequest(
                    emailAliasId = "nonExistentMaskId",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasNotFoundException> {
                instanceUnderTest.deprovision(request)
            }

            verify(mockApiClient).deprovisionEmailMaskMutation(any<DeprovisionEmailMaskInput>())
        }

    /** Begin UpdateEmailAliasTests */

    @Test
    fun `update() should return sealed email alias when successful`() =
        runTest {
            val updateEmailMaskMutationResponse = DataFactory.updateEmailMaskMutationResponse()
            mockApiClient.stub {
                onBlocking {
                    updateEmailMaskMutation(any<UpdateEmailMaskInput>())
                } doReturn updateEmailMaskMutationResponse
            }

            val request =
                UpdateEmailAliasRequest(
                    id = "mockEmailMaskId",
                    metadata = null,
                    expiresAt = null,
                )

            val result = instanceUnderTest.update(request)

            result shouldNotBe null
            result.id shouldBe "mockEmailMaskId"

            verify(mockApiClient).updateEmailMaskMutation(
                check { input ->
                    input.id shouldBe "mockEmailMaskId"
                    input.metadata shouldBe Optional.absent()
                    input.expiresAtEpochSec shouldBe Optional.absent()
                    input.realAddress shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `update() should throw UpdateFailedException when no data returned`() =
        runTest {
            val emptyResponse =
                GraphQLResponse<UpdateEmailMaskMutation.Data>(
                    null,
                    null,
                )

            mockApiClient.stub {
                onBlocking {
                    updateEmailMaskMutation(any<UpdateEmailMaskInput>())
                } doReturn emptyResponse
            }

            val request =
                UpdateEmailAliasRequest(
                    id = "mockEmailMaskId",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.UpdateFailedException> {
                instanceUnderTest.update(request)
            }

            verify(mockApiClient).updateEmailMaskMutation(any<UpdateEmailMaskInput>())
        }

    @Test
    fun `update() should throw AuthenticationException when NotAuthorizedException is thrown`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    updateEmailMaskMutation(any<UpdateEmailMaskInput>())
                } doThrow NotAuthorizedException("Not authorized")
            }

            val request =
                UpdateEmailAliasRequest(
                    id = "mockEmailMaskId",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.AuthenticationException> {
                instanceUnderTest.update(request)
            }

            verify(mockApiClient).updateEmailMaskMutation(any<UpdateEmailMaskInput>())
        }

    @Test
    fun `update() should throw EmailAliasNotFoundException when response has mask not found error`() =
        runTest {
            val errorResponse =
                GraphQLResponse<UpdateEmailMaskMutation.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "Email mask not found",
                            null,
                            null,
                            mapOf("errorType" to "sudoplatform.email.EmailMaskNotFound"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking {
                    updateEmailMaskMutation(any<UpdateEmailMaskInput>())
                } doReturn errorResponse
            }

            val request =
                UpdateEmailAliasRequest(
                    id = "nonExistentMaskId",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasNotFoundException> {
                instanceUnderTest.update(request)
            }

            verify(mockApiClient).updateEmailMaskMutation(any<UpdateEmailMaskInput>())
        }

    @Test
    fun `update() should include metadata and expiration when provided`() =
        runTest {
            val sealedMetadata =
                SealedAttributeInput(
                    algorithm = "algorithm",
                    keyId = mockKeyId,
                    plainTextType = "string",
                    base64EncodedSealedData = "base64EncodedSealedData",
                )
            val expirationDate = Date()

            val updateEmailMaskMutationResponse = DataFactory.updateEmailMaskMutationResponse()
            mockApiClient.stub {
                onBlocking {
                    updateEmailMaskMutation(any<UpdateEmailMaskInput>())
                } doReturn updateEmailMaskMutationResponse
            }

            val request =
                UpdateEmailAliasRequest(
                    id = "mockEmailMaskId",
                    metadata = sealedMetadata,
                    expiresAt = expirationDate,
                )

            val result = instanceUnderTest.update(request)

            result shouldNotBe null
            result.id shouldBe "mockEmailMaskId"

            verify(mockApiClient).updateEmailMaskMutation(
                check { input ->
                    input.id shouldBe "mockEmailMaskId"
                    input.metadata shouldNotBe Optional.absent()
                    input.expiresAtEpochSec shouldNotBe Optional.absent()
                },
            )
        }

    @Test
    fun `update() should include realAddress when provided`() =
        runTest {
            val updateEmailMaskMutationResponse = DataFactory.updateEmailMaskMutationResponse()
            mockApiClient.stub {
                onBlocking {
                    updateEmailMaskMutation(any<UpdateEmailMaskInput>())
                } doReturn updateEmailMaskMutationResponse
            }

            val request =
                UpdateEmailAliasRequest(
                    id = "mockEmailMaskId",
                    realAddress = "new@example.com",
                )

            val result = instanceUnderTest.update(request)

            result shouldNotBe null
            result.id shouldBe "mockEmailMaskId"

            verify(mockApiClient).updateEmailMaskMutation(
                check { input ->
                    input.id shouldBe "mockEmailMaskId"
                    input.metadata shouldBe Optional.absent()
                    input.expiresAtEpochSec shouldBe Optional.absent()
                    input.realAddress shouldBe Optional.presentIfNotNull("new@example.com")
                },
            )
        }

    /** Begin EnableEmailAliasTests */

    @Test
    fun `enable() should return sealed email alias when successful`() =
        runTest {
            val enableEmailMaskMutationResponse = DataFactory.enableEmailMaskMutationResponse()
            mockApiClient.stub {
                onBlocking {
                    enableEmailMaskMutation(any<EnableEmailMaskInput>())
                } doReturn enableEmailMaskMutationResponse
            }

            val request =
                EnableEmailAliasRequest(
                    emailAliasId = "mockEmailMaskId",
                )

            val result = instanceUnderTest.enable(request)

            result shouldNotBe null
            result.id shouldBe "mockEmailMaskId"
            result.status shouldBe EmailAliasEntityStatus.ENABLED

            verify(mockApiClient).enableEmailMaskMutation(
                check { input ->
                    input.emailMaskId shouldBe "mockEmailMaskId"
                },
            )
        }

    @Test
    fun `enable() should throw UpdateFailedException when no data returned`() =
        runTest {
            val emptyResponse =
                GraphQLResponse<com.sudoplatform.sudoemail.graphql.EnableEmailMaskMutation.Data>(
                    null,
                    null,
                )

            mockApiClient.stub {
                onBlocking {
                    enableEmailMaskMutation(any<EnableEmailMaskInput>())
                } doReturn emptyResponse
            }

            val request =
                EnableEmailAliasRequest(
                    emailAliasId = "mockEmailMaskId",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.UpdateFailedException> {
                instanceUnderTest.enable(request)
            }

            verify(mockApiClient).enableEmailMaskMutation(any<EnableEmailMaskInput>())
        }

    @Test
    fun `enable() should throw AuthenticationException when NotAuthorizedException is thrown`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    enableEmailMaskMutation(any<EnableEmailMaskInput>())
                } doThrow NotAuthorizedException("Not authorized")
            }

            val request =
                EnableEmailAliasRequest(
                    emailAliasId = "mockEmailMaskId",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.AuthenticationException> {
                instanceUnderTest.enable(request)
            }

            verify(mockApiClient).enableEmailMaskMutation(any<EnableEmailMaskInput>())
        }

    @Test
    fun `enable() should throw EmailAliasNotFoundException when response has mask not found error`() =
        runTest {
            val errorResponse =
                GraphQLResponse<com.sudoplatform.sudoemail.graphql.EnableEmailMaskMutation.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "Email mask not found",
                            null,
                            null,
                            mapOf("errorType" to "sudoplatform.email.EmailMaskNotFound"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking {
                    enableEmailMaskMutation(any<EnableEmailMaskInput>())
                } doReturn errorResponse
            }

            val request =
                EnableEmailAliasRequest(
                    emailAliasId = "nonExistentMaskId",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasNotFoundException> {
                instanceUnderTest.enable(request)
            }

            verify(mockApiClient).enableEmailMaskMutation(any<EnableEmailMaskInput>())
        }

    /** Begin DisableEmailAliasTests */

    @Test
    fun `disable() should return sealed email alias when successful`() =
        runTest {
            val disableEmailMaskMutationResponse = DataFactory.disableEmailMaskMutationResponse()
            mockApiClient.stub {
                onBlocking {
                    disableEmailMaskMutation(any<DisableEmailMaskInput>())
                } doReturn disableEmailMaskMutationResponse
            }

            val request =
                DisableEmailAliasRequest(
                    emailAliasId = "mockEmailMaskId",
                )

            val result = instanceUnderTest.disable(request)

            result shouldNotBe null
            result.id shouldBe "mockEmailMaskId"
            result.status shouldBe EmailAliasEntityStatus.DISABLED

            verify(mockApiClient).disableEmailMaskMutation(
                check { input ->
                    input.emailMaskId shouldBe "mockEmailMaskId"
                },
            )
        }

    @Test
    fun `disable() should throw UpdateFailedException when no data returned`() =
        runTest {
            val emptyResponse =
                GraphQLResponse<com.sudoplatform.sudoemail.graphql.DisableEmailMaskMutation.Data>(
                    null,
                    null,
                )

            mockApiClient.stub {
                onBlocking {
                    disableEmailMaskMutation(any<DisableEmailMaskInput>())
                } doReturn emptyResponse
            }

            val request =
                DisableEmailAliasRequest(
                    emailAliasId = "mockEmailMaskId",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.UpdateFailedException> {
                instanceUnderTest.disable(request)
            }

            verify(mockApiClient).disableEmailMaskMutation(any<DisableEmailMaskInput>())
        }

    @Test
    fun `disable() should throw AuthenticationException when NotAuthorizedException is thrown`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    disableEmailMaskMutation(any<DisableEmailMaskInput>())
                } doThrow NotAuthorizedException("Not authorized")
            }

            val request =
                DisableEmailAliasRequest(
                    emailAliasId = "mockEmailMaskId",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.AuthenticationException> {
                instanceUnderTest.disable(request)
            }

            verify(mockApiClient).disableEmailMaskMutation(any<DisableEmailMaskInput>())
        }

    @Test
    fun `disable() should throw EmailAliasNotFoundException when response has mask not found error`() =
        runTest {
            val errorResponse =
                GraphQLResponse<com.sudoplatform.sudoemail.graphql.DisableEmailMaskMutation.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "Email mask not found",
                            null,
                            null,
                            mapOf("errorType" to "sudoplatform.email.EmailMaskNotFound"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking {
                    disableEmailMaskMutation(any<DisableEmailMaskInput>())
                } doReturn errorResponse
            }

            val request =
                DisableEmailAliasRequest(
                    emailAliasId = "nonExistentMaskId",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasNotFoundException> {
                instanceUnderTest.disable(request)
            }

            verify(mockApiClient).disableEmailMaskMutation(any<DisableEmailMaskInput>())
        }

    /** Begin VerifyExternalEmailAddressTests */

    @Test
    fun `verifyExternalEmailAddress() should return result when triggering verification email`() =
        runTest {
            // Simulate nil response (verification email sent)
            val mutationResponse =
                GraphQLResponse<com.sudoplatform.sudoemail.graphql.VerifyExternalEmailAddressMutation.Data>(
                    null,
                    emptyList(),
                )

            mockApiClient.stub {
                onBlocking {
                    verifyExternalAddressMutation(any())
                } doReturn mutationResponse
            }

            val request =
                com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.VerifyExternalEmailAddressRequest(
                    emailAddress = "external@example.com",
                    emailAliasId = "mask-id-123",
                    verificationCode = null,
                )

            val result = instanceUnderTest.verifyExternalEmailAddress(request)

            result shouldNotBe null
            result.isVerified shouldBe false
            result.reason shouldBe "Verification email sent"

            verify(mockApiClient).verifyExternalAddressMutation(
                check { input ->
                    input.emailAddress shouldBe "external@example.com"
                    input.emailMaskId shouldBe "mask-id-123"
                    input.verificationCode shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `verifyExternalEmailAddress() should return success when verification succeeds`() =
        runTest {
            val verifyResult =
                com.sudoplatform.sudoemail.graphql.fragment.VerifyExternalEmailAddressResult(
                    isVerified = true,
                    reason = null,
                )
            val mutationResponse =
                GraphQLResponse<com.sudoplatform.sudoemail.graphql.VerifyExternalEmailAddressMutation.Data>(
                    com.sudoplatform.sudoemail.graphql.VerifyExternalEmailAddressMutation.Data(
                        com.sudoplatform.sudoemail.graphql.VerifyExternalEmailAddressMutation.VerifyExternalEmailAddress(
                            __typename = "VerifyExternalEmailAddressResult",
                            verifyExternalEmailAddressResult = verifyResult,
                        ),
                    ),
                    emptyList(),
                )

            mockApiClient.stub {
                onBlocking {
                    verifyExternalAddressMutation(any())
                } doReturn mutationResponse
            }

            val request =
                com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.VerifyExternalEmailAddressRequest(
                    emailAddress = "external@example.com",
                    emailAliasId = "mask-id-123",
                    verificationCode = "123456",
                )

            val result = instanceUnderTest.verifyExternalEmailAddress(request)

            result shouldNotBe null
            result.isVerified shouldBe true
            result.reason shouldBe null

            verify(mockApiClient).verifyExternalAddressMutation(
                check { input ->
                    input.emailAddress shouldBe "external@example.com"
                    input.emailMaskId shouldBe "mask-id-123"
                    input.verificationCode shouldBe Optional.Present("123456")
                },
            )
        }

    @Test
    fun `verifyExternalEmailAddress() should return failure with invalid code`() =
        runTest {
            val verifyResult =
                com.sudoplatform.sudoemail.graphql.fragment.VerifyExternalEmailAddressResult(
                    isVerified = false,
                    reason = "Invalid verification code",
                )
            val mutationResponse =
                GraphQLResponse<com.sudoplatform.sudoemail.graphql.VerifyExternalEmailAddressMutation.Data>(
                    com.sudoplatform.sudoemail.graphql.VerifyExternalEmailAddressMutation.Data(
                        com.sudoplatform.sudoemail.graphql.VerifyExternalEmailAddressMutation.VerifyExternalEmailAddress(
                            __typename = "VerifyExternalEmailAddressResult",
                            verifyExternalEmailAddressResult = verifyResult,
                        ),
                    ),
                    emptyList(),
                )

            mockApiClient.stub {
                onBlocking {
                    verifyExternalAddressMutation(any())
                } doReturn mutationResponse
            }

            val request =
                com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.VerifyExternalEmailAddressRequest(
                    emailAddress = "external@example.com",
                    emailAliasId = "mask-id-123",
                    verificationCode = "wrong-code",
                )

            val result = instanceUnderTest.verifyExternalEmailAddress(request)

            result shouldNotBe null
            result.isVerified shouldBe false
            result.reason shouldBe "Invalid verification code"

            verify(mockApiClient).verifyExternalAddressMutation(any())
        }

    @Test
    fun `verifyExternalEmailAddress() should throw when GraphQL returns error`() =
        runTest {
            val errorResponse =
                GraphQLResponse<com.sudoplatform.sudoemail.graphql.VerifyExternalEmailAddressMutation.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "Email mask not found",
                            null,
                            null,
                            mapOf("errorType" to "sudoplatform.email.EmailMaskNotFound"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking {
                    verifyExternalAddressMutation(any())
                } doReturn errorResponse
            }

            val request =
                com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.VerifyExternalEmailAddressRequest(
                    emailAddress = "external@example.com",
                    emailAliasId = "nonExistentMaskId",
                    verificationCode = "123456",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasNotFoundException> {
                instanceUnderTest.verifyExternalEmailAddress(request)
            }

            verify(mockApiClient).verifyExternalAddressMutation(any())
        }

    @Test
    fun `verifyExternalEmailAddress() should throw AuthenticationException when not authorized`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    verifyExternalAddressMutation(any())
                } doThrow NotAuthorizedException("Not authorized")
            }

            val request =
                com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.VerifyExternalEmailAddressRequest(
                    emailAddress = "external@example.com",
                    emailAliasId = "mask-id-123",
                    verificationCode = "123456",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.AuthenticationException> {
                instanceUnderTest.verifyExternalEmailAddress(request)
            }

            verify(mockApiClient).verifyExternalAddressMutation(any())
        }
}
