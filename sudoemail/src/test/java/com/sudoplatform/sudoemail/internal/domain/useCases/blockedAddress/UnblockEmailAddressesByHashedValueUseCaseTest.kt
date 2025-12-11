/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.blockedAddress

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationStatusEntity
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.exceptions.SudoUserException
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
 * Test the correct operation of [UnblockEmailAddressesByHashedValueUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class UnblockEmailAddressesByHashedValueUseCaseTest : BaseTests() {
    private val subject = "subject"
    private val hashedValue1 = "hashedValue1"
    private val hashedValue2 = "hashedValue2"

    private val mockBlockedAddressService by before {
        mock<BlockedAddressService>().stub {
            onBlocking { unblockEmailAddresses(any()) } doReturn
                BatchOperationResultEntity(
                    status = BatchOperationStatusEntity.SUCCESS,
                    successValues = listOf(hashedValue1, hashedValue2),
                    failureValues = emptyList(),
                )
        }
    }

    private val mockSudoUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn subject
        }
    }

    private val useCase by before {
        UnblockEmailAddressesByHashedValueUseCase(
            blockedAddressService = mockBlockedAddressService,
            sudoUserClient = mockSudoUserClient,
            logger = mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockBlockedAddressService,
            mockSudoUserClient,
        )
    }

    @Test
    fun `execute() should unblock hashed values successfully`() =
        runTest {
            val input =
                UnblockEmailAddressesByHashedValueUseCaseInput(
                    hashedValues = listOf(hashedValue1, hashedValue2),
                )

            val result = useCase.execute(input)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues shouldBe listOf(hashedValue1, hashedValue2)
            result.failureValues shouldBe emptyList()

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).unblockEmailAddresses(
                check {
                    it.owner shouldBe subject
                    it.hashedBlockedValues.size shouldBe 2
                    it.hashedBlockedValues shouldBe listOf(hashedValue1, hashedValue2)
                },
            )
        }

    @Test
    fun `execute() should throw InvalidInputException when hashed values list is empty`() =
        runTest {
            val input =
                UnblockEmailAddressesByHashedValueUseCaseInput(
                    hashedValues = emptyList(),
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.ADDRESS_BLOCKLIST_EMPTY_MSG
        }

    @Test
    fun `execute() should throw NotSignedInException when user not signed in`() =
        runTest {
            mockSudoUserClient.stub {
                on { getSubject() } doReturn null
            }

            val input =
                UnblockEmailAddressesByHashedValueUseCaseInput(
                    hashedValues = listOf(hashedValue1),
                )

            shouldThrow<SudoUserException.NotSignedInException> {
                useCase.execute(input)
            }

            verify(mockSudoUserClient).getSubject()
        }

    @Test
    fun `execute() should handle single hashed value`() =
        runTest {
            mockBlockedAddressService.stub {
                onBlocking { unblockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.SUCCESS,
                        successValues = listOf(hashedValue1),
                        failureValues = emptyList(),
                    )
            }

            val input =
                UnblockEmailAddressesByHashedValueUseCaseInput(
                    hashedValues = listOf(hashedValue1),
                )

            val result = useCase.execute(input)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues!!.size shouldBe 1
            result.successValues!![0] shouldBe hashedValue1

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).unblockEmailAddresses(any())
        }

    @Test
    fun `execute() should handle partial success`() =
        runTest {
            mockBlockedAddressService.stub {
                onBlocking { unblockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.PARTIAL,
                        successValues = listOf(hashedValue1),
                        failureValues = listOf(hashedValue2),
                    )
            }

            val input =
                UnblockEmailAddressesByHashedValueUseCaseInput(
                    hashedValues = listOf(hashedValue1, hashedValue2),
                )

            val result = useCase.execute(input)

            result.status shouldBe BatchOperationStatusEntity.PARTIAL
            result.successValues shouldBe listOf(hashedValue1)
            result.failureValues shouldBe listOf(hashedValue2)

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).unblockEmailAddresses(any())
        }

    @Test
    fun `execute() should handle failure status`() =
        runTest {
            mockBlockedAddressService.stub {
                onBlocking { unblockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.FAILURE,
                        successValues = emptyList(),
                        failureValues = listOf(hashedValue1, hashedValue2),
                    )
            }

            val input =
                UnblockEmailAddressesByHashedValueUseCaseInput(
                    hashedValues = listOf(hashedValue1, hashedValue2),
                )

            val result = useCase.execute(input)

            result.status shouldBe BatchOperationStatusEntity.FAILURE
            result.successValues shouldBe emptyList()
            result.failureValues shouldBe listOf(hashedValue1, hashedValue2)

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).unblockEmailAddresses(any())
        }

    @Test
    fun `execute() should propagate exceptions from service`() =
        runTest {
            mockBlockedAddressService.stub {
                onBlocking { unblockEmailAddresses(any()) } doThrow
                    SudoEmailClient.EmailBlocklistException.FailedException("Failed")
            }

            val input =
                UnblockEmailAddressesByHashedValueUseCaseInput(
                    hashedValues = listOf(hashedValue1),
                )

            shouldThrow<SudoEmailClient.EmailBlocklistException.FailedException> {
                useCase.execute(input)
            }

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).unblockEmailAddresses(any())
        }
}
