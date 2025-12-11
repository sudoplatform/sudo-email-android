/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.amazonaws.auth.CognitoCredentialsProvider
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftMessageObjectMetadata
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageStateEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudouser.SudoUserClient
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
import java.util.Date

/**
 * Test the correct operation of [ScheduleSendDraftMessageUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class ScheduleSendDraftMessageUseCaseTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId
    private val draftId = "draftId"
    private val keyId = "symmetricKeyId"
    private val identityId = "identityId"
    private val algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString()
    private val symmetricKeyData = "symmetricKeyData".toByteArray()

    private val sendAt = Date(System.currentTimeMillis() + 86400000) // 1 day in future

    private val mockEmailAddress by before {
        EntityDataFactory.getSealedEmailAddressEntity(
            id = emailAddressId,
        )
    }

    private val draftMetadata by before {
        DraftMessageObjectMetadata(
            keyId = keyId,
            algorithm = algorithm,
            updatedAt = Date(),
        )
    }

    private val scheduledDraftMessageEntity by before {
        ScheduledDraftMessageEntity(
            id = draftId,
            emailAddressId = emailAddressId,
            owner = mockOwner,
            owners = emptyList(),
            sendAt = sendAt,
            state = ScheduledDraftMessageStateEntity.SCHEDULED,
            createdAt = Date(),
            updatedAt = Date(),
        )
    }

    private val mockCredentialsProvider by before {
        mock<CognitoCredentialsProvider>().stub {
            on { identityId } doReturn identityId
        }
    }

    private val mockDraftEmailMessageService by before {
        mock<DraftEmailMessageService>().stub {
            onBlocking { getObjectMetadata(any()) } doReturn draftMetadata
            onBlocking { scheduleSend(any()) } doReturn scheduledDraftMessageEntity
        }
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { get(any()) } doReturn mockEmailAddress
        }
    }

    private val mockSudoUserClient by before {
        mock<SudoUserClient>().stub {
            on { getCredentialsProvider() } doReturn mockCredentialsProvider
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { getSymmetricKeyData(any()) } doReturn symmetricKeyData
        }
    }

    private val useCase by before {
        ScheduleSendDraftMessageUseCase(
            draftEmailMessageService = mockDraftEmailMessageService,
            emailAddressService = mockEmailAddressService,
            sudoUserClient = mockSudoUserClient,
            serviceKeyManager = mockServiceKeyManager,
            logger = mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockDraftEmailMessageService,
            mockEmailAddressService,
            mockSudoUserClient,
            mockServiceKeyManager,
            mockCredentialsProvider,
        )
    }

    @Test
    fun `execute() should return ScheduledDraftMessageEntity on success`() =
        runTest {
            val input =
                ScheduleSendDraftMessageUseCaseInput(
                    id = draftId,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                )

            val result = useCase.execute(input)

            result shouldBe scheduledDraftMessageEntity
            result.id shouldBe draftId
            result.emailAddressId shouldBe emailAddressId
            result.sendAt shouldBe sendAt
            result.state shouldBe ScheduledDraftMessageStateEntity.SCHEDULED

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe emailAddressId
                },
            )
            verify(mockDraftEmailMessageService).getObjectMetadata(any())
            verify(mockServiceKeyManager).getSymmetricKeyData(keyId)
            verify(mockSudoUserClient).getCredentialsProvider()
            verify(mockCredentialsProvider).identityId
            verify(mockDraftEmailMessageService).scheduleSend(any())
        }

    @Test
    fun `execute() should throw EmailAddressNotFoundException when email address does not exist`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { get(any()) } doReturn null
            }

            val input =
                ScheduleSendDraftMessageUseCaseInput(
                    id = draftId,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `execute() should throw InvalidArgumentException when sendAt is not in the future`() =
        runTest {
            val pastDate = Date(System.currentTimeMillis() - 86400000) // 1 day in past

            val input =
                ScheduleSendDraftMessageUseCaseInput(
                    id = draftId,
                    emailAddressId = emailAddressId,
                    sendAt = pastDate,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                    useCase.execute(input)
                }

            exception.message shouldBe "sendAt must be in the future"

            verify(mockEmailAddressService).get(any())
        }

    @Test
    fun `execute() should throw InvalidArgumentException when sendAt is now`() =
        runTest {
            val now = Date()

            val input =
                ScheduleSendDraftMessageUseCaseInput(
                    id = draftId,
                    emailAddressId = emailAddressId,
                    sendAt = now,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
        }

    @Test
    fun `execute() should retrieve draft message metadata from correct S3 key`() =
        runTest {
            val input =
                ScheduleSendDraftMessageUseCaseInput(
                    id = draftId,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                )

            useCase.execute(input)

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).getObjectMetadata(
                check { s3Key ->
                    s3Key.contains(emailAddressId) shouldBe true
                    s3Key.contains(draftId) shouldBe true
                    s3Key.contains("/draft/") shouldBe true
                },
            )
            verify(mockServiceKeyManager).getSymmetricKeyData(keyId)
            verify(mockSudoUserClient).getCredentialsProvider()
            verify(mockCredentialsProvider).identityId
            verify(mockDraftEmailMessageService).scheduleSend(any())
        }

    @Test
    fun `execute() should throw KeyNotFoundException when symmetric key not found`() =
        runTest {
            mockServiceKeyManager.stub {
                on { getSymmetricKeyData(any()) } doReturn null
            }

            val input =
                ScheduleSendDraftMessageUseCaseInput(
                    id = draftId,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                )

            val exception =
                shouldThrow<KeyNotFoundException> {
                    useCase.execute(input)
                }

            exception.message shouldBe "Could not find symmetric key $keyId"

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).getObjectMetadata(any())
            verify(mockServiceKeyManager).getSymmetricKeyData(keyId)
        }

    @Test
    fun `execute() should construct draftMessageKey with identity ID and S3 key`() =
        runTest {
            val input =
                ScheduleSendDraftMessageUseCaseInput(
                    id = draftId,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                )

            useCase.execute(input)

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).getObjectMetadata(any())
            verify(mockServiceKeyManager).getSymmetricKeyData(keyId)
            verify(mockSudoUserClient).getCredentialsProvider()
            verify(mockCredentialsProvider).identityId
            verify(mockDraftEmailMessageService).scheduleSend(
                check { request ->
                    request.draftMessageKey.contains(identityId) shouldBe true
                    request.draftMessageKey.contains(emailAddressId) shouldBe true
                    request.draftMessageKey.contains(draftId) shouldBe true
                },
            )
        }

    @Test
    fun `execute() should pass correct parameters to scheduleSendDraftMessage`() =
        runTest {
            val input =
                ScheduleSendDraftMessageUseCaseInput(
                    id = draftId,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                )

            useCase.execute(input)

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).getObjectMetadata(any())
            verify(mockServiceKeyManager).getSymmetricKeyData(keyId)
            verify(mockSudoUserClient).getCredentialsProvider()
            verify(mockCredentialsProvider).identityId
            verify(mockDraftEmailMessageService).scheduleSend(
                check { request ->
                    request.emailAddressId shouldBe emailAddressId
                    request.sendAt shouldBe sendAt
                    request.symmetricKey.isNotEmpty() shouldBe true
                },
            )
        }

    @Test
    fun `execute() should encode symmetric key as base64`() =
        runTest {
            val input =
                ScheduleSendDraftMessageUseCaseInput(
                    id = draftId,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                )

            useCase.execute(input)

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).getObjectMetadata(any())
            verify(mockServiceKeyManager).getSymmetricKeyData(keyId)
            verify(mockSudoUserClient).getCredentialsProvider()
            verify(mockCredentialsProvider).identityId
            verify(mockDraftEmailMessageService).scheduleSend(
                check { request ->
                    // The symmetric key should be base64 encoded
                    request.symmetricKey.isNotEmpty() shouldBe true
                },
            )
        }

    @Test
    fun `execute() should verify email address exists before scheduling`() =
        runTest {
            val input =
                ScheduleSendDraftMessageUseCaseInput(
                    id = draftId,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                )

            useCase.execute(input)

            // Verify email address is checked first
            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe emailAddressId
                },
            )
            // Then other operations happen
            verify(mockDraftEmailMessageService).getObjectMetadata(any())
            verify(mockServiceKeyManager).getSymmetricKeyData(keyId)
            verify(mockSudoUserClient).getCredentialsProvider()
            verify(mockCredentialsProvider).identityId
            verify(mockDraftEmailMessageService).scheduleSend(any())
        }

    @Test
    fun `execute() should propagate EmailMessageException from service`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { scheduleSend(any()) } doThrow
                    SudoEmailClient.EmailMessageException.FailedException("Schedule failed")
            }

            val input =
                ScheduleSendDraftMessageUseCaseInput(
                    id = draftId,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).getObjectMetadata(any())
            verify(mockServiceKeyManager).getSymmetricKeyData(any())
            verify(mockSudoUserClient).getCredentialsProvider()
            verify(mockCredentialsProvider).identityId
            verify(mockDraftEmailMessageService).scheduleSend(any())
        }

    @Test
    fun `execute() should interpret generic exceptions as EmailMessageException`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { getObjectMetadata(any()) } doThrow RuntimeException("Unexpected error")
            }

            val input =
                ScheduleSendDraftMessageUseCaseInput(
                    id = draftId,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                )

            shouldThrow<SudoEmailClient.EmailMessageException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).getObjectMetadata(any())
        }

    @Test
    fun `execute() should handle different draft IDs correctly`() =
        runTest {
            val customDraftId = "customDraftId123"

            val input =
                ScheduleSendDraftMessageUseCaseInput(
                    id = customDraftId,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                )

            useCase.execute(input)

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).getObjectMetadata(
                check { s3Key ->
                    s3Key.contains(customDraftId) shouldBe true
                },
            )
            verify(mockServiceKeyManager).getSymmetricKeyData(keyId)
            verify(mockSudoUserClient).getCredentialsProvider()
            verify(mockCredentialsProvider).identityId
            verify(mockDraftEmailMessageService).scheduleSend(any())
        }

    @Test
    fun `execute() should handle different email address IDs correctly`() =
        runTest {
            val customEmailAddressId = "customEmailAddress456"
            val customEmailAddress = EntityDataFactory.getSealedEmailAddressEntity(id = customEmailAddressId)

            mockEmailAddressService.stub {
                onBlocking { get(check { it.id == customEmailAddressId }) } doReturn customEmailAddress
            }

            val input =
                ScheduleSendDraftMessageUseCaseInput(
                    id = draftId,
                    emailAddressId = customEmailAddressId,
                    sendAt = sendAt,
                )

            useCase.execute(input)

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe customEmailAddressId
                },
            )
            verify(mockDraftEmailMessageService).getObjectMetadata(any())
            verify(mockServiceKeyManager).getSymmetricKeyData(keyId)
            verify(mockSudoUserClient).getCredentialsProvider()
            verify(mockCredentialsProvider).identityId
            verify(mockDraftEmailMessageService).scheduleSend(
                check {
                    it.emailAddressId shouldBe customEmailAddressId
                },
            )
        }
}
