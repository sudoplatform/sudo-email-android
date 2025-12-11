/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage

import com.amplifyframework.api.graphql.GraphQLResponse
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.graphql.GetEmailMessageQuery
import com.sudoplatform.sudoemail.graphql.ListEmailMessagesForEmailAddressIdQuery
import com.sudoplatform.sudoemail.graphql.ListEmailMessagesForEmailFolderIdQuery
import com.sudoplatform.sudoemail.graphql.ListEmailMessagesQuery
import com.sudoplatform.sudoemail.graphql.fragment.UpdateEmailMessagesResult
import com.sudoplatform.sudoemail.graphql.type.EmailMessageState
import com.sudoplatform.sudoemail.graphql.type.SortOrder
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailMessagesStatus
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.DateRangeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SortOrderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.DeleteMessageForFolderIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailAttachmentEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageDateRangeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.GetEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.InternetMessageFormatHeaderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.ListEmailMessagesForEmailAddressIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.ListEmailMessagesForEmailFolderIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.ListEmailMessagesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SendEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SendEncryptedEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.UpdateEmailMessagesRequest
import com.sudoplatform.sudoemail.s3.DefaultS3Client
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
 * Test the correct operation of [GraphQLEmailMessageService]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GraphQLEmailMessageServiceTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId
    private val messageId = "message-id-123"
    private val s3Key = DefaultS3Client.constructS3KeyForDraftEmailMessage(emailAddressId, messageId)
    override val mockApiClient by before {
        mock<ApiClient>()
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

    private val instanceUnderTest by before {
        GraphQLEmailMessageService(
            mockApiClient,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockApiClient,
            mockKeyManager,
        )
    }

    /** Begin SendEmailMessageTests */

    @Test
    fun `sendEmailMessage() should return result when successful`() =
        runTest {
            val messageId = "message-id-123"
            val s3Key = DefaultS3Client.constructS3KeyForDraftEmailMessage(emailAddressId, messageId)
            val createdAtEpochMs = 1000.0
            val mutationResponse = DataFactory.sendEmailMessageMutationResponse(messageId, createdAtEpochMs)
            mockApiClient.stub {
                onBlocking {
                    sendEmailMessageMutation(any())
                } doReturn mutationResponse
            }

            val request =
                SendEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "us-east-1",
                    transientBucket = "transient-bucket",
                )

            val result = instanceUnderTest.send(request)

            result shouldNotBe null
            result.id shouldBe messageId
            result.createdAt shouldBe Date(1000)

            verify(mockApiClient).sendEmailMessageMutation(
                check { input ->
                    input.emailAddressId shouldBe emailAddressId
                    input.message.key shouldBe s3Key
                    input.message.region shouldBe "us-east-1"
                    input.message.bucket shouldBe "transient-bucket"
                },
            )
        }

    @Test
    fun `sendEmailMessage() should use correct emailAddressId`() =
        runTest {
            val customEmailAddressId = "custom-email-address-id-12345"
            val s3Key = DefaultS3Client.constructS3KeyForDraftEmailMessage(customEmailAddressId, messageId)
            val mutationResponse = DataFactory.sendEmailMessageMutationResponse("messageId", 1000.0)
            mockApiClient.stub {
                onBlocking {
                    sendEmailMessageMutation(any())
                } doReturn mutationResponse
            }

            val request =
                SendEmailMessageRequest(
                    emailAddressId = customEmailAddressId,
                    s3ObjectKey = s3Key,
                    region = "us-west-2",
                    transientBucket = "bucket",
                )

            val result = instanceUnderTest.send(request)

            result shouldNotBe null

            verify(mockApiClient).sendEmailMessageMutation(
                check { input ->
                    input.emailAddressId shouldBe customEmailAddressId
                },
            )
        }

    @Test
    fun `sendEmailMessage() should use correct S3 parameters`() =
        runTest {
            val mutationResponse = DataFactory.sendEmailMessageMutationResponse("messageId", 1000.0)
            mockApiClient.stub {
                onBlocking {
                    sendEmailMessageMutation(any())
                } doReturn mutationResponse
            }

            val request =
                SendEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "eu-west-1",
                    transientBucket = "custom-transient-bucket",
                )

            val result = instanceUnderTest.send(request)

            result shouldNotBe null

            verify(mockApiClient).sendEmailMessageMutation(
                check { input ->
                    input.message.key shouldBe s3Key
                    input.message.region shouldBe "eu-west-1"
                    input.message.bucket shouldBe "custom-transient-bucket"
                },
            )
        }

    @Test
    fun `sendEmailMessage() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            mockApiClient.stub {
                onBlocking {
                    sendEmailMessageMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                SendEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "us-east-1",
                    transientBucket = "bucket",
                )

            shouldThrow<SudoEmailClient.EmailMessageException> {
                instanceUnderTest.send(request)
            }

            verify(mockApiClient).sendEmailMessageMutation(any())
        }

    @Test
    fun `sendEmailMessage() should throw when result is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    sendEmailMessageMutation(any())
                } doReturn GraphQLResponse(null, null)
            }

            val request =
                SendEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "us-east-1",
                    transientBucket = "bucket",
                )

            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                instanceUnderTest.send(request)
            }

            verify(mockApiClient).sendEmailMessageMutation(any())
        }

    @Test
    fun `sendEmailMessage() should handle different regions`() =
        runTest {
            val mutationResponse = DataFactory.sendEmailMessageMutationResponse("messageId", 2000.0)
            mockApiClient.stub {
                onBlocking {
                    sendEmailMessageMutation(any())
                } doReturn mutationResponse
            }

            val request =
                SendEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "ap-southeast-1",
                    transientBucket = "bucket",
                )

            val result = instanceUnderTest.send(request)

            result shouldNotBe null

            verify(mockApiClient).sendEmailMessageMutation(
                check { input ->
                    input.message.region shouldBe "ap-southeast-1"
                },
            )
        }

    /** Begin SendEncryptedEmailMessageTests */

    @Test
    fun `sendEncryptedEmailMessage() should return result when successful`() =
        runTest {
            val messageId = "encrypted-message-id-123"
            val s3Key = DefaultS3Client.constructS3KeyForDraftEmailMessage(emailAddressId, messageId)
            val createdAtEpochMs = 3000.0
            val mutationResponse = DataFactory.sendEncryptedEmailMessageMutationResponse(messageId, createdAtEpochMs)
            mockApiClient.stub {
                onBlocking {
                    sendEncryptedEmailMessageMutation(any())
                } doReturn mutationResponse
            }

            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@example.com", "Sender"),
                    to = listOf(EmailMessageAddressEntity("recipient@example.com", "Recipient")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val request =
                SendEncryptedEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "us-east-1",
                    transientBucket = "transient-bucket",
                    emailMessageHeader = header,
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                    replyingMessageId = null,
                    forwardingMessageId = null,
                )

            val result = instanceUnderTest.sendEncrypted(request)

            result shouldNotBe null
            result.id shouldBe messageId
            result.createdAt shouldBe Date(3000)

            verify(mockApiClient).sendEncryptedEmailMessageMutation(
                check { input ->
                    input.emailAddressId shouldBe emailAddressId
                    input.message.key shouldBe s3Key
                    input.message.region shouldBe "us-east-1"
                    input.message.bucket shouldBe "transient-bucket"
                    input.rfc822Header.from shouldBe "\"Sender\" <sender@example.com>"
                    input.rfc822Header.to.size shouldBe 1
                    input.rfc822Header.subject shouldBe Optional.present("Test Subject")
                },
            )
        }

    @Test
    fun `sendEncryptedEmailMessage() should handle multiple recipients`() =
        runTest {
            val mutationResponse = DataFactory.sendEncryptedEmailMessageMutationResponse("messageId", 1000.0)
            mockApiClient.stub {
                onBlocking {
                    sendEncryptedEmailMessageMutation(any())
                } doReturn mutationResponse
            }

            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@example.com"),
                    to =
                        listOf(
                            EmailMessageAddressEntity("recipient1@example.com", "Recipient 1"),
                            EmailMessageAddressEntity("recipient2@example.com", "Recipient 2"),
                        ),
                    cc = listOf(EmailMessageAddressEntity("cc@example.com")),
                    bcc = listOf(EmailMessageAddressEntity("bcc@example.com")),
                    replyTo = listOf(EmailMessageAddressEntity("replyto@example.com")),
                    subject = "Test Subject",
                )

            val request =
                SendEncryptedEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "us-east-1",
                    transientBucket = "bucket",
                    emailMessageHeader = header,
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                    replyingMessageId = null,
                    forwardingMessageId = null,
                )

            val result = instanceUnderTest.sendEncrypted(request)

            result shouldNotBe null

            verify(mockApiClient).sendEncryptedEmailMessageMutation(
                check { input ->
                    input.rfc822Header.to.size shouldBe 2
                    input.rfc822Header.cc.size shouldBe 1
                    input.rfc822Header.bcc.size shouldBe 1
                    input.rfc822Header.replyTo.size shouldBe 1
                },
            )
        }

    @Test
    fun `sendEncryptedEmailMessage() should handle attachments`() =
        runTest {
            val mutationResponse = DataFactory.sendEncryptedEmailMessageMutationResponse("messageId", 1000.0)
            mockApiClient.stub {
                onBlocking {
                    sendEncryptedEmailMessageMutation(any())
                } doReturn mutationResponse
            }

            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@example.com"),
                    to = listOf(EmailMessageAddressEntity("recipient@example.com")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val attachment =
                EmailAttachmentEntity(
                    fileName = "test.pdf",
                    contentId = "content-id-1",
                    mimeType = "application/pdf",
                    inlineAttachment = false,
                    data = "test data".toByteArray(),
                )

            val request =
                SendEncryptedEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "us-east-1",
                    transientBucket = "bucket",
                    emailMessageHeader = header,
                    attachments = listOf(attachment),
                    inlineAttachments = emptyList(),
                    replyingMessageId = null,
                    forwardingMessageId = null,
                )

            val result = instanceUnderTest.sendEncrypted(request)

            result shouldNotBe null

            verify(mockApiClient).sendEncryptedEmailMessageMutation(
                check { input ->
                    input.rfc822Header.hasAttachments shouldBe Optional.present(true)
                },
            )
        }

    @Test
    fun `sendEncryptedEmailMessage() should handle inline attachments`() =
        runTest {
            val mutationResponse = DataFactory.sendEncryptedEmailMessageMutationResponse("messageId", 1000.0)
            mockApiClient.stub {
                onBlocking {
                    sendEncryptedEmailMessageMutation(any())
                } doReturn mutationResponse
            }

            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@example.com"),
                    to = listOf(EmailMessageAddressEntity("recipient@example.com")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val inlineAttachment =
                EmailAttachmentEntity(
                    fileName = "image.png",
                    contentId = "content-id-2",
                    mimeType = "image/png",
                    inlineAttachment = true,
                    data = "image data".toByteArray(),
                )

            val request =
                SendEncryptedEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "us-east-1",
                    transientBucket = "bucket",
                    emailMessageHeader = header,
                    attachments = emptyList(),
                    inlineAttachments = listOf(inlineAttachment),
                    replyingMessageId = null,
                    forwardingMessageId = null,
                )

            val result = instanceUnderTest.sendEncrypted(request)

            result shouldNotBe null

            verify(mockApiClient).sendEncryptedEmailMessageMutation(
                check { input ->
                    input.rfc822Header.hasAttachments shouldBe Optional.present(true)
                },
            )
        }

    @Test
    fun `sendEncryptedEmailMessage() should handle replyingMessageId`() =
        runTest {
            val mutationResponse = DataFactory.sendEncryptedEmailMessageMutationResponse("messageId", 1000.0)
            mockApiClient.stub {
                onBlocking {
                    sendEncryptedEmailMessageMutation(any())
                } doReturn mutationResponse
            }

            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@example.com"),
                    to = listOf(EmailMessageAddressEntity("recipient@example.com")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Re: Original Subject",
                )

            val request =
                SendEncryptedEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "us-east-1",
                    transientBucket = "bucket",
                    emailMessageHeader = header,
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                    replyingMessageId = "original-message-id-123",
                    forwardingMessageId = null,
                )

            val result = instanceUnderTest.sendEncrypted(request)

            result shouldNotBe null

            verify(mockApiClient).sendEncryptedEmailMessageMutation(
                check { input ->
                    input.rfc822Header.inReplyTo shouldBe Optional.present("original-message-id-123")
                },
            )
        }

    @Test
    fun `sendEncryptedEmailMessage() should handle forwardingMessageId`() =
        runTest {
            val mutationResponse = DataFactory.sendEncryptedEmailMessageMutationResponse("messageId", 1000.0)
            mockApiClient.stub {
                onBlocking {
                    sendEncryptedEmailMessageMutation(any())
                } doReturn mutationResponse
            }

            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@example.com"),
                    to = listOf(EmailMessageAddressEntity("recipient@example.com")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Fwd: Original Subject",
                )

            val request =
                SendEncryptedEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "us-east-1",
                    transientBucket = "bucket",
                    emailMessageHeader = header,
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                    replyingMessageId = null,
                    forwardingMessageId = "forwarded-message-id-456",
                )

            val result = instanceUnderTest.sendEncrypted(request)

            result shouldNotBe null

            verify(mockApiClient).sendEncryptedEmailMessageMutation(
                check { input ->
                    input.rfc822Header.references shouldBe Optional.present(listOf("forwarded-message-id-456"))
                },
            )
        }

    @Test
    fun `sendEncryptedEmailMessage() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            mockApiClient.stub {
                onBlocking {
                    sendEncryptedEmailMessageMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@example.com"),
                    to = listOf(EmailMessageAddressEntity("recipient@example.com")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val request =
                SendEncryptedEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "us-east-1",
                    transientBucket = "bucket",
                    emailMessageHeader = header,
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                    replyingMessageId = null,
                    forwardingMessageId = null,
                )

            shouldThrow<SudoEmailClient.EmailMessageException> {
                instanceUnderTest.sendEncrypted(request)
            }

            verify(mockApiClient).sendEncryptedEmailMessageMutation(any())
        }

    @Test
    fun `sendEncryptedEmailMessage() should throw when result is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    sendEncryptedEmailMessageMutation(any())
                } doReturn GraphQLResponse(null, null)
            }

            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@example.com"),
                    to = listOf(EmailMessageAddressEntity("recipient@example.com")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val request =
                SendEncryptedEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "us-east-1",
                    transientBucket = "bucket",
                    emailMessageHeader = header,
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                    replyingMessageId = null,
                    forwardingMessageId = null,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                instanceUnderTest.sendEncrypted(request)
            }

            verify(mockApiClient).sendEncryptedEmailMessageMutation(any())
        }

    @Test
    fun `sendEncryptedEmailMessage() should use correct S3 parameters`() =
        runTest {
            val mutationResponse = DataFactory.sendEncryptedEmailMessageMutationResponse("messageId", 1000.0)
            mockApiClient.stub {
                onBlocking {
                    sendEncryptedEmailMessageMutation(any())
                } doReturn mutationResponse
            }

            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@example.com"),
                    to = listOf(EmailMessageAddressEntity("recipient@example.com")),
                    cc = emptyList(),
                    bcc = emptyList(),
                    replyTo = emptyList(),
                    subject = "Test Subject",
                )

            val request =
                SendEncryptedEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "eu-central-1",
                    transientBucket = "custom-encrypted-bucket",
                    emailMessageHeader = header,
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                    replyingMessageId = null,
                    forwardingMessageId = null,
                )

            val result = instanceUnderTest.sendEncrypted(request)

            result shouldNotBe null

            verify(mockApiClient).sendEncryptedEmailMessageMutation(
                check { input ->
                    input.message.key shouldBe s3Key
                    input.message.region shouldBe "eu-central-1"
                    input.message.bucket shouldBe "custom-encrypted-bucket"
                },
            )
        }

    @Test
    fun `sendEncryptedEmailMessage() should handle display names with special characters`() =
        runTest {
            val mutationResponse = DataFactory.sendEncryptedEmailMessageMutationResponse("messageId", 1000.0)
            mockApiClient.stub {
                onBlocking {
                    sendEncryptedEmailMessageMutation(any())
                } doReturn mutationResponse
            }

            val header =
                InternetMessageFormatHeaderEntity(
                    from = EmailMessageAddressEntity("sender@example.com", "John \"The Boss\" O'Brien"),
                    to =
                        listOf(
                            EmailMessageAddressEntity("recipient1@example.com", "Jane & Co."),
                            EmailMessageAddressEntity("recipient2@example.com", "Bob's <Business>"),
                        ),
                    cc = listOf(EmailMessageAddressEntity("cc@example.com", "CC User @ Work")),
                    bcc = listOf(EmailMessageAddressEntity("bcc@example.com", "BCC\\User")),
                    replyTo = listOf(EmailMessageAddressEntity("replyto@example.com", "Reply \"Here\"")),
                    subject = "Test Subject",
                )

            val request =
                SendEncryptedEmailMessageRequest(
                    emailAddressId = emailAddressId,
                    s3ObjectKey = s3Key,
                    region = "us-east-1",
                    transientBucket = "bucket",
                    emailMessageHeader = header,
                    attachments = emptyList(),
                    inlineAttachments = emptyList(),
                    replyingMessageId = null,
                    forwardingMessageId = null,
                )

            val result = instanceUnderTest.sendEncrypted(request)

            result shouldNotBe null
            result.id shouldBe "messageId"

            verify(mockApiClient).sendEncryptedEmailMessageMutation(
                check { input ->
                    input.rfc822Header.from shouldBe "\"John \"The Boss\" O'Brien\" <sender@example.com>"
                    input.rfc822Header.to.size shouldBe 2
                    input.rfc822Header.to[0] shouldBe "\"Jane & Co.\" <recipient1@example.com>"
                    input.rfc822Header.to[1] shouldBe "\"Bob's <Business>\" <recipient2@example.com>"
                    input.rfc822Header.cc.size shouldBe 1
                    input.rfc822Header.cc[0] shouldBe "\"CC User @ Work\" <cc@example.com>"
                    input.rfc822Header.bcc.size shouldBe 1
                    input.rfc822Header.bcc[0] shouldBe "\"BCC\\User\" <bcc@example.com>"
                    input.rfc822Header.replyTo.size shouldBe 1
                    input.rfc822Header.replyTo[0] shouldBe "\"Reply \"Here\"\" <replyto@example.com>"
                },
            )
        }

    /** Begin UpdateEmailMessagesTests */

    @Test
    fun `updateEmailMessages() should return success result when all messages updated successfully`() =
        runTest {
            val messageIds = listOf("message-id-1", "message-id-2", "message-id-3")
            val successMessages =
                messageIds.map { id ->
                    UpdateEmailMessagesResult.SuccessMessage(
                        id = id,
                        createdAtEpochMs = 1000.0,
                        updatedAtEpochMs = 2000.0,
                    )
                }
            val mutationResponse =
                DataFactory.updateEmailMessagesMutationResponse(
                    status = UpdateEmailMessagesStatus.SUCCESS,
                    successMessages = successMessages,
                )
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateEmailMessagesRequest(
                    ids = messageIds,
                    values =
                        UpdateEmailMessagesRequest.UpdatableValues(
                            folderId = "new-folder-id",
                            seen = true,
                        ),
                )

            val result = instanceUnderTest.update(request)

            result shouldNotBe null
            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues?.size shouldBe 3
            result.failureValues?.size shouldBe 0

            verify(mockApiClient).updateEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe messageIds
                    input.values.folderId shouldBe Optional.present("new-folder-id")
                    input.values.seen shouldBe Optional.present(true)
                },
            )
        }

    @Test
    fun `updateEmailMessages() should handle partial success with some failed messages`() =
        runTest {
            val successIds = listOf("success-1", "success-2")
            val failedIds = listOf("failed-1")
            val successMessages =
                successIds.map { id ->
                    UpdateEmailMessagesResult.SuccessMessage(
                        id = id,
                        createdAtEpochMs = 1000.0,
                        updatedAtEpochMs = 2000.0,
                    )
                }
            val failedMessages =
                failedIds.map { id ->
                    UpdateEmailMessagesResult.FailedMessage(
                        id = id,
                        errorType = "MessageNotFound",
                    )
                }
            val mutationResponse =
                DataFactory.updateEmailMessagesMutationResponse(
                    status = UpdateEmailMessagesStatus.PARTIAL,
                    successMessages = successMessages,
                    failedMessages = failedMessages,
                )
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateEmailMessagesRequest(
                    ids = successIds + failedIds,
                    values =
                        UpdateEmailMessagesRequest.UpdatableValues(
                            folderId = "new-folder-id",
                        ),
                )

            val result = instanceUnderTest.update(request)

            result shouldNotBe null
            result.status shouldBe BatchOperationStatusEntity.PARTIAL
            result.successValues?.size shouldBe 2
            result.failureValues?.size shouldBe 1

            verify(mockApiClient).updateEmailMessagesMutation(any())
        }

    @Test
    fun `updateEmailMessages() should handle all failed messages`() =
        runTest {
            val failedIds = listOf("failed-1", "failed-2")
            val failedMessages =
                failedIds.map { id ->
                    UpdateEmailMessagesResult.FailedMessage(
                        id = id,
                        errorType = "UnauthorizedAddress",
                    )
                }
            val mutationResponse =
                DataFactory.updateEmailMessagesMutationResponse(
                    status = UpdateEmailMessagesStatus.FAILED,
                    failedMessages = failedMessages,
                )
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateEmailMessagesRequest(
                    ids = failedIds,
                    values =
                        UpdateEmailMessagesRequest.UpdatableValues(
                            seen = false,
                        ),
                )

            val result = instanceUnderTest.update(request)

            result shouldNotBe null
            result.status shouldBe BatchOperationStatusEntity.FAILURE
            result.successValues?.size shouldBe 0
            result.failureValues?.size shouldBe 2

            verify(mockApiClient).updateEmailMessagesMutation(any())
        }

    @Test
    fun `updateEmailMessages() should update only folderId when seen is null`() =
        runTest {
            val messageIds = listOf("message-id-1")
            val successMessages =
                messageIds.map { id ->
                    UpdateEmailMessagesResult.SuccessMessage(
                        id = id,
                        createdAtEpochMs = 1000.0,
                        updatedAtEpochMs = 2000.0,
                    )
                }
            val mutationResponse =
                DataFactory.updateEmailMessagesMutationResponse(
                    successMessages = successMessages,
                )
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateEmailMessagesRequest(
                    ids = messageIds,
                    values =
                        UpdateEmailMessagesRequest.UpdatableValues(
                            folderId = "target-folder-id",
                            seen = null,
                        ),
                )

            val result = instanceUnderTest.update(request)

            result shouldNotBe null

            verify(mockApiClient).updateEmailMessagesMutation(
                check { input ->
                    input.values.folderId shouldBe Optional.present("target-folder-id")
                    input.values.seen shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `updateEmailMessages() should update only seen when folderId is null`() =
        runTest {
            val messageIds = listOf("message-id-1", "message-id-2")
            val successMessages =
                messageIds.map { id ->
                    UpdateEmailMessagesResult.SuccessMessage(
                        id = id,
                        createdAtEpochMs = 1000.0,
                        updatedAtEpochMs = 2000.0,
                    )
                }
            val mutationResponse =
                DataFactory.updateEmailMessagesMutationResponse(
                    successMessages = successMessages,
                )
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateEmailMessagesRequest(
                    ids = messageIds,
                    values =
                        UpdateEmailMessagesRequest.UpdatableValues(
                            folderId = null,
                            seen = true,
                        ),
                )

            val result = instanceUnderTest.update(request)

            result shouldNotBe null

            verify(mockApiClient).updateEmailMessagesMutation(
                check { input ->
                    input.values.folderId shouldBe Optional.absent()
                    input.values.seen shouldBe Optional.present(true)
                },
            )
        }

    @Test
    fun `updateEmailMessages() should handle single message update`() =
        runTest {
            val messageId = "single-message-id"
            val successMessages =
                listOf(
                    UpdateEmailMessagesResult.SuccessMessage(
                        id = messageId,
                        createdAtEpochMs = 1000.0,
                        updatedAtEpochMs = 2000.0,
                    ),
                )
            val mutationResponse =
                DataFactory.updateEmailMessagesMutationResponse(
                    successMessages = successMessages,
                )
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateEmailMessagesRequest(
                    ids = listOf(messageId),
                    values =
                        UpdateEmailMessagesRequest.UpdatableValues(
                            seen = false,
                        ),
                )

            val result = instanceUnderTest.update(request)

            result shouldNotBe null
            result.successValues?.size shouldBe 1
            result.successValues?.get(0)?.id shouldBe messageId

            verify(mockApiClient).updateEmailMessagesMutation(
                check { input ->
                    input.messageIds.size shouldBe 1
                    input.messageIds[0] shouldBe messageId
                },
            )
        }

    @Test
    fun `updateEmailMessages() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                UpdateEmailMessagesRequest(
                    ids = listOf("message-id"),
                    values =
                        UpdateEmailMessagesRequest.UpdatableValues(
                            seen = true,
                        ),
                )

            shouldThrow<SudoEmailClient.EmailMessageException> {
                instanceUnderTest.update(request)
            }

            verify(mockApiClient).updateEmailMessagesMutation(any())
        }

    @Test
    fun `updateEmailMessages() should throw when result is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(any())
                } doReturn GraphQLResponse(null, null)
            }

            val request =
                UpdateEmailMessagesRequest(
                    ids = listOf("message-id"),
                    values =
                        UpdateEmailMessagesRequest.UpdatableValues(
                            folderId = "folder-id",
                        ),
                )

            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                instanceUnderTest.update(request)
            }

            verify(mockApiClient).updateEmailMessagesMutation(any())
        }

    @Test
    fun `updateEmailMessages() should handle updating both folderId and seen`() =
        runTest {
            val messageIds = listOf("msg-1", "msg-2")
            val successMessages =
                messageIds.map { id ->
                    UpdateEmailMessagesResult.SuccessMessage(
                        id = id,
                        createdAtEpochMs = 1000.0,
                        updatedAtEpochMs = 2000.0,
                    )
                }
            val mutationResponse =
                DataFactory.updateEmailMessagesMutationResponse(
                    successMessages = successMessages,
                )
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateEmailMessagesRequest(
                    ids = messageIds,
                    values =
                        UpdateEmailMessagesRequest.UpdatableValues(
                            folderId = "archive-folder",
                            seen = true,
                        ),
                )

            val result = instanceUnderTest.update(request)

            result shouldNotBe null
            result.status shouldBe BatchOperationStatusEntity.SUCCESS

            verify(mockApiClient).updateEmailMessagesMutation(
                check { input ->
                    input.messageIds shouldBe messageIds
                    input.values.folderId shouldBe Optional.present("archive-folder")
                    input.values.seen shouldBe Optional.present(true)
                },
            )
        }

    @Test
    fun `updateEmailMessages() should handle marking messages as unread`() =
        runTest {
            val messageIds = listOf("unread-msg-1", "unread-msg-2")
            val successMessages =
                messageIds.map { id ->
                    UpdateEmailMessagesResult.SuccessMessage(
                        id = id,
                        createdAtEpochMs = 1000.0,
                        updatedAtEpochMs = 2000.0,
                    )
                }
            val mutationResponse =
                DataFactory.updateEmailMessagesMutationResponse(
                    successMessages = successMessages,
                )
            mockApiClient.stub {
                onBlocking {
                    updateEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateEmailMessagesRequest(
                    ids = messageIds,
                    values =
                        UpdateEmailMessagesRequest.UpdatableValues(
                            seen = false,
                        ),
                )

            val result = instanceUnderTest.update(request)

            result shouldNotBe null

            verify(mockApiClient).updateEmailMessagesMutation(
                check { input ->
                    input.values.seen shouldBe Optional.present(false)
                },
            )
        }

    /** Begin DeleteEmailMessagesTests */

    @Test
    fun `deleteEmailMessages() should return success result when all messages deleted successfully`() =
        runTest {
            val messageIds = setOf("message-id-1", "message-id-2", "message-id-3")
            val mutationResponse = DataFactory.deleteEmailMessagesMutationResponse(emptyList())
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val result = instanceUnderTest.delete(messageIds)

            result shouldNotBe null
            result.successIds.size shouldBe 3
            result.failureIds.size shouldBe 0
            result.successIds shouldBe messageIds.toList()

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds.toSet() shouldBe messageIds
                },
            )
        }

    @Test
    fun `deleteEmailMessages() should handle partial failure with some messages not deleted`() =
        runTest {
            val allIds = setOf("success-1", "success-2", "failed-1", "failed-2")
            val failedIds = listOf("failed-1", "failed-2")
            val mutationResponse = DataFactory.deleteEmailMessagesMutationResponse(failedIds)
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val result = instanceUnderTest.delete(allIds)

            result shouldNotBe null
            result.successIds.size shouldBe 2
            result.failureIds.size shouldBe 2
            result.successIds.toSet() shouldBe setOf("success-1", "success-2")
            result.failureIds shouldBe failedIds

            verify(mockApiClient).deleteEmailMessagesMutation(any())
        }

    @Test
    fun `deleteEmailMessages() should handle all messages failed to delete`() =
        runTest {
            val messageIds = setOf("failed-1", "failed-2", "failed-3")
            val mutationResponse = DataFactory.deleteEmailMessagesMutationResponse(messageIds.toList())
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val result = instanceUnderTest.delete(messageIds)

            result shouldNotBe null
            result.successIds.size shouldBe 0
            result.failureIds.size shouldBe 3
            result.failureIds.toSet() shouldBe messageIds

            verify(mockApiClient).deleteEmailMessagesMutation(any())
        }

    @Test
    fun `deleteEmailMessages() should handle single message deletion`() =
        runTest {
            val messageId = setOf("single-message-id")
            val mutationResponse = DataFactory.deleteEmailMessagesMutationResponse(emptyList())
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val result = instanceUnderTest.delete(messageId)

            result shouldNotBe null
            result.successIds.size shouldBe 1
            result.successIds[0] shouldBe "single-message-id"
            result.failureIds.size shouldBe 0

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds.size shouldBe 1
                    input.messageIds[0] shouldBe "single-message-id"
                },
            )
        }

    @Test
    fun `deleteEmailMessages() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val messageIds = setOf("message-id-1")

            shouldThrow<SudoEmailClient.EmailMessageException> {
                instanceUnderTest.delete(messageIds)
            }

            verify(mockApiClient).deleteEmailMessagesMutation(any())
        }

    @Test
    fun `deleteEmailMessages() should throw when result is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(any())
                } doReturn GraphQLResponse(null, null)
            }

            val messageIds = setOf("message-id-1", "message-id-2")

            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                instanceUnderTest.delete(messageIds)
            }

            verify(mockApiClient).deleteEmailMessagesMutation(any())
        }

    @Test
    fun `deleteEmailMessages() should handle empty set of ids`() =
        runTest {
            val emptyIds = emptySet<String>()
            val mutationResponse = DataFactory.deleteEmailMessagesMutationResponse(emptyList())
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val result = instanceUnderTest.delete(emptyIds)

            result shouldNotBe null
            result.successIds.size shouldBe 0
            result.failureIds.size shouldBe 0

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds.size shouldBe 0
                },
            )
        }

    @Test
    fun `deleteEmailMessages() should convert Set to List for mutation input`() =
        runTest {
            val messageIds = setOf("id-3", "id-1", "id-2") // Set has no guaranteed order
            val mutationResponse = DataFactory.deleteEmailMessagesMutationResponse(emptyList())
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val result = instanceUnderTest.delete(messageIds)

            result shouldNotBe null
            result.successIds.size shouldBe 3

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds.toSet() shouldBe messageIds
                },
            )
        }

    @Test
    fun `deleteEmailMessages() should handle large batch of messages`() =
        runTest {
            val largeSet = (1..100).map { "message-id-$it" }.toSet()
            val mutationResponse = DataFactory.deleteEmailMessagesMutationResponse(emptyList())
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val result = instanceUnderTest.delete(largeSet)

            result shouldNotBe null
            result.successIds.size shouldBe 100
            result.failureIds.size shouldBe 0

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    input.messageIds.size shouldBe 100
                },
            )
        }

    @Test
    fun `deleteEmailMessages() should correctly partition success and failure ids`() =
        runTest {
            val allIds = setOf("msg-1", "msg-2", "msg-3", "msg-4", "msg-5")
            val failedIds = listOf("msg-2", "msg-4")
            val mutationResponse = DataFactory.deleteEmailMessagesMutationResponse(failedIds)
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val result = instanceUnderTest.delete(allIds)

            result shouldNotBe null
            result.successIds.size shouldBe 3
            result.failureIds.size shouldBe 2
            result.successIds.toSet() shouldBe setOf("msg-1", "msg-3", "msg-5")
            result.failureIds shouldBe failedIds

            verify(mockApiClient).deleteEmailMessagesMutation(any())
        }

    @Test
    fun `deleteEmailMessages() should handle failure with special error type`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Authorization failed",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "UnauthorizedAddress"),
                )
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val messageIds = setOf("message-id-1", "message-id-2")

            shouldThrow<SudoEmailClient.EmailMessageException> {
                instanceUnderTest.delete(messageIds)
            }

            verify(mockApiClient).deleteEmailMessagesMutation(any())
        }

    @Test
    fun `deleteEmailMessages() should preserve order independence of Set input`() =
        runTest {
            val messageIds = setOf("z-id", "a-id", "m-id")
            val mutationResponse = DataFactory.deleteEmailMessagesMutationResponse(emptyList())
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val result = instanceUnderTest.delete(messageIds)

            result shouldNotBe null
            result.successIds.toSet() shouldBe messageIds
            result.failureIds.size shouldBe 0

            verify(mockApiClient).deleteEmailMessagesMutation(any())
        }

    @Test
    fun `deleteEmailMessages() should handle removing duplicates`() =
        runTest {
            // Create a set from a list with duplicates - Set will deduplicate automatically
            val messageIds = setOf("id-1", "id-2", "id-3", "id-1", "id-2")
            val mutationResponse = DataFactory.deleteEmailMessagesMutationResponse(emptyList())
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesMutation(any())
                } doReturn mutationResponse
            }

            val result = instanceUnderTest.delete(messageIds)

            result shouldNotBe null
            // Set should only have 3 unique ids
            messageIds.size shouldBe 3
            result.successIds.size shouldBe 3
            result.successIds.toSet() shouldBe setOf("id-1", "id-2", "id-3")
            result.failureIds.size shouldBe 0

            verify(mockApiClient).deleteEmailMessagesMutation(
                check { input ->
                    // Mutation should receive 3 unique ids
                    input.messageIds.size shouldBe 3
                    input.messageIds.toSet() shouldBe setOf("id-1", "id-2", "id-3")
                },
            )
        }

    /** Begin GetEmailMessageTests */

    @Test
    fun `getEmailMessage() should return email message when found`() =
        runTest {
            val messageId = "message-id-123"
            val sealedData = mockSeal(DataFactory.unsealedHeaderDetailsString)
            val queryResponse = DataFactory.getEmailMessageQueryResponse(sealedData)
            mockApiClient.stub {
                onBlocking {
                    getEmailMessageQuery(any())
                } doReturn queryResponse
            }

            val request =
                GetEmailMessageRequest(
                    id = messageId,
                )

            val result = instanceUnderTest.get(request)

            result shouldNotBe null
            result?.id shouldBe "id"

            verify(mockApiClient).getEmailMessageQuery(messageId)
        }

    @Test
    fun `getEmailMessage() should return null when message not found`() =
        runTest {
            val queryResponse =
                GraphQLResponse<GetEmailMessageQuery.Data>(
                    null,
                    null,
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailMessageQuery(any())
                } doReturn queryResponse
            }

            val request =
                GetEmailMessageRequest(
                    id = "non-existent-id",
                )

            val result = instanceUnderTest.get(request)

            result shouldBe null

            verify(mockApiClient).getEmailMessageQuery("non-existent-id")
        }

    @Test
    fun `getEmailMessage() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailMessageQuery(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                GetEmailMessageRequest(
                    id = "message-id",
                )

            shouldThrow<SudoEmailClient.EmailMessageException> {
                instanceUnderTest.get(request)
            }

            verify(mockApiClient).getEmailMessageQuery("message-id")
        }

    @Test
    fun `getEmailMessage() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getEmailMessageQuery(any())
                } doThrow SudoEmailClient.EmailMessageException.AuthenticationException("Mock")
            }

            val request =
                GetEmailMessageRequest(
                    id = "message-id",
                )

            shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
                instanceUnderTest.get(request)
            }

            verify(mockApiClient).getEmailMessageQuery("message-id")
        }

    @Test
    fun `getEmailMessage() should handle GraphQL error with specific error type`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Message not found",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "EmailMessageNotFound"),
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailMessageQuery(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                GetEmailMessageRequest(
                    id = "missing-message-id",
                )

            shouldThrow<SudoEmailClient.EmailMessageException> {
                instanceUnderTest.get(request)
            }

            verify(mockApiClient).getEmailMessageQuery("missing-message-id")
        }

    @Test
    fun `getEmailMessage() should return null when data is present but getEmailMessage is null`() =
        runTest {
            val queryResponse =
                GraphQLResponse(
                    GetEmailMessageQuery.Data(
                        getEmailMessage = null,
                    ),
                    null,
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailMessageQuery(any())
                } doReturn queryResponse
            }

            val request =
                GetEmailMessageRequest(
                    id = "message-id",
                )

            val result = instanceUnderTest.get(request)

            result shouldBe null

            verify(mockApiClient).getEmailMessageQuery("message-id")
        }

    /** Begin ListEmailMessagesTests */

    @Test
    fun `listEmailMessages() should return results when successful`() =
        runTest {
            val message1 = DataFactory.getSealedEmailMessage(id = "message-id-1", emailAddressId = emailAddressId)
            val message2 = DataFactory.getSealedEmailMessage(id = "message-id-2", emailAddressId = emailAddressId)
            val queryResponse =
                DataFactory.listEmailMessagesQueryResponse(
                    items = listOf(message1, message2),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesRequest(
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.list(request)

            result shouldNotBe null
            result.items.size shouldBe 2
            result.items[0].id shouldBe "message-id-1"
            result.items[1].id shouldBe "message-id-2"
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailMessagesQuery(
                check { input ->
                    input.limit shouldBe Optional.present(10)
                    input.nextToken shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.present(SortOrder.DESC)
                    input.includeDeletedMessages shouldBe Optional.present(false)
                },
            )
        }

    @Test
    fun `listEmailMessages() should return results with nextToken when pagination is needed`() =
        runTest {
            val message1 = DataFactory.getSealedEmailMessage(id = "message-id-1", emailAddressId = emailAddressId)
            val message2 = DataFactory.getSealedEmailMessage(id = "message-id-2", emailAddressId = emailAddressId)
            val nextToken = "next-token-value"
            val queryResponse =
                DataFactory.listEmailMessagesQueryResponse(
                    items = listOf(message1, message2),
                    nextToken = nextToken,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesRequest(
                    dateRange = null,
                    limit = 2,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.list(request)

            result shouldNotBe null
            result.items.size shouldBe 2
            result.nextToken shouldBe nextToken

            verify(mockApiClient).listEmailMessagesQuery(any())
        }

    @Test
    fun `listEmailMessages() should handle pagination with nextToken`() =
        runTest {
            val message3 = DataFactory.getSealedEmailMessage(id = "message-id-3", emailAddressId = emailAddressId)
            val queryResponse =
                DataFactory.listEmailMessagesQueryResponse(
                    items = listOf(message3),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesRequest(
                    dateRange = null,
                    limit = 10,
                    nextToken = "previous-token",
                    sortOrder = SortOrderEntity.ASC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.list(request)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.items[0].id shouldBe "message-id-3"
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailMessagesQuery(
                check { input ->
                    input.nextToken shouldBe Optional.present("previous-token")
                    input.sortOrder shouldBe Optional.present(SortOrder.ASC)
                },
            )
        }

    @Test
    fun `listEmailMessages() should return empty list when no messages exist`() =
        runTest {
            val queryResponse =
                DataFactory.listEmailMessagesQueryResponse(
                    items = emptyList(),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesRequest(
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.list(request)

            result shouldNotBe null
            result.items.size shouldBe 0
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailMessagesQuery(any())
        }

    @Test
    fun `listEmailMessages() should handle date range filter`() =
        runTest {
            val message = DataFactory.getSealedEmailMessage(id = "message-id-1", emailAddressId = emailAddressId)
            val queryResponse =
                DataFactory.listEmailMessagesQueryResponse(
                    items = listOf(message),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesQuery(any())
                } doReturn queryResponse
            }

            val dateRange =
                EmailMessageDateRangeEntity(
                    sortDate =
                        DateRangeEntity(
                            startDate = Date(1000),
                            endDate = Date(2000),
                        ),
                )

            val request =
                ListEmailMessagesRequest(
                    dateRange = dateRange,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.list(request)

            result shouldNotBe null
            result.items.size shouldBe 1

            verify(mockApiClient).listEmailMessagesQuery(
                check { input ->
                    input.specifiedDateRange shouldNotBe Optional.absent()
                },
            )
        }

    @Test
    fun `listEmailMessages() should include deleted messages when specified`() =
        runTest {
            val message1 = DataFactory.getSealedEmailMessage(id = "message-id-1", emailAddressId = emailAddressId)
            val message2 =
                DataFactory.getSealedEmailMessage(
                    id = "message-id-2",
                    emailAddressId = emailAddressId,
                    state = EmailMessageState.DELETED,
                )
            val queryResponse =
                DataFactory.listEmailMessagesQueryResponse(
                    items = listOf(message1, message2),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesRequest(
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = true,
                )

            val result = instanceUnderTest.list(request)

            result shouldNotBe null
            result.items.size shouldBe 2

            verify(mockApiClient).listEmailMessagesQuery(
                check { input ->
                    input.includeDeletedMessages shouldBe Optional.present(true)
                },
            )
        }

    @Test
    fun `listEmailMessages() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesQuery(any())
                } doThrow SudoEmailClient.EmailMessageException.AuthenticationException("Mock")
            }

            val request =
                ListEmailMessagesRequest(
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
                instanceUnderTest.list(request)
            }

            verify(mockApiClient).listEmailMessagesQuery(any())
        }

    @Test
    fun `listEmailMessages() should throw exception when GraphQL returns errors`() =
        runTest {
            val error =
                GraphQLResponse.Error(
                    "Mock GraphQL Error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            val queryResponse =
                GraphQLResponse<ListEmailMessagesQuery.Data>(
                    null,
                    listOf(error),
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesRequest(
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            shouldThrow<SudoEmailClient.EmailMessageException> {
                instanceUnderTest.list(request)
            }

            verify(mockApiClient).listEmailMessagesQuery(any())
        }

    @Test
    fun `listEmailMessages() should handle null limit parameter`() =
        runTest {
            val message = DataFactory.getSealedEmailMessage(id = "message-id-1", emailAddressId = emailAddressId)
            val queryResponse =
                DataFactory.listEmailMessagesQueryResponse(
                    items = listOf(message),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesRequest(
                    dateRange = null,
                    limit = null,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.list(request)

            result shouldNotBe null
            result.items.size shouldBe 1

            verify(mockApiClient).listEmailMessagesQuery(
                check { input ->
                    input.limit shouldBe Optional.absent()
                },
            )
        }

    /** Begin ListEmailMessagesForEmailAddressIdTests */

    @Test
    fun `listEmailMessagesForEmailAddressId() should return results when successful`() =
        runTest {
            val emailAddressId = "test-email-address-id"
            val message1 = DataFactory.getSealedEmailMessage(id = "message-id-1", emailAddressId = emailAddressId)
            val message2 = DataFactory.getSealedEmailMessage(id = "message-id-2", emailAddressId = emailAddressId)
            val queryResponse =
                DataFactory.listEmailMessagesForEmailAddressIdQueryResponse(
                    items = listOf(message1, message2),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesForEmailAddressIdRequest(
                    emailAddressId = emailAddressId,
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.listForEmailAddressId(request)

            result shouldNotBe null
            result.items.size shouldBe 2
            result.items[0].id shouldBe "message-id-1"
            result.items[1].id shouldBe "message-id-2"
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe emailAddressId
                    input.limit shouldBe Optional.present(10)
                    input.nextToken shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.present(SortOrder.DESC)
                    input.includeDeletedMessages shouldBe Optional.present(false)
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return results with nextToken when pagination is needed`() =
        runTest {
            val emailAddressId = "test-email-address-id"
            val message1 = DataFactory.getSealedEmailMessage(id = "message-id-1", emailAddressId = emailAddressId)
            val nextToken = "next-token-value"
            val queryResponse =
                DataFactory.listEmailMessagesForEmailAddressIdQueryResponse(
                    items = listOf(message1),
                    nextToken = nextToken,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesForEmailAddressIdRequest(
                    emailAddressId = emailAddressId,
                    dateRange = null,
                    limit = 1,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.listForEmailAddressId(request)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.nextToken shouldBe nextToken

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(any())
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should handle pagination with nextToken`() =
        runTest {
            val emailAddressId = "test-email-address-id"
            val message3 = DataFactory.getSealedEmailMessage(id = "message-id-3", emailAddressId = emailAddressId)
            val queryResponse =
                DataFactory.listEmailMessagesForEmailAddressIdQueryResponse(
                    items = listOf(message3),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesForEmailAddressIdRequest(
                    emailAddressId = emailAddressId,
                    dateRange = null,
                    limit = 10,
                    nextToken = "previous-token",
                    sortOrder = SortOrderEntity.ASC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.listForEmailAddressId(request)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.items[0].id shouldBe "message-id-3"
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe emailAddressId
                    input.nextToken shouldBe Optional.present("previous-token")
                    input.sortOrder shouldBe Optional.present(SortOrder.ASC)
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should return empty list when no messages exist`() =
        runTest {
            val emailAddressId = "test-email-address-id"
            val queryResponse =
                DataFactory.listEmailMessagesForEmailAddressIdQueryResponse(
                    items = emptyList(),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesForEmailAddressIdRequest(
                    emailAddressId = emailAddressId,
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.listForEmailAddressId(request)

            result shouldNotBe null
            result.items.size shouldBe 0
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(any())
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should handle date range filter`() =
        runTest {
            val emailAddressId = "test-email-address-id"
            val message = DataFactory.getSealedEmailMessage(id = "message-id-1", emailAddressId = emailAddressId)
            val queryResponse =
                DataFactory.listEmailMessagesForEmailAddressIdQueryResponse(
                    items = listOf(message),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(any())
                } doReturn queryResponse
            }

            val dateRange =
                EmailMessageDateRangeEntity(
                    sortDate =
                        DateRangeEntity(
                            startDate = Date(1000),
                            endDate = Date(2000),
                        ),
                )

            val request =
                ListEmailMessagesForEmailAddressIdRequest(
                    emailAddressId = emailAddressId,
                    dateRange = dateRange,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.listForEmailAddressId(request)

            result shouldNotBe null
            result.items.size shouldBe 1

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe emailAddressId
                    input.specifiedDateRange shouldNotBe Optional.absent()
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should include deleted messages when specified`() =
        runTest {
            val emailAddressId = "test-email-address-id"
            val message1 = DataFactory.getSealedEmailMessage(id = "message-id-1", emailAddressId = emailAddressId)
            val message2 =
                DataFactory.getSealedEmailMessage(
                    id = "message-id-2",
                    emailAddressId = emailAddressId,
                    state = EmailMessageState.DELETED,
                )
            val queryResponse =
                DataFactory.listEmailMessagesForEmailAddressIdQueryResponse(
                    items = listOf(message1, message2),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesForEmailAddressIdRequest(
                    emailAddressId = emailAddressId,
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = true,
                )

            val result = instanceUnderTest.listForEmailAddressId(request)

            result shouldNotBe null
            result.items.size shouldBe 2

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe emailAddressId
                    input.includeDeletedMessages shouldBe Optional.present(true)
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            val emailAddressId = "test-email-address-id"
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(any())
                } doThrow SudoEmailClient.EmailMessageException.AuthenticationException("Mock")
            }

            val request =
                ListEmailMessagesForEmailAddressIdRequest(
                    emailAddressId = emailAddressId,
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
                instanceUnderTest.listForEmailAddressId(request)
            }

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(any())
        }

    @Test
    fun `listEmailMessagesForEmailAddressId() should throw exception when GraphQL returns errors`() =
        runTest {
            val emailAddressId = "test-email-address-id"
            val error =
                GraphQLResponse.Error(
                    "Mock GraphQL Error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            val queryResponse =
                GraphQLResponse<ListEmailMessagesForEmailAddressIdQuery.Data>(
                    null,
                    listOf(error),
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailAddressIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesForEmailAddressIdRequest(
                    emailAddressId = emailAddressId,
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            shouldThrow<SudoEmailClient.EmailMessageException> {
                instanceUnderTest.listForEmailAddressId(request)
            }

            verify(mockApiClient).listEmailMessagesForEmailAddressIdQuery(any())
        }

    /** Begin ListEmailMessagesForEmailFolderIdTests */

    @Test
    fun `listEmailMessagesForEmailFolderId() should return results when successful`() =
        runTest {
            val emailFolderId = "test-email-folder-id"
            val message1 = DataFactory.getSealedEmailMessage(id = "message-id-1", folderId = emailFolderId)
            val message2 = DataFactory.getSealedEmailMessage(id = "message-id-2", folderId = emailFolderId)
            val queryResponse =
                DataFactory.listEmailMessagesForEmailFolderIdQueryResponse(
                    items = listOf(message1, message2),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailFolderIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesForEmailFolderIdRequest(
                    emailFolderId = emailFolderId,
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.listForEmailFolderId(request)

            result shouldNotBe null
            result.items.size shouldBe 2
            result.items[0].id shouldBe "message-id-1"
            result.items[1].id shouldBe "message-id-2"
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailMessagesForEmailFolderIdQuery(
                check { input ->
                    input.folderId shouldBe emailFolderId
                    input.limit shouldBe Optional.present(10)
                    input.nextToken shouldBe Optional.absent()
                    input.sortOrder shouldBe Optional.present(SortOrder.DESC)
                    input.includeDeletedMessages shouldBe Optional.present(false)
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should return results with nextToken when pagination is needed`() =
        runTest {
            val emailFolderId = "test-email-folder-id"
            val message1 = DataFactory.getSealedEmailMessage(id = "message-id-1", folderId = emailFolderId)
            val nextToken = "next-token-value"
            val queryResponse =
                DataFactory.listEmailMessagesForEmailFolderIdQueryResponse(
                    items = listOf(message1),
                    nextToken = nextToken,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailFolderIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesForEmailFolderIdRequest(
                    emailFolderId = emailFolderId,
                    dateRange = null,
                    limit = 1,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.listForEmailFolderId(request)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.nextToken shouldBe nextToken

            verify(mockApiClient).listEmailMessagesForEmailFolderIdQuery(any())
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should handle pagination with nextToken`() =
        runTest {
            val emailFolderId = "test-email-folder-id"
            val message3 = DataFactory.getSealedEmailMessage(id = "message-id-3", folderId = emailFolderId)
            val queryResponse =
                DataFactory.listEmailMessagesForEmailFolderIdQueryResponse(
                    items = listOf(message3),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailFolderIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesForEmailFolderIdRequest(
                    emailFolderId = emailFolderId,
                    dateRange = null,
                    limit = 10,
                    nextToken = "previous-token",
                    sortOrder = SortOrderEntity.ASC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.listForEmailFolderId(request)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.items[0].id shouldBe "message-id-3"
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailMessagesForEmailFolderIdQuery(
                check { input ->
                    input.folderId shouldBe emailFolderId
                    input.nextToken shouldBe Optional.present("previous-token")
                    input.sortOrder shouldBe Optional.present(SortOrder.ASC)
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should return empty list when no messages exist`() =
        runTest {
            val emailFolderId = "test-email-folder-id"
            val queryResponse =
                DataFactory.listEmailMessagesForEmailFolderIdQueryResponse(
                    items = emptyList(),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailFolderIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesForEmailFolderIdRequest(
                    emailFolderId = emailFolderId,
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.listForEmailFolderId(request)

            result shouldNotBe null
            result.items.size shouldBe 0
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailMessagesForEmailFolderIdQuery(any())
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should handle date range filter`() =
        runTest {
            val emailFolderId = "test-email-folder-id"
            val message = DataFactory.getSealedEmailMessage(id = "message-id-1", folderId = emailFolderId)
            val queryResponse =
                DataFactory.listEmailMessagesForEmailFolderIdQueryResponse(
                    items = listOf(message),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailFolderIdQuery(any())
                } doReturn queryResponse
            }

            val dateRange =
                EmailMessageDateRangeEntity(
                    sortDate =
                        DateRangeEntity(
                            startDate = Date(1000),
                            endDate = Date(2000),
                        ),
                )

            val request =
                ListEmailMessagesForEmailFolderIdRequest(
                    emailFolderId = emailFolderId,
                    dateRange = dateRange,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            val result = instanceUnderTest.listForEmailFolderId(request)

            result shouldNotBe null
            result.items.size shouldBe 1

            verify(mockApiClient).listEmailMessagesForEmailFolderIdQuery(
                check { input ->
                    input.folderId shouldBe emailFolderId
                    input.specifiedDateRange shouldNotBe Optional.absent()
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should include deleted messages when specified`() =
        runTest {
            val emailFolderId = "test-email-folder-id"
            val message1 = DataFactory.getSealedEmailMessage(id = "message-id-1", folderId = emailFolderId)
            val message2 =
                DataFactory.getSealedEmailMessage(
                    id = "message-id-2",
                    folderId = emailFolderId,
                    state = EmailMessageState.DELETED,
                )
            val queryResponse =
                DataFactory.listEmailMessagesForEmailFolderIdQueryResponse(
                    items = listOf(message1, message2),
                    nextToken = null,
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailFolderIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesForEmailFolderIdRequest(
                    emailFolderId = emailFolderId,
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = true,
                )

            val result = instanceUnderTest.listForEmailFolderId(request)

            result shouldNotBe null
            result.items.size shouldBe 2

            verify(mockApiClient).listEmailMessagesForEmailFolderIdQuery(
                check { input ->
                    input.folderId shouldBe emailFolderId
                    input.includeDeletedMessages shouldBe Optional.present(true)
                },
            )
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            val emailFolderId = "test-email-folder-id"
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailFolderIdQuery(any())
                } doThrow SudoEmailClient.EmailMessageException.AuthenticationException("Mock")
            }

            val request =
                ListEmailMessagesForEmailFolderIdRequest(
                    emailFolderId = emailFolderId,
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
                instanceUnderTest.listForEmailFolderId(request)
            }

            verify(mockApiClient).listEmailMessagesForEmailFolderIdQuery(any())
        }

    @Test
    fun `listEmailMessagesForEmailFolderId() should throw exception when GraphQL returns errors`() =
        runTest {
            val emailFolderId = "test-email-folder-id"
            val error =
                GraphQLResponse.Error(
                    "Mock GraphQL Error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            val queryResponse =
                GraphQLResponse<ListEmailMessagesForEmailFolderIdQuery.Data>(
                    null,
                    listOf(error),
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailMessagesForEmailFolderIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailMessagesForEmailFolderIdRequest(
                    emailFolderId = emailFolderId,
                    dateRange = null,
                    limit = 10,
                    nextToken = null,
                    sortOrder = SortOrderEntity.DESC,
                    includeDeletedMessages = false,
                )

            shouldThrow<SudoEmailClient.EmailMessageException> {
                instanceUnderTest.listForEmailFolderId(request)
            }

            verify(mockApiClient).listEmailMessagesForEmailFolderIdQuery(any())
        }

    /** Begin DeleteMessagesByFolderIdTests */

    @Test
    fun `deleteMessagesByFolderId() should return folder ID when deletion is successful`() =
        runTest {
            val folderId = "folder-id-123"
            val emailAddressId = "email-address-id-456"
            val mutationResponse = DataFactory.deleteEmailMessagesForFolderIdMutationResponse(folderId)
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesByFolderIdMutation(any())
                } doReturn mutationResponse
            }

            val request =
                DeleteMessageForFolderIdRequest(
                    emailFolderId = folderId,
                    emailAddressId = emailAddressId,
                    hardDelete = false,
                )

            val result = instanceUnderTest.deleteForFolderId(request)

            result shouldBe folderId

            verify(mockApiClient).deleteEmailMessagesByFolderIdMutation(
                check { input ->
                    input.folderId shouldBe folderId
                    input.emailAddressId shouldBe emailAddressId
                    input.hardDelete shouldBe Optional.presentIfNotNull(false)
                },
            )
        }

    @Test
    fun `deleteMessagesByFolderId() should pass hardDelete as true when specified`() =
        runTest {
            val folderId = "folder-id-123"
            val emailAddressId = "email-address-id-456"
            val mutationResponse = DataFactory.deleteEmailMessagesForFolderIdMutationResponse(folderId)
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesByFolderIdMutation(any())
                } doReturn mutationResponse
            }

            val request =
                DeleteMessageForFolderIdRequest(
                    emailFolderId = folderId,
                    emailAddressId = emailAddressId,
                    hardDelete = true,
                )

            val result = instanceUnderTest.deleteForFolderId(request)

            result shouldBe folderId

            verify(mockApiClient).deleteEmailMessagesByFolderIdMutation(
                check { input ->
                    input.folderId shouldBe folderId
                    input.emailAddressId shouldBe emailAddressId
                    input.hardDelete shouldBe Optional.presentIfNotNull(true)
                },
            )
        }

    @Test
    fun `deleteMessagesByFolderId() should pass hardDelete as null when not specified`() =
        runTest {
            val folderId = "folder-id-123"
            val emailAddressId = "email-address-id-456"
            val mutationResponse = DataFactory.deleteEmailMessagesForFolderIdMutationResponse(folderId)
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesByFolderIdMutation(any())
                } doReturn mutationResponse
            }

            val request =
                DeleteMessageForFolderIdRequest(
                    emailFolderId = folderId,
                    emailAddressId = emailAddressId,
                    hardDelete = null,
                )

            val result = instanceUnderTest.deleteForFolderId(request)

            result shouldBe folderId

            verify(mockApiClient).deleteEmailMessagesByFolderIdMutation(
                check { input ->
                    input.folderId shouldBe folderId
                    input.emailAddressId shouldBe emailAddressId
                    input.hardDelete shouldBe Optional.presentIfNotNull(null)
                },
            )
        }

    @Test
    fun `deleteMessagesByFolderId() should handle different folder IDs`() =
        runTest {
            val folderId = "custom-folder-id-xyz"
            val emailAddressId = "email-address-id-789"
            val mutationResponse = DataFactory.deleteEmailMessagesForFolderIdMutationResponse(folderId)
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesByFolderIdMutation(any())
                } doReturn mutationResponse
            }

            val request =
                DeleteMessageForFolderIdRequest(
                    emailFolderId = folderId,
                    emailAddressId = emailAddressId,
                    hardDelete = false,
                )

            val result = instanceUnderTest.deleteForFolderId(request)

            result shouldBe folderId

            verify(mockApiClient).deleteEmailMessagesByFolderIdMutation(any())
        }

    @Test
    fun `deleteMessagesByFolderId() should throw when response has errors`() =
        runTest {
            val folderId = "folder-id-123"
            val emailAddressId = "email-address-id-456"
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "FolderNotFoundError"),
                )
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesByFolderIdMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                DeleteMessageForFolderIdRequest(
                    emailFolderId = folderId,
                    emailAddressId = emailAddressId,
                    hardDelete = false,
                )

            shouldThrow<SudoEmailClient.EmailFolderException> {
                instanceUnderTest.deleteForFolderId(request)
            }

            verify(mockApiClient).deleteEmailMessagesByFolderIdMutation(any())
        }

    @Test
    fun `deleteMessagesByFolderId() should correctly pass all parameters to mutation`() =
        runTest {
            val folderId = "folder-id-123"
            val emailAddressId = "email-address-id-456"
            val hardDelete = true
            val mutationResponse = DataFactory.deleteEmailMessagesForFolderIdMutationResponse(folderId)
            mockApiClient.stub {
                onBlocking {
                    deleteEmailMessagesByFolderIdMutation(any())
                } doReturn mutationResponse
            }

            val request =
                DeleteMessageForFolderIdRequest(
                    emailFolderId = folderId,
                    emailAddressId = emailAddressId,
                    hardDelete = hardDelete,
                )

            instanceUnderTest.deleteForFolderId(request)

            verify(mockApiClient).deleteEmailMessagesByFolderIdMutation(
                check { input ->
                    input.folderId shouldBe folderId
                    input.emailAddressId shouldBe emailAddressId
                    input.hardDelete shouldBe Optional.presentIfNotNull(hardDelete)
                },
            )
        }
}
