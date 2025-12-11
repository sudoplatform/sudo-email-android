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
import com.sudoplatform.sudoemail.util.StringHasher
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
 * Test the correct operation of [UnblockEmailAddressesUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class UnblockEmailAddressesUseCaseTest : BaseTests() {
    private val subject = "subject"
    private val address1 = "spammer@example.com"
    private val address2 = "phisher@example.com"

    // Calculate actual hashed values that the use case will generate
    private val hashedValue1 = StringHasher.hashString("$subject|$address1")
    private val hashedValue2 = StringHasher.hashString("$subject|$address2")

    private val mockUnblockEmailAddressesByHashedValueUseCase by before {
        mock<UnblockEmailAddressesByHashedValueUseCase>().stub {
            onBlocking { execute(any()) } doReturn
                BatchOperationResultEntity(
                    status = BatchOperationStatusEntity.SUCCESS,
                    successValues = listOf(hashedValue1, hashedValue2),
                    failureValues = emptyList(),
                )
        }
    }

    private val mockBlockedAddressService by before {
        mock<BlockedAddressService>()
    }

    private val mockSudoUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn subject
        }
    }

    private val useCase by before {
        UnblockEmailAddressesUseCase(
            blockedAddressService = mockBlockedAddressService,
            sudoUserClient = mockSudoUserClient,
            logger = mockLogger,
            unblockEmailAddressesByHashedValueUseCase = mockUnblockEmailAddressesByHashedValueUseCase,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockBlockedAddressService,
            mockSudoUserClient,
            mockUnblockEmailAddressesByHashedValueUseCase,
        )
    }

    @Test
    fun `execute() should unblock addresses successfully`() =
        runTest {
            val input =
                UnblockEmailAddressesUseCaseInput(
                    addresses = listOf(address1, address2),
                )

            val result = useCase.execute(input)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues shouldBe listOf(address1, address2)
            result.failureValues shouldBe emptyList()

            verify(mockSudoUserClient).getSubject()
            verify(mockUnblockEmailAddressesByHashedValueUseCase).execute(
                check {
                    it.hashedValues.size shouldBe 2
                    it.hashedValues shouldBe listOf(hashedValue1, hashedValue2)
                },
            )
        }

    @Test
    fun `execute() should throw InvalidInputException when addresses list is empty`() =
        runTest {
            val input =
                UnblockEmailAddressesUseCaseInput(
                    addresses = emptyList(),
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
                UnblockEmailAddressesUseCaseInput(
                    addresses = listOf(address1),
                )

            shouldThrow<SudoUserException.NotSignedInException> {
                useCase.execute(input)
            }

            verify(mockSudoUserClient).getSubject()
        }

    @Test
    fun `execute() should throw InvalidInputException for invalid email address`() =
        runTest {
            val invalidAddress = "not-an-email"

            val input =
                UnblockEmailAddressesUseCaseInput(
                    addresses = listOf(invalidAddress),
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.INVALID_EMAIL_ADDRESS_MSG

            verify(mockSudoUserClient).getSubject()
        }

    @Test
    fun `execute() should handle single address`() =
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
                UnblockEmailAddressesUseCaseInput(
                    addresses = listOf(address1),
                )

            val result = useCase.execute(input)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues!!.size shouldBe 1
            result.successValues!![0] shouldBe address1

            verify(mockSudoUserClient).getSubject()
            verify(mockUnblockEmailAddressesByHashedValueUseCase).execute(any())
        }

    @Test
    fun `execute() should handle partial success`() =
        runTest {
            mockUnblockEmailAddressesByHashedValueUseCase.stub {
                onBlocking { execute(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.PARTIAL,
                        successValues = listOf(hashedValue1),
                        failureValues = listOf(hashedValue2),
                    )
            }

            val input =
                UnblockEmailAddressesUseCaseInput(
                    addresses = listOf(address1, address2),
                )

            val result = useCase.execute(input)

            result.status shouldBe BatchOperationStatusEntity.PARTIAL
            result.successValues shouldBe listOf(address1)
            result.failureValues shouldBe listOf(address2)

            verify(mockSudoUserClient).getSubject()
            verify(mockUnblockEmailAddressesByHashedValueUseCase).execute(any())
        }

    @Test
    fun `execute() should handle failure status`() =
        runTest {
            mockUnblockEmailAddressesByHashedValueUseCase.stub {
                onBlocking { execute(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.FAILURE,
                        successValues = emptyList(),
                        failureValues = listOf(hashedValue1, hashedValue2),
                    )
            }

            val input =
                UnblockEmailAddressesUseCaseInput(
                    addresses = listOf(address1, address2),
                )

            val result = useCase.execute(input)

            result.status shouldBe BatchOperationStatusEntity.FAILURE
            result.successValues shouldBe emptyList()
            result.failureValues shouldBe listOf(address1, address2)

            verify(mockSudoUserClient).getSubject()
            verify(mockUnblockEmailAddressesByHashedValueUseCase).execute(any())
        }

    @Test
    fun `execute() should normalize addresses before unblocking`() =
        runTest {
            val unnormalizedAddress = "Spammer@EXAMPLE.COM"
            // Hashed value is calculated with normalized address
            val hashedNormalized = StringHasher.hashString("$subject|spammer@example.com")

            mockBlockedAddressService.stub {
                onBlocking { unblockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.SUCCESS,
                        successValues = listOf(hashedNormalized),
                        failureValues = emptyList(),
                    )
            }

            val input =
                UnblockEmailAddressesUseCaseInput(
                    addresses = listOf(unnormalizedAddress),
                )

            val result = useCase.execute(input)

            // Should return the original address, not the normalized one
            result.successValues!![0] shouldBe unnormalizedAddress

            verify(mockSudoUserClient).getSubject()
            verify(mockUnblockEmailAddressesByHashedValueUseCase).execute(
                check {
                    // Should hash the normalized address
                    it.hashedValues[0] shouldBe hashedNormalized
                },
            )
        }

    @Test
    fun `execute() should throw InvalidInputException for duplicate addresses`() =
        runTest {
            val input =
                UnblockEmailAddressesUseCaseInput(
                    addresses = listOf(address1, address1),
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.ADDRESS_BLOCKLIST_DUPLICATE_MSG

            verify(mockSudoUserClient).getSubject()
        }

    @Test
    fun `execute() should throw InvalidInputException for case-insensitive duplicates`() =
        runTest {
            val input =
                UnblockEmailAddressesUseCaseInput(
                    addresses = listOf(address1, "SPAMMER@EXAMPLE.COM"),
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.ADDRESS_BLOCKLIST_DUPLICATE_MSG

            verify(mockSudoUserClient).getSubject()
        }

    @Test
    fun `execute() should propagate exceptions from UnblockEmailAddressesByHashedValueUseCase`() =
        runTest {
            mockUnblockEmailAddressesByHashedValueUseCase.stub {
                onBlocking { execute(any()) } doThrow
                    SudoEmailClient.EmailBlocklistException.FailedException("Failed")
            }

            val input =
                UnblockEmailAddressesUseCaseInput(
                    addresses = listOf(address1),
                )

            shouldThrow<SudoEmailClient.EmailBlocklistException.FailedException> {
                useCase.execute(input)
            }

            verify(mockSudoUserClient).getSubject()
            verify(mockUnblockEmailAddressesByHashedValueUseCase).execute(any())
        }

    @Test
    fun `execute() should map hashed values back to original addresses in results`() =
        runTest {
            mockUnblockEmailAddressesByHashedValueUseCase.stub {
                onBlocking { execute(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.PARTIAL,
                        successValues = listOf(hashedValue2),
                        failureValues = listOf(hashedValue1),
                    )
            }

            val input =
                UnblockEmailAddressesUseCaseInput(
                    addresses = listOf(address1, address2),
                )

            val result = useCase.execute(input)

            // Should map hashedValue2 back to address2 and hashedValue1 back to address1
            result.successValues shouldBe listOf(address2)
            result.failureValues shouldBe listOf(address1)

            verify(mockSudoUserClient).getSubject()
            verify(mockUnblockEmailAddressesByHashedValueUseCase).execute(any())
        }
}
