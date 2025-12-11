/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.amazonaws.services.s3.model.ObjectMetadata
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.GetEmailMessageRequest
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.s3.S3Client
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [GetEmailMessageRfc822DataUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GetEmailMessageRfc822DataUseCaseTest : BaseTests() {
    private val rfc822Data = DataFactory.unsealedHeaderDetailsString.toByteArray()
    private val sealedRfc822Data = mockSeal("sealed RFC822 data")
    private val mockS3Key =
        DefaultS3Client.constructS3KeyForEmailMessage(
            emailAddressId = mockEmailAddressId,
            mockEmailMessageId,
            keyId = mockKeyId,
        )
    private val mockRfc822Metadata: ObjectMetadata = ObjectMetadata()

    private val emailMessageEntity by before {
        EntityDataFactory.getSealedEmailMessageEntity(
            id = mockEmailMessageId,
            emailAddressId = mockEmailAddressId,
            rfc822Header =
                SealedAttributeEntity(
                    keyId = mockKeyId,
                    algorithm = mockAlgorithm,
                    base64EncodedSealedData = sealedRfc822Data,
                    plainTextType = "string",
                ),
            encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
        )
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
            onBlocking { get(any()) } doReturn emailMessageEntity
        }
    }

    private val mockS3EmailClient by before {
        mock<S3Client>()
    }

    private val mockRetrieveAndDecodeEmailMessageUseCase by before {
        mock<RetrieveAndDecodeEmailMessageUseCase>().stub {
            onBlocking { execute(any()) } doReturn rfc822Data
        }
    }

    private val useCase by before {
        GetEmailMessageRfc822DataUseCase(
            mockEmailMessageService,
            mockS3EmailClient,
            mockServiceKeyManager,
            mockLogger,
            mockRetrieveAndDecodeEmailMessageUseCase,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockEmailMessageService,
            mockS3EmailClient,
            mockKeyManager,
            mockRetrieveAndDecodeEmailMessageUseCase,
        )
    }

    @Test
    fun `execute() should call RetrieveAndDecodeEmailMessageUseCase and return proper result`() =
        runTest {
            val input =
                GetEmailMessageRfc822DataUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.id shouldBe mockEmailMessageId
            result?.rfc822Data shouldBe rfc822Data

            verify(mockEmailMessageService).get(
                GetEmailMessageRequest(id = mockEmailMessageId),
            )
            verify(mockRetrieveAndDecodeEmailMessageUseCase).execute(
                emailMessageEntity,
            )
        }

    @Test
    fun `execute() should return null when email message does not exist`() =
        runTest {
            mockEmailMessageService.stub {
                onBlocking { get(any()) } doReturn null
            }

            val input =
                GetEmailMessageRfc822DataUseCaseInput(
                    id = "non-existent-id",
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldBe null

            verify(mockEmailMessageService).get(
                GetEmailMessageRequest(id = "non-existent-id"),
            )
        }

    @Test
    fun `execute() should propagate exception thrown by RetrieveAndDecodeEmailMessageUseCase`() =
        runTest {
            mockRetrieveAndDecodeEmailMessageUseCase.stub {
                onBlocking { execute(any()) } doThrow SudoEmailClient.EmailMessageException.UnsealingException("Unsealing failed")
            }

            val input =
                GetEmailMessageRfc822DataUseCaseInput(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                useCase.execute(input)
            }

            verify(mockEmailMessageService).get(
                GetEmailMessageRequest(id = mockEmailMessageId),
            )
            verify(mockRetrieveAndDecodeEmailMessageUseCase).execute(
                emailMessageEntity,
            )
        }
}
