/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.blockedAddress

import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedEmailAddressActionEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.BlockedEmailAddressAction
import com.sudoplatform.sudoemail.types.UnsealedBlockedAddressStatus
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [GetEmailAddressBlocklistUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GetEmailAddressBlocklistUseCaseTest : BaseTests() {
    private val subject = "subject"
    private val symmetricKeyId = "symmetricKeyId"
    private val emailAddressId = mockEmailAddressId
    private val address1 = "spammer@example.com"
    private val address2 = "phisher@example.com"
    private val hashedValue1 = "hashedValue1"
    private val hashedValue2 = "hashedValue2"
    private val sealedData1 = String(Base64.encode(address1.toByteArray()))
    private val sealedData2 = String(Base64.encode(address2.toByteArray()))

    private val sealedAttribute1 =
        SealedAttributeEntity(
            keyId = symmetricKeyId,
            algorithm = "AES/CBC/PKCS7Padding",
            plainTextType = "plainText",
            base64EncodedSealedData = sealedData1,
        )

    private val sealedAttribute2 =
        SealedAttributeEntity(
            keyId = symmetricKeyId,
            algorithm = "AES/CBC/PKCS7Padding",
            plainTextType = "plainText",
            base64EncodedSealedData = sealedData2,
        )

    private val blockedAddress1 =
        BlockedAddressEntity(
            hashedBlockedValue = hashedValue1,
            sealedValue = sealedAttribute1,
            action = BlockedEmailAddressActionEntity.DROP,
            emailAddressId = emailAddressId,
        )

    private val blockedAddress2 =
        BlockedAddressEntity(
            hashedBlockedValue = hashedValue2,
            sealedValue = sealedAttribute2,
            action = BlockedEmailAddressActionEntity.SPAM,
            emailAddressId = null,
        )

    private val mockBlockedAddressService by before {
        mock<BlockedAddressService>().stub {
            onBlocking { getEmailAddressBlocklist(any()) } doReturn listOf(blockedAddress1, blockedAddress2)
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { symmetricKeyExists(any()) } doReturn true
        }
    }

    private val mockSudoUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn subject
        }
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            onBlocking { unsealString(symmetricKeyId, Base64.decode(sealedData1)) } doReturn address1.toByteArray()
            onBlocking { unsealString(symmetricKeyId, Base64.decode(sealedData2)) } doReturn address2.toByteArray()
        }
    }

    private val useCase by before {
        GetEmailAddressBlocklistUseCase(
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
    fun `execute() should unseal and return blocked addresses successfully`() =
        runTest {
            val result = useCase.execute()

            result.size shouldBe 2
            result[0].address shouldBe address1
            result[0].hashedBlockedValue shouldBe hashedValue1
            result[0].action shouldBe BlockedEmailAddressAction.DROP
            result[0].status shouldBe UnsealedBlockedAddressStatus.Completed
            result[0].emailAddressId shouldBe emailAddressId

            result[1].address shouldBe address2
            result[1].hashedBlockedValue shouldBe hashedValue2
            result[1].action shouldBe BlockedEmailAddressAction.SPAM
            result[1].status shouldBe UnsealedBlockedAddressStatus.Completed
            result[1].emailAddressId shouldBe null

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).getEmailAddressBlocklist(any())
            verify(mockServiceKeyManager, times(2)).symmetricKeyExists(symmetricKeyId)
            verify(mockSealingService).unsealString(symmetricKeyId, Base64.decode(sealedData1))
            verify(mockSealingService).unsealString(symmetricKeyId, Base64.decode(sealedData2))
        }

    @Test
    fun `execute() should return empty list when no blocked addresses`() =
        runTest {
            mockBlockedAddressService.stub {
                onBlocking { getEmailAddressBlocklist(any()) } doReturn emptyList()
            }

            val result = useCase.execute()

            result shouldBe emptyList()

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).getEmailAddressBlocklist(any())
        }

    @Test
    fun `execute() should throw NotSignedInException when user not signed in`() =
        runTest {
            mockSudoUserClient.stub {
                on { getSubject() } doReturn null
            }

            shouldThrow<SudoUserException.NotSignedInException> {
                useCase.execute()
            }

            verify(mockSudoUserClient).getSubject()
        }

    @Test
    fun `execute() should handle single blocked address`() =
        runTest {
            mockBlockedAddressService.stub {
                onBlocking { getEmailAddressBlocklist(any()) } doReturn listOf(blockedAddress1)
            }

            val result = useCase.execute()

            result.size shouldBe 1
            result[0].address shouldBe address1
            result[0].hashedBlockedValue shouldBe hashedValue1

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).getEmailAddressBlocklist(any())
            verify(mockServiceKeyManager).symmetricKeyExists(symmetricKeyId)
            verify(mockSealingService).unsealString(symmetricKeyId, Base64.decode(sealedData1))
        }

    @Test
    fun `execute() should return Failed status when symmetric key does not exist`() =
        runTest {
            mockServiceKeyManager.stub {
                on { symmetricKeyExists(any()) } doReturn false
            }

            val result = useCase.execute()

            result.size shouldBe 2
            result[0].address shouldBe ""
            result[0].hashedBlockedValue shouldBe hashedValue1
            result[0].status.toString() shouldBe
                UnsealedBlockedAddressStatus
                    .Failed(
                        SudoEmailClient.EmailBlocklistException.FailedException(
                            StringConstants.SYMMETRIC_KEY_NOT_FOUND_ERROR_MSG,
                            KeyNotFoundException(
                                StringConstants.SYMMETRIC_KEY_NOT_FOUND_ERROR_MSG,
                            ),
                        ),
                    ).toString()

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).getEmailAddressBlocklist(any())
            verify(mockServiceKeyManager, times(2)).symmetricKeyExists(symmetricKeyId)
        }

    @Test
    fun `execute() should return Failed status when unsealing throws exception`() =
        runTest {
            mockSealingService.stub {
                onBlocking { unsealString(any(), any()) } doThrow RuntimeException("Unsealing failed")
            }

            val result = useCase.execute()

            result.size shouldBe 2
            result[0].address shouldBe ""
            result[0].hashedBlockedValue shouldBe hashedValue1
            result[0].action shouldBe BlockedEmailAddressAction.DROP
            (result[0].status is UnsealedBlockedAddressStatus.Failed) shouldBe true

            result[1].address shouldBe ""
            result[1].hashedBlockedValue shouldBe hashedValue2
            result[1].action shouldBe BlockedEmailAddressAction.SPAM
            (result[1].status is UnsealedBlockedAddressStatus.Failed) shouldBe true

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).getEmailAddressBlocklist(any())
            verify(mockServiceKeyManager, times(2)).symmetricKeyExists(symmetricKeyId)
            verify(mockSealingService).unsealString(symmetricKeyId, Base64.decode(sealedData1))
            verify(mockSealingService).unsealString(symmetricKeyId, Base64.decode(sealedData2))
        }

    @Test
    fun `execute() should handle partial unsealing failure`() =
        runTest {
            mockSealingService.stub {
                onBlocking { unsealString(symmetricKeyId, Base64.decode(sealedData1)) } doReturn address1.toByteArray()
                onBlocking { unsealString(symmetricKeyId, Base64.decode(sealedData2)) } doThrow RuntimeException("Unsealing failed")
            }

            val result = useCase.execute()

            result.size shouldBe 2
            result[0].address shouldBe address1
            result[0].status shouldBe UnsealedBlockedAddressStatus.Completed

            result[1].address shouldBe ""
            (result[1].status is UnsealedBlockedAddressStatus.Failed) shouldBe true

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).getEmailAddressBlocklist(any())
            verify(mockServiceKeyManager, times(2)).symmetricKeyExists(symmetricKeyId)
            verify(mockSealingService).unsealString(symmetricKeyId, Base64.decode(sealedData1))
            verify(mockSealingService).unsealString(symmetricKeyId, Base64.decode(sealedData2))
        }

    @Test
    fun `execute() should propagate exceptions from service`() =
        runTest {
            mockBlockedAddressService.stub {
                onBlocking { getEmailAddressBlocklist(any()) } doThrow
                    SudoEmailClient.EmailBlocklistException.FailedException("Failed")
            }

            shouldThrow<SudoEmailClient.EmailBlocklistException.FailedException> {
                useCase.execute()
            }

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).getEmailAddressBlocklist(any())
        }

    @Test
    fun `execute() should wrap unexpected exceptions`() =
        runTest {
            mockBlockedAddressService.stub {
                onBlocking { getEmailAddressBlocklist(any()) } doThrow RuntimeException("Unexpected error")
            }

            shouldThrow<SudoEmailClient.EmailBlocklistException> {
                useCase.execute()
            }

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).getEmailAddressBlocklist(any())
        }

    @Test
    fun `execute() should handle different key IDs for different addresses`() =
        runTest {
            val otherKeyId = "otherKeyId"
            val otherSealedAttribute =
                SealedAttributeEntity(
                    keyId = otherKeyId,
                    algorithm = "AES/CBC/PKCS7Padding",
                    plainTextType = "plainText",
                    base64EncodedSealedData = String(Base64.encode(address2.toByteArray())),
                )
            val blockedAddressWithDifferentKey =
                BlockedAddressEntity(
                    hashedBlockedValue = hashedValue2,
                    sealedValue = otherSealedAttribute,
                    action = BlockedEmailAddressActionEntity.SPAM,
                    emailAddressId = null,
                )

            mockBlockedAddressService.stub {
                onBlocking { getEmailAddressBlocklist(any()) } doReturn
                    listOf(
                        blockedAddress1,
                        blockedAddressWithDifferentKey,
                    )
            }

            mockServiceKeyManager.stub {
                on { symmetricKeyExists(symmetricKeyId) } doReturn true
                on { symmetricKeyExists(otherKeyId) } doReturn true
            }

            mockSealingService.stub {
                onBlocking { unsealString(symmetricKeyId, Base64.decode(sealedData1)) } doReturn address1.toByteArray()
                onBlocking { unsealString(otherKeyId, Base64.decode(sealedData2)) } doReturn address2.toByteArray()
            }

            val result = useCase.execute()

            result.size shouldBe 2
            result[0].address shouldBe address1
            result[1].address shouldBe address2

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).getEmailAddressBlocklist(any())
            verify(mockServiceKeyManager).symmetricKeyExists(symmetricKeyId)
            verify(mockServiceKeyManager).symmetricKeyExists(otherKeyId)
            verify(mockSealingService).unsealString(symmetricKeyId, Base64.decode(sealedData1))
            verify(mockSealingService).unsealString(otherKeyId, Base64.decode(sealedData2))
        }

    @Test
    fun `execute() should preserve action types during unsealing`() =
        runTest {
            val dropAddress =
                BlockedAddressEntity(
                    hashedBlockedValue = hashedValue1,
                    sealedValue = sealedAttribute1,
                    action = BlockedEmailAddressActionEntity.DROP,
                    emailAddressId = null,
                )
            val spamAddress =
                BlockedAddressEntity(
                    hashedBlockedValue = hashedValue2,
                    sealedValue = sealedAttribute2,
                    action = BlockedEmailAddressActionEntity.SPAM,
                    emailAddressId = emailAddressId,
                )

            mockBlockedAddressService.stub {
                onBlocking { getEmailAddressBlocklist(any()) } doReturn listOf(dropAddress, spamAddress)
            }

            val result = useCase.execute()

            result.size shouldBe 2
            result[0].action shouldBe BlockedEmailAddressAction.DROP
            result[1].action shouldBe BlockedEmailAddressAction.SPAM

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).getEmailAddressBlocklist(any())
            verify(mockServiceKeyManager, times(2)).symmetricKeyExists(symmetricKeyId)
            verify(mockSealingService).unsealString(symmetricKeyId, Base64.decode(sealedData1))
            verify(mockSealingService).unsealString(symmetricKeyId, Base64.decode(sealedData2))
        }

    @Test
    fun `execute() should preserve emailAddressId during unsealing`() =
        runTest {
            val customEmailAddressId = "customEmailAddressId"
            val customBlockedAddress =
                BlockedAddressEntity(
                    hashedBlockedValue = hashedValue1,
                    sealedValue = sealedAttribute1,
                    action = BlockedEmailAddressActionEntity.DROP,
                    emailAddressId = customEmailAddressId,
                )

            mockBlockedAddressService.stub {
                onBlocking { getEmailAddressBlocklist(any()) } doReturn listOf(customBlockedAddress)
            }

            val result = useCase.execute()

            result.size shouldBe 1
            result[0].emailAddressId shouldBe customEmailAddressId

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).getEmailAddressBlocklist(any())
            verify(mockServiceKeyManager).symmetricKeyExists(symmetricKeyId)
            verify(mockSealingService).unsealString(symmetricKeyId, Base64.decode(sealedData1))
        }

    @Test
    fun `execute() should include exception in Failed status`() =
        runTest {
            val errorMessage = "Custom unsealing error"
            mockSealingService.stub {
                onBlocking { unsealString(any(), any()) } doThrow RuntimeException(errorMessage)
            }

            val result = useCase.execute()

            result.size shouldBe 2
            (result[0].status is UnsealedBlockedAddressStatus.Failed) shouldBe true
            val failedStatus0 = result[0].status as UnsealedBlockedAddressStatus.Failed
            (failedStatus0.cause is SudoEmailClient.EmailBlocklistException.FailedException) shouldBe true

            verify(mockSudoUserClient).getSubject()
            verify(mockBlockedAddressService).getEmailAddressBlocklist(any())
            verify(mockServiceKeyManager, times(2)).symmetricKeyExists(symmetricKeyId)
            verify(mockSealingService).unsealString(symmetricKeyId, Base64.decode(sealedData1))
            verify(mockSealingService).unsealString(symmetricKeyId, Base64.decode(sealedData2))
        }
}
