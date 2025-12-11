/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.common.DateRangeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListAPIResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SortOrderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageDateRangeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.ListEmailMessagesOutput
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [ListEmailMessagesUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class ListEmailMessagesUseCaseTest : BaseTests() {
    private val sealedEmailMessage1 by before {
        EntityDataFactory.getSealedEmailMessageEntity(
            id = "message-id-1",
            emailAddressId = mockEmailAddressId,
            rfc822Header =
                SealedAttributeEntity(
                    keyId = mockKeyId,
                    algorithm = mockAlgorithm,
                    base64EncodedSealedData = mockSeal(DataFactory.unsealedHeaderDetailsString),
                    plainTextType = "string",
                ),
            encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
        )
    }

    private val sealedEmailMessage2 by before {
        EntityDataFactory.getSealedEmailMessageEntity(
            id = "message-id-2",
            emailAddressId = mockEmailAddressId,
            rfc822Header =
                SealedAttributeEntity(
                    keyId = mockKeyId,
                    algorithm = mockAlgorithm,
                    base64EncodedSealedData = mockSeal(DataFactory.unsealedHeaderDetailsString),
                    plainTextType = "string",
                ),
            encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
        )
    }

    override val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { decryptWithPrivateKey(anyString(), any(), any()) } doReturn ByteArray(42)
            on {
                decryptWithSymmetricKey(
                    any<ByteArray>(),
                    any<ByteArray>(),
                )
            } doReturn DataFactory.unsealedHeaderDetailsString.toByteArray()
        }
    }

    private val mockServiceKeyManager by before {
        DefaultServiceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
    }

    private val mockEmailMessageService by before {
        mock<EmailMessageService>().stub {
            onBlocking { list(any()) } doReturn
                ListEmailMessagesOutput(
                    items = listOf(sealedEmailMessage1, sealedEmailMessage2),
                    nextToken = null,
                )
            onBlocking { listForEmailAddressId(any()) } doReturn
                ListEmailMessagesOutput(
                    items = listOf(sealedEmailMessage1, sealedEmailMessage2),
                    nextToken = null,
                )
            onBlocking { listForEmailFolderId(any()) } doReturn
                ListEmailMessagesOutput(
                    items = listOf(sealedEmailMessage1, sealedEmailMessage2),
                    nextToken = null,
                )
        }
    }

    private val useCase by before {
        ListEmailMessagesUseCase(
            mockEmailMessageService,
            mockServiceKeyManager,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockEmailMessageService,
            mockKeyManager,
        )
    }

    @Test
    fun `execute() should return success result when all messages unsealed successfully`() =
        runTest {
            val input =
                ListEmailMessagesUseCaseInput(
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    val successResult = result.result
                    successResult.items.size shouldBe 2
                    successResult.items[0].id shouldBe sealedEmailMessage1.id
                    successResult.items[1].id shouldBe sealedEmailMessage2.id
                    successResult.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailMessageService).list(
                check { request ->
                    request.limit shouldBe 10
                    request.sortOrder shouldBe SortOrderEntity.DESC
                    request.includeDeletedMessages shouldBe false
                },
            )
            verify(mockKeyManager, times(2)).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager, times(2)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should return success result with nextToken when pagination is available`() =
        runTest {
            val nextToken = "next-token-value"
            mockEmailMessageService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailMessagesOutput(
                        items = listOf(sealedEmailMessage1, sealedEmailMessage2),
                        nextToken = nextToken,
                    )
            }

            val input =
                ListEmailMessagesUseCaseInput(
                    dateRange = null,
                    limit = 2,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    val successResult = result.result
                    successResult.items.size shouldBe 2
                    successResult.nextToken shouldBe nextToken
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailMessageService).list(any())
            verify(mockKeyManager, times(2)).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager, times(2)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should return empty success result when no messages exist`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailMessagesOutput(
                        items = emptyList(),
                        nextToken = null,
                    )
            }

            val input =
                ListEmailMessagesUseCaseInput(
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    val successResult = result.result
                    successResult.items.size shouldBe 0
                    successResult.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailMessageService).list(any())
        }

    @Test
    fun `execute() should return partial result when some messages fail to unseal`() =
        runTest {
            val badSealedMessage =
                EntityDataFactory.getSealedEmailMessageEntity(
                    id = "message-id-3",
                    emailAddressId = mockEmailAddressId,
                    rfc822Header =
                        SealedAttributeEntity(
                            keyId = "bad-key-id",
                            algorithm = mockAlgorithm,
                            base64EncodedSealedData = mockSeal("bad data"),
                            plainTextType = "string",
                        ),
                    encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
                )
            val exception = RuntimeException("Unsealing failed")

            mockKeyManager.stub {
                on { decryptWithPrivateKey(anyString(), any(), any()) } doReturn ByteArray(42)
                on {
                    decryptWithSymmetricKey(
                        any<ByteArray>(),
                        any<ByteArray>(),
                    )
                } doAnswer {
                    DataFactory.unsealedHeaderDetailsString.toByteArray()
                } doAnswer {
                    throw exception
                } doAnswer {
                    DataFactory.unsealedHeaderDetailsString.toByteArray()
                }
            }

            mockEmailMessageService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailMessagesOutput(
                        items = listOf(sealedEmailMessage1, badSealedMessage, sealedEmailMessage2),
                        nextToken = null,
                    )
            }

            val input =
                ListEmailMessagesUseCaseInput(
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Partial -> {
                    val partialResult = result.result
                    partialResult.items.size shouldBe 2
                    partialResult.failed.size shouldBe 1
                    partialResult.items[0].id shouldBe sealedEmailMessage1.id
                    partialResult.items[1].id shouldBe sealedEmailMessage2.id
                    partialResult.failed[0].partial.id shouldBe badSealedMessage.id
                    partialResult.failed[0].cause shouldBe exception
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailMessageService).list(any())
            verify(mockKeyManager, times(3)).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager, times(3)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should handle pagination with nextToken`() =
        runTest {
            val nextToken = "previous-token"
            val input =
                ListEmailMessagesUseCaseInput(
                    dateRange = null,
                    limit = 10,
                    nextToken = nextToken,
                    sortOrder = SortOrderEntity.ASC,
                    includeDeletedMessages = false,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    // Success - as expected
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailMessageService).list(
                check { request ->
                    request.nextToken shouldBe nextToken
                    request.sortOrder shouldBe SortOrderEntity.ASC
                },
            )
            verify(mockKeyManager, times(2)).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager, times(2)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should handle date range filter`() =
        runTest {
            val dateRange =
                EmailMessageDateRangeEntity(
                    sortDate =
                        DateRangeEntity(
                            startDate = java.util.Date(1000),
                            endDate = java.util.Date(2000),
                        ),
                )

            val input =
                ListEmailMessagesUseCaseInput(
                    dateRange = dateRange,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    // Success - as expected
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailMessageService).list(
                check { request ->
                    request.dateRange shouldBe dateRange
                },
            )
            verify(mockKeyManager, times(2)).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager, times(2)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should include deleted messages when specified`() =
        runTest {
            val input =
                ListEmailMessagesUseCaseInput(
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = true,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    // Success - as expected
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailMessageService).list(
                check { request ->
                    request.includeDeletedMessages shouldBe true
                },
            )
            verify(mockKeyManager, times(2)).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager, times(2)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking { list(any()) } doThrow NotAuthorizedException("Mock")
            }

            val input =
                ListEmailMessagesUseCaseInput(
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
                useCase.execute(input)
            }

            verify(mockEmailMessageService).list(any())
        }

    @Test
    fun `execute() should handle null limit parameter`() =
        runTest {
            val input =
                ListEmailMessagesUseCaseInput(
                    dateRange = null,
                    limit = null,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    // Success - as expected
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailMessageService).list(
                check { request ->
                    request.limit shouldBe null
                },
            )
            verify(mockKeyManager, times(2)).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager, times(2)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should handle encrypted email messages`() =
        runTest {
            val encryptedMessage =
                EntityDataFactory.getSealedEmailMessageEntity(
                    id = "encrypted-message-id",
                    emailAddressId = mockEmailAddressId,
                    rfc822Header =
                        SealedAttributeEntity(
                            keyId = mockKeyId,
                            algorithm = mockAlgorithm,
                            base64EncodedSealedData = mockSeal(DataFactory.unsealedHeaderDetailsString),
                            plainTextType = "string",
                        ),
                    encryptionStatus = EncryptionStatusEntity.ENCRYPTED,
                )

            mockEmailMessageService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailMessagesOutput(
                        items = listOf(encryptedMessage),
                        nextToken = null,
                    )
            }

            val input =
                ListEmailMessagesUseCaseInput(
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    val successResult = result.result
                    successResult.items.size shouldBe 1
                    successResult.items[0].encryptionStatus shouldBe EncryptionStatusEntity.ENCRYPTED
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailMessageService).list(any())
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should return partial result when all messages fail to unseal`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithPrivateKey(anyString(), any(), any()) } doThrow RuntimeException("Decryption failed")
            }

            val input =
                ListEmailMessagesUseCaseInput(
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Partial -> {
                    val partialResult = result.result
                    partialResult.items.size shouldBe 0
                    partialResult.failed.size shouldBe 2
                    partialResult.failed[0].partial.id shouldBe "message-id-1"
                    partialResult.failed[1].partial.id shouldBe "message-id-2"
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailMessageService).list(any())
            verify(mockKeyManager, times(2)).decryptWithPrivateKey(anyString(), any(), any())
        }

    @Test
    fun `execute() should call listEmailMessagesForEmailAddressId when emailAddressId is provided`() =
        runTest {
            val emailAddressId = "test-email-address-id"
            val input =
                ListEmailMessagesUseCaseInput(
                    emailAddressId = emailAddressId,
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    val successResult = result.result
                    successResult.items.size shouldBe 2
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailMessageService).listForEmailAddressId(
                check { request ->
                    request.emailAddressId shouldBe emailAddressId
                    request.limit shouldBe 10
                    request.sortOrder shouldBe SortOrderEntity.DESC
                    request.includeDeletedMessages shouldBe false
                },
            )
            verify(mockKeyManager, times(2)).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager, times(2)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should call listEmailMessagesForEmailFolderId when emailFolderId is provided`() =
        runTest {
            val emailFolderId = "test-email-folder-id"
            val input =
                ListEmailMessagesUseCaseInput(
                    emailFolderId = emailFolderId,
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    val successResult = result.result
                    successResult.items.size shouldBe 2
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailMessageService).listForEmailFolderId(
                check { request ->
                    request.emailFolderId shouldBe emailFolderId
                    request.limit shouldBe 10
                    request.sortOrder shouldBe SortOrderEntity.DESC
                    request.includeDeletedMessages shouldBe false
                },
            )
            verify(mockKeyManager, times(2)).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager, times(2)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should throw when both emailAddressId and emailFolderId are provided`() =
        runTest {
            shouldThrow<IllegalArgumentException> {
                ListEmailMessagesUseCaseInput(
                    emailAddressId = "test-email-address-id",
                    emailFolderId = "test-email-folder-id",
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )
            }
        }

    @Test
    fun `execute() should call listEmailMessages when neither emailAddressId nor emailFolderId is provided`() =
        runTest {
            val input =
                ListEmailMessagesUseCaseInput(
                    emailAddressId = null,
                    emailFolderId = null,
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    val successResult = result.result
                    successResult.items.size shouldBe 2
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailMessageService).list(
                check { request ->
                    request.limit shouldBe 10
                    request.sortOrder shouldBe SortOrderEntity.DESC
                    request.includeDeletedMessages shouldBe false
                },
            )
            verify(mockKeyManager, times(2)).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager, times(2)).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }
}
