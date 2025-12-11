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
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [GetEmailMessageUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GetEmailMessageUseCaseTest : BaseTests() {
    private val sealedEmailMessageEntity by before {
        EntityDataFactory.getSealedEmailMessageEntity(
            id = mockEmailMessageId,
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
            onBlocking { get(any()) } doReturn sealedEmailMessageEntity
        }
    }

    private val useCase by before {
        GetEmailMessageUseCase(
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
    fun `execute() should return unsealed email message when message exists`() =
        runTest {
            val input = GetEmailMessageUseCaseInput(id = mockEmailMessageId)

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.id shouldBe mockEmailMessageId
            result?.emailAddressId shouldBe mockEmailAddressId

            verify(mockEmailMessageService).get(
                org.mockito.kotlin.check { request ->
                    request.id shouldBe mockEmailMessageId
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should return null when email message does not exist`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking { get(any()) } doReturn null
            }

            val input = GetEmailMessageUseCaseInput(id = "non-existent-id")

            val result = useCase.execute(input)

            result shouldBe null

            verify(mockEmailMessageService).get(
                org.mockito.kotlin.check { request ->
                    request.id shouldBe "non-existent-id"
                },
            )
        }

    @Test
    fun `execute() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking { get(any()) } doThrow NotAuthorizedException("Mock")
            }

            val input = GetEmailMessageUseCaseInput(id = mockEmailMessageId)

            shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
                useCase.execute(input)
            }

            verify(mockEmailMessageService).get(
                org.mockito.kotlin.check { request ->
                    request.id shouldBe mockEmailMessageId
                },
            )
        }

    @Test
    fun `execute() should handle unsealing encrypted email message`() =
        runTest {
            val encryptedEmailMessage =
                EntityDataFactory.getSealedEmailMessageEntity(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                    encryptionStatus = EncryptionStatusEntity.ENCRYPTED,
                    rfc822Header =
                        SealedAttributeEntity(
                            keyId = mockKeyId,
                            algorithm = mockAlgorithm,
                            base64EncodedSealedData = mockSeal("sealed encrypted data"),
                            plainTextType = "string",
                        ),
                )

            mockEmailMessageService.stub {
                onBlocking { get(any()) } doReturn encryptedEmailMessage
            }

            val input = GetEmailMessageUseCaseInput(id = mockEmailMessageId)

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.id shouldBe mockEmailMessageId
            result?.encryptionStatus shouldBe EncryptionStatusEntity.ENCRYPTED

            verify(mockEmailMessageService).get(
                org.mockito.kotlin.check { request ->
                    request.id shouldBe mockEmailMessageId
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should throw UnsealingException when decryption fails`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithPrivateKey(anyString(), any(), any()) } doThrow
                    SudoEmailClient.EmailMessageException.UnsealingException("Decryption failed")
            }

            val input = GetEmailMessageUseCaseInput(id = mockEmailMessageId)

            shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                useCase.execute(input)
            }

            verify(mockEmailMessageService).get(
                org.mockito.kotlin.check { request ->
                    request.id shouldBe mockEmailMessageId
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
        }

    @Test
    fun `execute() should handle email message with all fields`() =
        runTest {
            val completeEmailMessage =
                EntityDataFactory.getSealedEmailMessageEntity(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                    folderId = mockFolderId,
                    previousFolderId = "previousFolderId",
                    seen = true,
                    repliedTo = true,
                    forwarded = true,
                    version = 2,
                    sortDateEpochMs = 1000.0,
                    createdAtEpochMs = 1000.0,
                    updatedAtEpochMs = 2000.0,
                    size = 1024.0,
                    rfc822Header =
                        SealedAttributeEntity(
                            keyId = mockKeyId,
                            algorithm = mockAlgorithm,
                            base64EncodedSealedData = mockSeal(DataFactory.unsealedHeaderDetailsString),
                            plainTextType = "string",
                        ),
                    encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
                )

            mockEmailMessageService.stub {
                onBlocking { get(any()) } doReturn completeEmailMessage
            }

            val input = GetEmailMessageUseCaseInput(id = mockEmailMessageId)

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.id shouldBe mockEmailMessageId
            result?.seen shouldBe true
            result?.repliedTo shouldBe true
            result?.forwarded shouldBe true
            result?.version shouldBe 2

            verify(mockEmailMessageService).get(
                org.mockito.kotlin.check { request ->
                    request.id shouldBe mockEmailMessageId
                },
            )
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }
}
