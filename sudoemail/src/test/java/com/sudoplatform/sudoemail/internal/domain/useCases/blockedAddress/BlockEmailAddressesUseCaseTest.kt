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
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.BlockedEmailAddressAction
import com.sudoplatform.sudoemail.types.BlockedEmailAddressLevel
import com.sudoplatform.sudoemail.util.StringHasher
import com.sudoplatform.sudokeymanager.KeyNotFoundException
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [BlockEmailAddressesUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class BlockEmailAddressesUseCaseTest : BaseTests() {
    private val subject = "subject"
    private val emailAddressId = mockEmailAddressId
    private val symmetricKeyId = "symmetricKeyId"
    private val address1 = "spammer@example.com"
    private val address2 = "phisher@example.com"

    // Calculate actual hashed values that the use case will generate
    private val hashedValue1 = StringHasher.hashString("$subject|$address1")
    private val hashedValue2 = StringHasher.hashString("$subject|$address2")
    private val sealedData1 = byteArrayOf(1, 2, 3)
    private val sealedData2 = byteArrayOf(4, 5, 6)

    private val mockBlockedAddressService by before {
        mock<BlockedAddressService>().stub {
            onBlocking { blockEmailAddresses(any()) } doReturn
                BatchOperationResultEntity(
                    status = BatchOperationStatusEntity.SUCCESS,
                    successValues = listOf(hashedValue1, hashedValue2),
                    failureValues = emptyList(),
                )
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { getCurrentSymmetricKeyId() } doReturn symmetricKeyId
        }
    }

    private val mockSudoUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn subject
        }
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            onBlocking { sealString(any(), any()) } doReturn sealedData1 doReturn sealedData2
        }
    }

    private val useCase by before {
        BlockEmailAddressesUseCase(
            blockedAddressService = mockBlockedAddressService,
            serviceKeyManager = mockServiceKeyManager,
            sudoUserClient = mockSudoUserClient,
            sealingService = mockSealingService,
            logger = mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockBlockedAddressService,
            mockServiceKeyManager,
            mockSudoUserClient,
            mockSealingService,
        )
    }

    @Test
    fun `execute() should block addresses successfully`() =
        runTest {
            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1, address2),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            val result = useCase.execute(input)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues shouldBe listOf(address1, address2)
            result.failureValues shouldBe emptyList()

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            verify(mockSealingService).sealString(symmetricKeyId, address1.toByteArray())
            verify(mockSealingService).sealString(symmetricKeyId, address2.toByteArray())
            verify(mockBlockedAddressService).blockEmailAddresses(
                check {
                    it.owner shouldBe subject
                    it.blockedAddresses.size shouldBe 2
                    it.emailAddressId shouldBe null
                },
            )
        }

    @Test
    fun `execute() should throw InvalidInputException when addresses list is empty`() =
        runTest {
            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = emptyList(),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.ADDRESS_BLOCKLIST_EMPTY_MSG
        }

    @Test
    fun `execute() should throw KeyNotFoundException when symmetric key not found`() =
        runTest {
            mockServiceKeyManager.stub {
                on { getCurrentSymmetricKeyId() } doReturn null
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            val exception =
                shouldThrow<KeyNotFoundException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.SYMMETRIC_KEY_NOT_FOUND_ERROR_MSG

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        }

    @Test
    fun `execute() should throw NotSignedInException when user not signed in`() =
        runTest {
            mockSudoUserClient.stub {
                on { getSubject() } doReturn null
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            shouldThrow<SudoUserException.NotSignedInException> {
                useCase.execute(input)
            }

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
        }

    @Test
    fun `execute() should throw InvalidInputException for invalid email address`() =
        runTest {
            val invalidAddress = "not-an-email"

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(invalidAddress),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                    useCase.execute(input)
                }

            exception.message shouldBe "Invalid email address: $invalidAddress"

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
        }

    @Test
    fun `execute() should handle single address`() =
        runTest {
            mockBlockedAddressService.stub {
                onBlocking { blockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.SUCCESS,
                        successValues = listOf(hashedValue1),
                        failureValues = emptyList(),
                    )
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            val result = useCase.execute(input)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues!!.size shouldBe 1
            result.successValues!![0] shouldBe address1

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            verify(mockSealingService).sealString(symmetricKeyId, address1.toByteArray())
            verify(mockBlockedAddressService).blockEmailAddresses(any())
        }

    @Test
    fun `execute() should handle partial success`() =
        runTest {
            mockBlockedAddressService.stub {
                onBlocking { blockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.PARTIAL,
                        successValues = listOf(hashedValue1),
                        failureValues = listOf(hashedValue2),
                    )
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1, address2),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            val result = useCase.execute(input)

            result.status shouldBe BatchOperationStatusEntity.PARTIAL
            result.successValues shouldBe listOf(address1)
            result.failureValues shouldBe listOf(address2)

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            verify(mockSealingService, times(2)).sealString(any(), any())
            verify(mockBlockedAddressService).blockEmailAddresses(any())
        }

    @Test
    fun `execute() should handle SPAM action`() =
        runTest {
            // When emailAddressId is provided, it's used as the prefix for hashing
            val hashedValueWithEmailAddressId = StringHasher.hashString("$emailAddressId|$address1")

            mockBlockedAddressService.stub {
                onBlocking { blockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.SUCCESS,
                        successValues = listOf(hashedValueWithEmailAddressId),
                        failureValues = emptyList(),
                    )
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1),
                    action = BlockedEmailAddressAction.SPAM,
                    emailAddressId = emailAddressId,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            useCase.execute(input)

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            verify(mockSealingService).sealString(any(), any())
            verify(mockBlockedAddressService).blockEmailAddresses(any())
        }

    @Test
    fun `execute() should pass emailAddressId when provided`() =
        runTest {
            // When emailAddressId is provided, it's used as the prefix for hashing
            val hashedValueWithEmailAddressId = StringHasher.hashString("$emailAddressId|$address1")

            mockBlockedAddressService.stub {
                onBlocking { blockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.SUCCESS,
                        successValues = listOf(hashedValueWithEmailAddressId),
                        failureValues = emptyList(),
                    )
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = emailAddressId,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            useCase.execute(input)

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            verify(mockSealingService).sealString(any(), any())
            verify(mockBlockedAddressService).blockEmailAddresses(
                check {
                    it.emailAddressId shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `execute() should block at DOMAIN level`() =
        runTest {
            // When blocking at DOMAIN level, only the domain is hashed
            val hashedDomain = StringHasher.hashString("$subject|example.com")

            mockBlockedAddressService.stub {
                onBlocking { blockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.SUCCESS,
                        successValues = listOf(hashedDomain),
                        failureValues = emptyList(),
                    )
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.DOMAIN,
                )

            useCase.execute(input)

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            verify(mockSealingService).sealString(
                check { it shouldBe symmetricKeyId },
                check {
                    // Should seal the domain, not the full address
                    String(it) shouldBe "example.com"
                },
            )
            verify(mockBlockedAddressService).blockEmailAddresses(any())
        }

    @Test
    fun `execute() should normalize addresses before blocking`() =
        runTest {
            val unnormalizedAddress = "Spammer@EXAMPLE.COM"
            // Hashed value is calculated with normalized address
            val hashedNormalized = StringHasher.hashString("$subject|spammer@example.com")

            mockBlockedAddressService.stub {
                onBlocking { blockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.SUCCESS,
                        successValues = listOf(hashedNormalized),
                        failureValues = emptyList(),
                    )
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(unnormalizedAddress),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            val result = useCase.execute(input)

            // Should return the original address, not the normalized one
            result.successValues!![0] shouldBe unnormalizedAddress

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            verify(mockSealingService).sealString(
                any(),
                check {
                    // Should seal the normalized address
                    String(it) shouldBe "spammer@example.com"
                },
            )
            verify(mockBlockedAddressService).blockEmailAddresses(any())
        }

    @Test
    fun `execute() should deduplicate addresses`() =
        runTest {
            // All three addresses normalize to the same value
            val hashedDeduplicated = StringHasher.hashString("$subject|spammer@example.com")

            mockBlockedAddressService.stub {
                onBlocking { blockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.SUCCESS,
                        successValues = listOf(hashedDeduplicated),
                        failureValues = emptyList(),
                    )
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1, address1, "SPAMMER@EXAMPLE.COM"),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            useCase.execute(input)

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            // Should only seal once for the deduplicated address
            verify(mockSealingService).sealString(any(), any())
            verify(mockBlockedAddressService).blockEmailAddresses(
                check {
                    it.blockedAddresses.size shouldBe 1
                },
            )
        }

    @Test
    fun `execute() should use subject as prefix when emailAddressId is null`() =
        runTest {
            val hashedDeduplicated = StringHasher.hashString("$subject|$address1")

            mockBlockedAddressService.stub {
                onBlocking { blockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.SUCCESS,
                        successValues = listOf(hashedDeduplicated),
                        failureValues = emptyList(),
                    )
            }
            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            useCase.execute(input)

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            verify(mockSealingService).sealString(any(), any())
            verify(mockBlockedAddressService).blockEmailAddresses(
                check {
                    it.owner shouldBe subject
                },
            )
        }

    @Test
    fun `execute() should propagate exceptions from service`() =
        runTest {
            mockBlockedAddressService.stub {
                onBlocking { blockEmailAddresses(any()) } doThrow
                    SudoEmailClient.EmailBlocklistException.FailedException("Failed")
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            shouldThrow<SudoEmailClient.EmailBlocklistException.FailedException> {
                useCase.execute(input)
            }

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            verify(mockSealingService).sealString(any(), any())
            verify(mockBlockedAddressService).blockEmailAddresses(any())
        }

    @Test
    fun `execute() should handle multiple invalid addresses`() =
        runTest {
            val validAddress = "valid@example.com"

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(validAddress, "invalid", "another@example.com"),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                    useCase.execute(input)
                }

            exception.message shouldBe "Invalid email address: invalid"

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            // Should seal the first valid address before failing on invalid
            verify(mockSealingService).sealString(any(), any())
        }

    @Test
    fun `execute() should map hashed values back to original addresses in results`() =
        runTest {
            mockBlockedAddressService.stub {
                onBlocking { blockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.PARTIAL,
                        successValues = listOf(hashedValue2),
                        failureValues = listOf(hashedValue1),
                    )
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1, address2),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            val result = useCase.execute(input)

            // Should map hashedValue2 back to address2 and hashedValue1 back to address1
            result.successValues shouldBe listOf(address2)
            result.failureValues shouldBe listOf(address1)

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            verify(mockSealingService, times(2)).sealString(any(), any())
            verify(mockBlockedAddressService).blockEmailAddresses(any())
        }

    @Test
    fun `execute() should handle duplicates with partial success - duplicates at start`() =
        runTest {
            // addresses list: [address1, address1, address2]
            // After deduplication: hashedBlockedValues = [hashedValue1, hashedValue2]
            // addresses at indices: 0=address1, 1=address1, 2=address2
            // If service returns hashedValue1 as success:
            // indexOf(hashedValue1) = 0, so addresses[0] = address1 ✓
            // If service returns hashedValue2 as failure:
            // indexOf(hashedValue2) = 1, so addresses[1] = address1 ✗ (should be address2)

            val hashedDuplicate = StringHasher.hashString("$subject|$address1")
            val hashedUnique = StringHasher.hashString("$subject|$address2")

            mockBlockedAddressService.stub {
                onBlocking { blockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.PARTIAL,
                        successValues = listOf(hashedDuplicate),
                        failureValues = listOf(hashedUnique),
                    )
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1, address1, address2),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            val result = useCase.execute(input)

            // This test will reveal if the bug exists
            // Expected: success=[address1], failure=[address2]
            // Actual (if bug exists): success=[address1], failure=[address1]
            result.status shouldBe BatchOperationStatusEntity.PARTIAL
            result.successValues shouldBe listOf(address1)
            result.failureValues shouldBe listOf(address2)

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            verify(mockSealingService, times(2)).sealString(any(), any())
            verify(mockBlockedAddressService).blockEmailAddresses(any())
        }

    @Test
    fun `execute() should handle duplicates with partial success - duplicates at end`() =
        runTest {
            // addresses list: [address2, address1, address1]
            // After deduplication: hashedBlockedValues = [hashedValue2, hashedValue1]
            // addresses at indices: 0=address2, 1=address1, 2=address1
            // If service returns hashedValue2 as failure:
            // indexOf(hashedValue2) = 0, so addresses[0] = address2 ✓
            // If service returns hashedValue1 as success:
            // indexOf(hashedValue1) = 1, so addresses[1] = address1 ✓

            val hashedAddress2 = StringHasher.hashString("$subject|$address2")
            val hashedAddress1 = StringHasher.hashString("$subject|$address1")

            mockBlockedAddressService.stub {
                onBlocking { blockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.PARTIAL,
                        successValues = listOf(hashedAddress1),
                        failureValues = listOf(hashedAddress2),
                    )
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address2, address1, address1),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            val result = useCase.execute(input)

            // This test will check if order matters
            result.status shouldBe BatchOperationStatusEntity.PARTIAL
            result.successValues shouldBe listOf(address1)
            result.failureValues shouldBe listOf(address2)

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            verify(mockSealingService, times(2)).sealString(any(), any())
            verify(mockBlockedAddressService).blockEmailAddresses(any())
        }

    @Test
    fun `execute() should handle duplicates with partial success - interleaved duplicates`() =
        runTest {
            // addresses list: [address1, address2, address1]
            // After deduplication: hashedBlockedValues = [hashedValue1, hashedValue2]
            // addresses at indices: 0=address1, 1=address2, 2=address1
            // If service returns hashedValue1 as failure:
            // indexOf(hashedValue1) = 0, so addresses[0] = address1 ✓
            // If service returns hashedValue2 as success:
            // indexOf(hashedValue2) = 1, so addresses[1] = address2 ✓

            val hashedAddress1 = StringHasher.hashString("$subject|$address1")
            val hashedAddress2 = StringHasher.hashString("$subject|$address2")

            mockBlockedAddressService.stub {
                onBlocking { blockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.PARTIAL,
                        successValues = listOf(hashedAddress2),
                        failureValues = listOf(hashedAddress1),
                    )
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1, address2, address1),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            val result = useCase.execute(input)

            result.status shouldBe BatchOperationStatusEntity.PARTIAL
            result.successValues shouldBe listOf(address2)
            result.failureValues shouldBe listOf(address1)

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            verify(mockSealingService, times(2)).sealString(any(), any())
            verify(mockBlockedAddressService).blockEmailAddresses(any())
        }

    @Test
    fun `execute() should handle case-insensitive duplicates with partial success`() =
        runTest {
            // addresses list: [address1, "SPAMMER@EXAMPLE.COM", address2]
            // Both address1 and uppercase version normalize to same value
            // After deduplication: hashedBlockedValues = [hashedValue1, hashedValue2]
            // addresses at indices: 0=address1, 1="SPAMMER@EXAMPLE.COM", 2=address2
            // If service returns hashedValue1 as success:
            // indexOf(hashedValue1) = 0, so addresses[0] = address1 ✓
            // But original list had uppercase at index 1, which is also address1!
            // If service returns hashedValue2 as failure:
            // indexOf(hashedValue2) = 1, so addresses[1] = "SPAMMER@EXAMPLE.COM" ✗ (should be address2)

            val uppercaseAddress1 = "SPAMMER@EXAMPLE.COM"
            val hashedNormalized = StringHasher.hashString("$subject|$address1")
            val hashedAddress2 = StringHasher.hashString("$subject|$address2")

            mockBlockedAddressService.stub {
                onBlocking { blockEmailAddresses(any()) } doReturn
                    BatchOperationResultEntity(
                        status = BatchOperationStatusEntity.PARTIAL,
                        successValues = listOf(hashedNormalized),
                        failureValues = listOf(hashedAddress2),
                    )
            }

            val input =
                BlockEmailAddressesUseCaseInput(
                    addresses = listOf(address1, uppercaseAddress1, address2),
                    action = BlockedEmailAddressAction.DROP,
                    emailAddressId = null,
                    level = BlockedEmailAddressLevel.ADDRESS,
                )

            val result = useCase.execute(input)

            // This test will reveal the bug with case-insensitive duplicates
            // Expected: success=[address1 or uppercaseAddress1], failure=[address2]
            // The bug would cause failure to contain the wrong address
            result.status shouldBe BatchOperationStatusEntity.PARTIAL
            // Success should be one of the normalized duplicates (either original is acceptable)
            (result.successValues!!.contains(address1) || result.successValues!!.contains(uppercaseAddress1)) shouldBe true
            result.failureValues shouldBe listOf(address2)

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSudoUserClient).getSubject()
            verify(mockSealingService, times(2)).sealString(any(), any())
            verify(mockBlockedAddressService).blockEmailAddresses(any())
        }
}
