/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.data.emailMessage.transformers.EmailAttachmentTransformer
import com.sudoplatform.sudoemail.internal.data.emailMessage.transformers.InternetMessageFormatHeaderTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SendEmailMessageResultEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.SendEmailMessageUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.InternetMessageFormatHeader
import com.sudoplatform.sudoemail.types.inputs.SendEmailMessageInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.sendEmailMessage]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailSendEmailMessageTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId
    private val emailAttachment =
        EmailAttachment(
            "fileName.pdf",
            "contentId",
            "mimeType",
            false,
            ByteArray(1),
        )
    private val inlineEmailAttachment =
        EmailAttachment(
            "fileName.jpg",
            "contentId",
            "mimeType",
            true,
            ByteArray(1),
        )
    private val headers =
        InternetMessageFormatHeader(
            EmailMessage.EmailAddress("from@bar.com"),
            listOf(EmailMessage.EmailAddress("to@bar.com")),
            listOf(EmailMessage.EmailAddress("cc@bar.com")),
            listOf(EmailMessage.EmailAddress("bcc@bar.com")),
            listOf(EmailMessage.EmailAddress("replyTo@bar.com")),
            "email message subject",
        )

    private val input =
        SendEmailMessageInput(
            senderEmailAddressId = emailAddressId,
            emailMessageHeader = headers,
            body = "email message body",
            attachments =
                listOf(
                    emailAttachment,
                ),
            inlineAttachment =
                listOf(
                    inlineEmailAttachment,
                ),
            replyingMessageId = "replyingMessageId",
            forwardingMessageId = "forwardingMessageId",
        )

    private val sendResult by before {
        SendEmailMessageResultEntity(
            id = "emailMessageId",
            createdAt = Date(1L),
        )
    }

    private val mockUseCase by before {
        mock<SendEmailMessageUseCase>().stub {
            onBlocking { execute(any()) } doReturn sendResult
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createSendEmailMessageUseCase() } doReturn mockUseCase
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

    private val client by before {
        DefaultSudoEmailClient(
            context = mockContext,
            serviceKeyManager = mockServiceKeyManager,
            apiClient = mockApiClient,
            sudoUserClient = mockUserClient,
            logger = mockLogger,
            region = "region",
            emailBucket = "identityBucket",
            transientBucket = "transientBucket",
            notificationHandler = null,
            s3TransientClient = mockS3Client,
            s3EmailClient = mockS3Client,
            useCaseFactory = mockUseCaseFactory,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockUseCaseFactory,
            mockUseCase,
        )
    }

    @Test
    fun `sendEmailMessage() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.sendEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockUseCaseFactory).createSendEmailMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.senderEmailAddressId shouldBe emailAddressId
                    useCaseInput.emailMessageHeader.toString() shouldBe
                        InternetMessageFormatHeaderTransformer
                            .apiToEntity(
                                input.emailMessageHeader,
                            ).toString()
                    useCaseInput.body shouldBe input.body
                    useCaseInput.attachments.toString() shouldBe
                        input.attachments.map { EmailAttachmentTransformer.apiToEntity(it) }.toString()
                    useCaseInput.inlineAttachments.toString() shouldBe
                        input.inlineAttachment.map { EmailAttachmentTransformer.apiToEntity(it) }.toString()
                    useCaseInput.forwardingMessageId shouldBe input.forwardingMessageId
                    useCaseInput.replyingMessageId shouldBe input.replyingMessageId
                },
            )
        }

    @Test
    fun `sendEmailMessage() should throw error when use case throws`() =
        runTest {
            mockUseCase.stub {
                onBlocking {
                    execute(
                        any(),
                    )
                }.thenAnswer {
                    throw SudoEmailClient.EmailMessageException.FailedException("Mock")
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.sendEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockUseCaseFactory).createSendEmailMessageUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.senderEmailAddressId shouldBe emailAddressId
                    useCaseInput.emailMessageHeader.toString() shouldBe
                        InternetMessageFormatHeaderTransformer
                            .apiToEntity(
                                input.emailMessageHeader,
                            ).toString()
                    useCaseInput.body shouldBe input.body
                    useCaseInput.attachments.toString() shouldBe
                        input.attachments.map { EmailAttachmentTransformer.apiToEntity(it) }.toString()
                    useCaseInput.inlineAttachments.toString() shouldBe
                        input.inlineAttachment.map { EmailAttachmentTransformer.apiToEntity(it) }.toString()
                    useCaseInput.forwardingMessageId shouldBe input.forwardingMessageId
                    useCaseInput.replyingMessageId shouldBe input.replyingMessageId
                },
            )
        }
}
