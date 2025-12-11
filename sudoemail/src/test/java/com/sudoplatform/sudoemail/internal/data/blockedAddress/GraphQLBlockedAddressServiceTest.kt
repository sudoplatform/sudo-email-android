/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.blockedAddress

import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.graphql.BlockEmailAddressesMutation
import com.sudoplatform.sudoemail.graphql.GetEmailAddressBlocklistQuery
import com.sudoplatform.sudoemail.graphql.UnblockEmailAddressesMutation
import com.sudoplatform.sudoemail.graphql.type.BlockEmailAddressesBulkUpdateStatus
import com.sudoplatform.sudoemail.graphql.type.BlockedAddressAction
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockEmailAddressRequestItem
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockEmailAddressesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedAddressHashAlgorithmEntity
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedEmailAddressActionEntity
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.GetEmailAddressBlocklistRequest
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.UnblockEmailAddressesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
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

/**
 * Test the correct operation of [GraphQLBlockedAddressService]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GraphQLBlockedAddressServiceTest : BaseTests() {
    private val hashedValue1 = "hashedValue1"
    private val hashedValue2 = "hashedValue2"
    private val keyId = "keyId"
    private val algorithm = "algorithm"
    private val plainTextType = "plainTextType"
    private val sealedData1 = "sealedData1"
    private val sealedData2 = "sealedData2"

    override val mockApiClient by before {
        mock<ApiClient>()
    }

    private val sealedAttribute1 by before {
        SealedAttributeEntity(
            keyId = keyId,
            algorithm = algorithm,
            plainTextType = plainTextType,
            base64EncodedSealedData = sealedData1,
        )
    }

    private val sealedAttribute2 by before {
        SealedAttributeEntity(
            keyId = keyId,
            algorithm = algorithm,
            plainTextType = plainTextType,
            base64EncodedSealedData = sealedData2,
        )
    }

    private val blockRequestItem1 by before {
        BlockEmailAddressRequestItem(
            hashAlgorithm = BlockedAddressHashAlgorithmEntity.SHA256,
            hashedBlockedValue = hashedValue1,
            sealedValue = sealedAttribute1,
            action = BlockedEmailAddressActionEntity.DROP,
        )
    }

    private val blockRequestItem2 by before {
        BlockEmailAddressRequestItem(
            hashAlgorithm = BlockedAddressHashAlgorithmEntity.SHA256,
            hashedBlockedValue = hashedValue2,
            sealedValue = sealedAttribute2,
            action = BlockedEmailAddressActionEntity.DROP,
        )
    }

    private val instanceUnderTest by before {
        GraphQLBlockedAddressService(
            mockApiClient,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockApiClient,
        )
    }

    @Test
    fun `blockEmailAddresses() should return success with all addresses on success`() =
        runTest {
            val mutationResponse =
                DataFactory.blockEmailAddressMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
                    successAddresses = listOf(hashedValue1, hashedValue2),
                    failedAddresses = emptyList(),
                )

            mockApiClient.stub {
                onBlocking { blockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                BlockEmailAddressesRequest(
                    owner = mockOwner,
                    blockedAddresses = listOf(blockRequestItem1, blockRequestItem2),
                    emailAddressId = null,
                )

            val result = instanceUnderTest.blockEmailAddresses(request)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues shouldBe listOf(hashedValue1, hashedValue2)
            result.failureValues shouldBe emptyList()

            verify(mockApiClient).blockEmailAddressesMutation(
                check {
                    it.owner shouldBe mockOwner
                    it.blockedAddresses.size shouldBe 2
                    it.blockedAddresses[0].hashedBlockedValue shouldBe hashedValue1
                    it.blockedAddresses[1].hashedBlockedValue shouldBe hashedValue2
                },
            )
        }

    @Test
    fun `blockEmailAddresses() should handle single blocked address`() =
        runTest {
            val mutationResponse =
                DataFactory.blockEmailAddressMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
                    successAddresses = listOf(hashedValue1),
                    failedAddresses = emptyList(),
                )

            mockApiClient.stub {
                onBlocking { blockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                BlockEmailAddressesRequest(
                    owner = mockOwner,
                    blockedAddresses = listOf(blockRequestItem1),
                    emailAddressId = null,
                )

            val result = instanceUnderTest.blockEmailAddresses(request)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues!!.size shouldBe 1
            result.successValues!![0] shouldBe hashedValue1

            verify(mockApiClient).blockEmailAddressesMutation(any())
        }

    @Test
    fun `blockEmailAddresses() should handle partial success`() =
        runTest {
            val mutationResponse =
                DataFactory.blockEmailAddressMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.PARTIAL,
                    successAddresses = listOf(hashedValue1),
                    failedAddresses = listOf(hashedValue2),
                )

            mockApiClient.stub {
                onBlocking { blockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                BlockEmailAddressesRequest(
                    owner = mockOwner,
                    blockedAddresses = listOf(blockRequestItem1, blockRequestItem2),
                    emailAddressId = null,
                )

            val result = instanceUnderTest.blockEmailAddresses(request)

            result.status shouldBe BatchOperationStatusEntity.PARTIAL
            result.successValues shouldBe listOf(hashedValue1)
            result.failureValues shouldBe listOf(hashedValue2)

            verify(mockApiClient).blockEmailAddressesMutation(any())
        }

    @Test
    fun `blockEmailAddresses() should handle failure status`() =
        runTest {
            val mutationResponse =
                DataFactory.blockEmailAddressMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.FAILED,
                    successAddresses = emptyList(),
                    failedAddresses = listOf(hashedValue1, hashedValue2),
                )

            mockApiClient.stub {
                onBlocking { blockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                BlockEmailAddressesRequest(
                    owner = mockOwner,
                    blockedAddresses = listOf(blockRequestItem1, blockRequestItem2),
                    emailAddressId = null,
                )

            val result = instanceUnderTest.blockEmailAddresses(request)

            result.status shouldBe BatchOperationStatusEntity.FAILURE
            result.successValues shouldBe emptyList()
            result.failureValues shouldBe listOf(hashedValue1, hashedValue2)

            verify(mockApiClient).blockEmailAddressesMutation(any())
        }

    @Test
    fun `blockEmailAddresses() should pass mockEmailAddressId when provided`() =
        runTest {
            val mutationResponse =
                DataFactory.blockEmailAddressMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
                    successAddresses = listOf(hashedValue1),
                    failedAddresses = emptyList(),
                )

            mockApiClient.stub {
                onBlocking { blockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                BlockEmailAddressesRequest(
                    owner = mockOwner,
                    blockedAddresses = listOf(blockRequestItem1),
                    emailAddressId = mockEmailAddressId,
                )

            instanceUnderTest.blockEmailAddresses(request)

            verify(mockApiClient).blockEmailAddressesMutation(
                check {
                    it.owner shouldBe mockOwner
                },
            )
        }

    @Test
    fun `blockEmailAddresses() should not pass mockEmailAddressId when null`() =
        runTest {
            val mutationResponse =
                DataFactory.blockEmailAddressMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
                    successAddresses = listOf(hashedValue1),
                    failedAddresses = emptyList(),
                )

            mockApiClient.stub {
                onBlocking { blockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                BlockEmailAddressesRequest(
                    owner = mockOwner,
                    blockedAddresses = listOf(blockRequestItem1),
                    emailAddressId = null,
                )

            instanceUnderTest.blockEmailAddresses(request)

            verify(mockApiClient).blockEmailAddressesMutation(
                check {
                    it.owner shouldBe mockOwner
                },
            )
        }

    @Test
    fun `blockEmailAddresses() should transform sealed attributes correctly`() =
        runTest {
            val mutationResponse =
                DataFactory.blockEmailAddressMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
                    successAddresses = listOf(hashedValue1),
                    failedAddresses = emptyList(),
                )

            mockApiClient.stub {
                onBlocking { blockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                BlockEmailAddressesRequest(
                    owner = mockOwner,
                    blockedAddresses = listOf(blockRequestItem1),
                    emailAddressId = null,
                )

            instanceUnderTest.blockEmailAddresses(request)

            verify(mockApiClient).blockEmailAddressesMutation(
                check {
                    val sealedValue = it.blockedAddresses[0].sealedValue
                    sealedValue.keyId shouldBe keyId
                    sealedValue.algorithm shouldBe algorithm
                    sealedValue.plainTextType shouldBe plainTextType
                    sealedValue.base64EncodedSealedData shouldBe sealedData1
                },
            )
        }

    @Test
    fun `blockEmailAddresses() should throw when mutation returns errors`() =
        runTest {
            val errorResponse =
                GraphQLResponse<BlockEmailAddressesMutation.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "InvalidArgument",
                            emptyList(),
                            emptyList(),
                            mapOf("errorType" to "InvalidArgument"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking { blockEmailAddressesMutation(any()) } doReturn errorResponse
            }

            val request =
                BlockEmailAddressesRequest(
                    owner = mockOwner,
                    blockedAddresses = listOf(blockRequestItem1),
                    emailAddressId = null,
                )

            shouldThrow<SudoEmailClient.EmailBlocklistException> {
                instanceUnderTest.blockEmailAddresses(request)
            }

            verify(mockApiClient).blockEmailAddressesMutation(any())
        }

    @Test
    fun `blockEmailAddresses() should throw FailedException when response data is null`() =
        runTest {
            val nullDataResponse =
                GraphQLResponse<BlockEmailAddressesMutation.Data>(
                    null,
                    null,
                )

            mockApiClient.stub {
                onBlocking { blockEmailAddressesMutation(any()) } doReturn nullDataResponse
            }

            val request =
                BlockEmailAddressesRequest(
                    owner = mockOwner,
                    blockedAddresses = listOf(blockRequestItem1),
                    emailAddressId = null,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailBlocklistException.FailedException> {
                    instanceUnderTest.blockEmailAddresses(request)
                }

            exception.message shouldBe StringConstants.UNKNOWN_ERROR_MSG

            verify(mockApiClient).blockEmailAddressesMutation(any())
        }

    @Test
    fun `blockEmailAddresses() should throw when API client throws exception`() =
        runTest {
            mockApiClient.stub {
                onBlocking { blockEmailAddressesMutation(any()) } doThrow RuntimeException("Network error")
            }

            val request =
                BlockEmailAddressesRequest(
                    owner = mockOwner,
                    blockedAddresses = listOf(blockRequestItem1),
                    emailAddressId = null,
                )

            shouldThrow<SudoEmailClient.EmailBlocklistException> {
                instanceUnderTest.blockEmailAddresses(request)
            }

            verify(mockApiClient).blockEmailAddressesMutation(any())
        }

    @Test
    fun `blockEmailAddresses() should handle different actions`() =
        runTest {
            val spamItem =
                BlockEmailAddressRequestItem(
                    hashAlgorithm = BlockedAddressHashAlgorithmEntity.SHA256,
                    hashedBlockedValue = hashedValue1,
                    sealedValue = sealedAttribute1,
                    action = BlockedEmailAddressActionEntity.SPAM,
                )

            val mutationResponse =
                DataFactory.blockEmailAddressMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
                    successAddresses = listOf(hashedValue1),
                    failedAddresses = emptyList(),
                )

            mockApiClient.stub {
                onBlocking { blockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                BlockEmailAddressesRequest(
                    owner = mockOwner,
                    blockedAddresses = listOf(spamItem),
                    emailAddressId = null,
                )

            instanceUnderTest.blockEmailAddresses(request)

            verify(mockApiClient).blockEmailAddressesMutation(any())
        }

    @Test
    fun `blockEmailAddresses() should handle empty success and failure lists`() =
        runTest {
            val mutationResponse =
                DataFactory.blockEmailAddressMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
                    successAddresses = emptyList(),
                    failedAddresses = emptyList(),
                )

            mockApiClient.stub {
                onBlocking { blockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                BlockEmailAddressesRequest(
                    owner = mockOwner,
                    blockedAddresses = listOf(blockRequestItem1),
                    emailAddressId = null,
                )

            val result = instanceUnderTest.blockEmailAddresses(request)

            result.successValues shouldBe emptyList()
            result.failureValues shouldBe emptyList()

            verify(mockApiClient).blockEmailAddressesMutation(any())
        }

    @Test
    fun `blockEmailAddresses() should pass correct mockOwner to mutation`() =
        runTest {
            val customOwner = "customOwner123"
            val mutationResponse =
                DataFactory.blockEmailAddressMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
                    successAddresses = listOf(hashedValue1),
                    failedAddresses = emptyList(),
                )

            mockApiClient.stub {
                onBlocking { blockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                BlockEmailAddressesRequest(
                    owner = customOwner,
                    blockedAddresses = listOf(blockRequestItem1),
                    emailAddressId = null,
                )

            instanceUnderTest.blockEmailAddresses(request)

            verify(mockApiClient).blockEmailAddressesMutation(
                check {
                    it.owner shouldBe customOwner
                },
            )
        }

    @Test
    fun `unblockEmailAddresses() should return success with all addresses on success`() =
        runTest {
            val mutationResponse =
                DataFactory.unblockEmailAddressesMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
                    successAddresses = listOf(hashedValue1, hashedValue2),
                    failedAddresses = emptyList(),
                )

            mockApiClient.stub {
                onBlocking { unblockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                UnblockEmailAddressesRequest(
                    owner = mockOwner,
                    hashedBlockedValues = listOf(hashedValue1, hashedValue2),
                )

            val result = instanceUnderTest.unblockEmailAddresses(request)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues shouldBe listOf(hashedValue1, hashedValue2)
            result.failureValues shouldBe emptyList()

            verify(mockApiClient).unblockEmailAddressesMutation(
                check {
                    it.owner shouldBe mockOwner
                    it.unblockedAddresses.size shouldBe 2
                    it.unblockedAddresses[0] shouldBe hashedValue1
                    it.unblockedAddresses[1] shouldBe hashedValue2
                },
            )
        }

    @Test
    fun `unblockEmailAddresses() should handle single address`() =
        runTest {
            val mutationResponse =
                DataFactory.unblockEmailAddressesMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
                    successAddresses = listOf(hashedValue1),
                    failedAddresses = emptyList(),
                )

            mockApiClient.stub {
                onBlocking { unblockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                UnblockEmailAddressesRequest(
                    owner = mockOwner,
                    hashedBlockedValues = listOf(hashedValue1),
                )

            val result = instanceUnderTest.unblockEmailAddresses(request)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues!!.size shouldBe 1
            result.successValues!![0] shouldBe hashedValue1

            verify(mockApiClient).unblockEmailAddressesMutation(any())
        }

    @Test
    fun `unblockEmailAddresses() should handle partial success`() =
        runTest {
            val mutationResponse =
                DataFactory.unblockEmailAddressesMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.PARTIAL,
                    successAddresses = listOf(hashedValue1),
                    failedAddresses = listOf(hashedValue2),
                )

            mockApiClient.stub {
                onBlocking { unblockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                UnblockEmailAddressesRequest(
                    owner = mockOwner,
                    hashedBlockedValues = listOf(hashedValue1, hashedValue2),
                )

            val result = instanceUnderTest.unblockEmailAddresses(request)

            result.status shouldBe BatchOperationStatusEntity.PARTIAL
            result.successValues shouldBe listOf(hashedValue1)
            result.failureValues shouldBe listOf(hashedValue2)

            verify(mockApiClient).unblockEmailAddressesMutation(any())
        }

    @Test
    fun `unblockEmailAddresses() should handle failure status`() =
        runTest {
            val mutationResponse =
                DataFactory.unblockEmailAddressesMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.FAILED,
                    successAddresses = emptyList(),
                    failedAddresses = listOf(hashedValue1, hashedValue2),
                )

            mockApiClient.stub {
                onBlocking { unblockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                UnblockEmailAddressesRequest(
                    owner = mockOwner,
                    hashedBlockedValues = listOf(hashedValue1, hashedValue2),
                )

            val result = instanceUnderTest.unblockEmailAddresses(request)

            result.status shouldBe BatchOperationStatusEntity.FAILURE
            result.successValues shouldBe emptyList()
            result.failureValues shouldBe listOf(hashedValue1, hashedValue2)

            verify(mockApiClient).unblockEmailAddressesMutation(any())
        }

    @Test
    fun `unblockEmailAddresses() should handle empty success and failure lists`() =
        runTest {
            val mutationResponse =
                DataFactory.unblockEmailAddressesMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
                    successAddresses = emptyList(),
                    failedAddresses = emptyList(),
                )

            mockApiClient.stub {
                onBlocking { unblockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                UnblockEmailAddressesRequest(
                    owner = mockOwner,
                    hashedBlockedValues = listOf(hashedValue1),
                )

            val result = instanceUnderTest.unblockEmailAddresses(request)

            result.successValues shouldBe emptyList()
            result.failureValues shouldBe emptyList()

            verify(mockApiClient).unblockEmailAddressesMutation(any())
        }

    @Test
    fun `unblockEmailAddresses() should pass correct mockOwner to mutation`() =
        runTest {
            val customOwner = "customOwner123"
            val mutationResponse =
                DataFactory.unblockEmailAddressesMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
                    successAddresses = listOf(hashedValue1),
                    failedAddresses = emptyList(),
                )

            mockApiClient.stub {
                onBlocking { unblockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                UnblockEmailAddressesRequest(
                    owner = customOwner,
                    hashedBlockedValues = listOf(hashedValue1),
                )

            instanceUnderTest.unblockEmailAddresses(request)

            verify(mockApiClient).unblockEmailAddressesMutation(
                check {
                    it.owner shouldBe customOwner
                },
            )
        }

    @Test
    fun `unblockEmailAddresses() should throw when mutation returns errors`() =
        runTest {
            val errorResponse =
                GraphQLResponse<UnblockEmailAddressesMutation.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "InvalidArgument",
                            emptyList(),
                            emptyList(),
                            mapOf("errorType" to "InvalidArgument"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking { unblockEmailAddressesMutation(any()) } doReturn errorResponse
            }

            val request =
                UnblockEmailAddressesRequest(
                    owner = mockOwner,
                    hashedBlockedValues = listOf(hashedValue1),
                )

            shouldThrow<SudoEmailClient.EmailBlocklistException> {
                instanceUnderTest.unblockEmailAddresses(request)
            }

            verify(mockApiClient).unblockEmailAddressesMutation(any())
        }

    @Test
    fun `unblockEmailAddresses() should throw FailedException when response data is null`() =
        runTest {
            val nullDataResponse =
                GraphQLResponse<UnblockEmailAddressesMutation.Data>(
                    null,
                    null,
                )

            mockApiClient.stub {
                onBlocking { unblockEmailAddressesMutation(any()) } doReturn nullDataResponse
            }

            val request =
                UnblockEmailAddressesRequest(
                    owner = mockOwner,
                    hashedBlockedValues = listOf(hashedValue1),
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailBlocklistException.FailedException> {
                    instanceUnderTest.unblockEmailAddresses(request)
                }

            exception.message shouldBe StringConstants.UNKNOWN_ERROR_MSG

            verify(mockApiClient).unblockEmailAddressesMutation(any())
        }

    @Test
    fun `unblockEmailAddresses() should throw when API client throws exception`() =
        runTest {
            mockApiClient.stub {
                onBlocking { unblockEmailAddressesMutation(any()) } doThrow RuntimeException("Network error")
            }

            val request =
                UnblockEmailAddressesRequest(
                    owner = mockOwner,
                    hashedBlockedValues = listOf(hashedValue1),
                )

            shouldThrow<SudoEmailClient.EmailBlocklistException> {
                instanceUnderTest.unblockEmailAddresses(request)
            }

            verify(mockApiClient).unblockEmailAddressesMutation(any())
        }

    @Test
    fun `unblockEmailAddresses() should handle multiple addresses`() =
        runTest {
            val hashedValue3 = "hashedValue3"
            val mutationResponse =
                DataFactory.unblockEmailAddressesMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
                    successAddresses = listOf(hashedValue1, hashedValue2, hashedValue3),
                    failedAddresses = emptyList(),
                )

            mockApiClient.stub {
                onBlocking { unblockEmailAddressesMutation(any()) } doReturn mutationResponse
            }

            val request =
                UnblockEmailAddressesRequest(
                    owner = mockOwner,
                    hashedBlockedValues = listOf(hashedValue1, hashedValue2, hashedValue3),
                )

            val result = instanceUnderTest.unblockEmailAddresses(request)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues!!.size shouldBe 3
            result.successValues shouldBe listOf(hashedValue1, hashedValue2, hashedValue3)

            verify(mockApiClient).unblockEmailAddressesMutation(
                check {
                    it.unblockedAddresses.size shouldBe 3
                },
            )
        }

    @Test
    fun `getEmailAddressBlocklist() should return blocked addresses successfully`() =
        runTest {
            val blockedAddress1 =
                DataFactory.GetEmailAddressBlocklistQueryDataValues(
                    sealedData = sealedData1,
                    hashedValue = hashedValue1,
                    action = BlockedAddressAction.DROP,
                    emailAddressId = mockEmailAddressId,
                )
            val blockedAddress2 =
                DataFactory.GetEmailAddressBlocklistQueryDataValues(
                    sealedData = sealedData2,
                    hashedValue = hashedValue2,
                    action = BlockedAddressAction.SPAM,
                    emailAddressId = null,
                )

            val queryResponse =
                DataFactory.getEmailAddressBlocklistQueryResponse(
                    blockedAddressesData = listOf(blockedAddress1, blockedAddress2),
                )

            mockApiClient.stub {
                onBlocking { getEmailAddressBlocklistQuery(any()) } doReturn queryResponse
            }

            val request =
                GetEmailAddressBlocklistRequest(
                    owner = mockOwner,
                )

            val result = instanceUnderTest.getEmailAddressBlocklist(request)

            result.size shouldBe 2
            result[0].hashedBlockedValue shouldBe hashedValue1
            result[0].sealedValue.base64EncodedSealedData shouldBe sealedData1
            result[1].hashedBlockedValue shouldBe hashedValue2
            result[1].sealedValue.base64EncodedSealedData shouldBe sealedData2

            verify(mockApiClient).getEmailAddressBlocklistQuery(
                check {
                    it.owner shouldBe mockOwner
                },
            )
        }

    @Test
    fun `getEmailAddressBlocklist() should return empty list when no blocked addresses`() =
        runTest {
            val queryResponse =
                DataFactory.getEmailAddressBlocklistQueryResponse(
                    blockedAddressesData = emptyList(),
                )

            mockApiClient.stub {
                onBlocking { getEmailAddressBlocklistQuery(any()) } doReturn queryResponse
            }

            val request =
                GetEmailAddressBlocklistRequest(
                    owner = mockOwner,
                )

            val result = instanceUnderTest.getEmailAddressBlocklist(request)

            result shouldBe emptyList()

            verify(mockApiClient).getEmailAddressBlocklistQuery(any())
        }

    @Test
    fun `getEmailAddressBlocklist() should handle single blocked address`() =
        runTest {
            val blockedAddress =
                DataFactory.GetEmailAddressBlocklistQueryDataValues(
                    sealedData = sealedData1,
                    hashedValue = hashedValue1,
                    action = BlockedAddressAction.DROP,
                    emailAddressId = mockEmailAddressId,
                )

            val queryResponse =
                DataFactory.getEmailAddressBlocklistQueryResponse(
                    blockedAddressesData = listOf(blockedAddress),
                )

            mockApiClient.stub {
                onBlocking { getEmailAddressBlocklistQuery(any()) } doReturn queryResponse
            }

            val request =
                GetEmailAddressBlocklistRequest(
                    owner = mockOwner,
                )

            val result = instanceUnderTest.getEmailAddressBlocklist(request)

            result.size shouldBe 1
            result[0].hashedBlockedValue shouldBe hashedValue1

            verify(mockApiClient).getEmailAddressBlocklistQuery(any())
        }

    @Test
    fun `getEmailAddressBlocklist() should handle blocked addresses with different actions`() =
        runTest {
            val dropAddress =
                DataFactory.GetEmailAddressBlocklistQueryDataValues(
                    sealedData = sealedData1,
                    hashedValue = hashedValue1,
                    action = BlockedAddressAction.DROP,
                    emailAddressId = null,
                )
            val spamAddress =
                DataFactory.GetEmailAddressBlocklistQueryDataValues(
                    sealedData = sealedData2,
                    hashedValue = hashedValue2,
                    action = BlockedAddressAction.SPAM,
                    emailAddressId = mockEmailAddressId,
                )

            val queryResponse =
                DataFactory.getEmailAddressBlocklistQueryResponse(
                    blockedAddressesData = listOf(dropAddress, spamAddress),
                )

            mockApiClient.stub {
                onBlocking { getEmailAddressBlocklistQuery(any()) } doReturn queryResponse
            }

            val request =
                GetEmailAddressBlocklistRequest(
                    owner = mockOwner,
                )

            val result = instanceUnderTest.getEmailAddressBlocklist(request)

            result.size shouldBe 2
            result[0].action shouldBe BlockedEmailAddressActionEntity.DROP
            result[1].action shouldBe BlockedEmailAddressActionEntity.SPAM

            verify(mockApiClient).getEmailAddressBlocklistQuery(any())
        }

    @Test
    fun `getEmailAddressBlocklist() should handle addresses with and without mockEmailAddressId`() =
        runTest {
            val withEmailAddressId =
                DataFactory.GetEmailAddressBlocklistQueryDataValues(
                    sealedData = sealedData1,
                    hashedValue = hashedValue1,
                    action = BlockedAddressAction.DROP,
                    emailAddressId = mockEmailAddressId,
                )
            val withoutEmailAddressId =
                DataFactory.GetEmailAddressBlocklistQueryDataValues(
                    sealedData = sealedData2,
                    hashedValue = hashedValue2,
                    action = BlockedAddressAction.DROP,
                    emailAddressId = null,
                )

            val queryResponse =
                DataFactory.getEmailAddressBlocklistQueryResponse(
                    blockedAddressesData = listOf(withEmailAddressId, withoutEmailAddressId),
                )

            mockApiClient.stub {
                onBlocking { getEmailAddressBlocklistQuery(any()) } doReturn queryResponse
            }

            val request =
                GetEmailAddressBlocklistRequest(
                    owner = mockOwner,
                )

            val result = instanceUnderTest.getEmailAddressBlocklist(request)

            result.size shouldBe 2
            result[0].emailAddressId shouldBe mockEmailAddressId
            result[1].emailAddressId shouldBe null

            verify(mockApiClient).getEmailAddressBlocklistQuery(any())
        }

    @Test
    fun `getEmailAddressBlocklist() should throw when query returns errors`() =
        runTest {
            val errorResponse =
                GraphQLResponse<GetEmailAddressBlocklistQuery.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "InvalidArgument",
                            emptyList(),
                            emptyList(),
                            mapOf("errorType" to "InvalidArgument"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking { getEmailAddressBlocklistQuery(any()) } doReturn errorResponse
            }

            val request =
                GetEmailAddressBlocklistRequest(
                    owner = mockOwner,
                )

            shouldThrow<SudoEmailClient.EmailBlocklistException> {
                instanceUnderTest.getEmailAddressBlocklist(request)
            }

            verify(mockApiClient).getEmailAddressBlocklistQuery(any())
        }

    @Test
    fun `getEmailAddressBlocklist() should return empty list when response data is null`() =
        runTest {
            val nullDataResponse =
                GraphQLResponse<GetEmailAddressBlocklistQuery.Data>(
                    null,
                    null,
                )

            mockApiClient.stub {
                onBlocking { getEmailAddressBlocklistQuery(any()) } doReturn nullDataResponse
            }

            val request =
                GetEmailAddressBlocklistRequest(
                    owner = mockOwner,
                )

            val result = instanceUnderTest.getEmailAddressBlocklist(request)

            result shouldBe emptyList()

            verify(mockApiClient).getEmailAddressBlocklistQuery(any())
        }

    @Test
    fun `getEmailAddressBlocklist() should throw when API client throws exception`() =
        runTest {
            mockApiClient.stub {
                onBlocking { getEmailAddressBlocklistQuery(any()) } doThrow RuntimeException("Network error")
            }

            val request =
                GetEmailAddressBlocklistRequest(
                    owner = mockOwner,
                )

            shouldThrow<SudoEmailClient.EmailBlocklistException> {
                instanceUnderTest.getEmailAddressBlocklist(request)
            }

            verify(mockApiClient).getEmailAddressBlocklistQuery(any())
        }
}
