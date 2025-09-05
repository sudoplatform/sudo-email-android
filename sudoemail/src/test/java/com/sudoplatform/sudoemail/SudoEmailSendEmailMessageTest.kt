/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amplifyframework.api.graphql.GraphQLResponse
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.graphql.type.Rfc822HeaderInput
import com.sudoplatform.sudoemail.graphql.type.S3EmailObjectInput
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.types.SecurePackage
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.InternetMessageFormatHeader
import com.sudoplatform.sudoemail.types.inputs.SendEmailMessageInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [SudoEmailClient.sendEmailMessage]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailSendEmailMessageTest : BaseTests() {
    private val emailAddressId = "emailAddressId"
    private val emailAttachment =
        EmailAttachment(
            "fileName.jpg",
            "contentId",
            "mimeType",
            false,
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
    private val domains = listOf("foo.com", "bear.com")
    private val rfc822HeaderInput =
        Rfc822HeaderInput(
            from = "from@bear.com",
            to = listOf("to@bear.com"),
            cc = listOf("cc@bear.com"),
            bcc = listOf("bcc@bear.com"),
            replyTo = listOf("replyTo@bear.com"),
            subject = Optional.Present("email message subject"),
            dateEpochMs = Optional.absent(),
            hasAttachments = Optional.Present(false),
        )

    private val input by before {
        SendEmailMessageInput(
            emailAddressId,
            headers,
            "email message body",
            listOf(emailAttachment),
            listOf(emailAttachment),
        )
    }

    private val sendMutationResponse by before {
        DataFactory.sendEmailMessageMutationResponse(
            "sendEmailMessage",
            1.0,
        )
    }

    private val sendEncryptedMutationResponse by before {
        DataFactory.sendEncryptedEmailMessageMutationResponse(
            "sendEmailMessage",
            1.0,
        )
    }

    private val lookupEmailAddressesPublicInfoQueryResponse by before {
        DataFactory.lookupEmailAddressPublicInfoQueryResponse(
            listOf(
                DataFactory.getEmailAddressPublicInfo("to@bear.com"),
                DataFactory.getEmailAddressPublicInfo("cc@bear.com"),
                DataFactory.getEmailAddressPublicInfo("bcc@bear.com"),
            ),
        )
    }

    private val getConfiguredDomainsQueryResponse by before {
        DataFactory.getConfiguredEmailDomainsQueryResponse(domains)
    }

    private val getEmailConfigQueryResponse by before {
        DataFactory.getEmailConfigQueryResponse()
    }

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "subject"
            on { getRefreshToken() } doReturn "refreshToken"
        }
    }

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                sendEmailMessageMutation(
                    any(),
                )
            } doAnswer {
                sendMutationResponse
            }
            onBlocking {
                sendEncryptedEmailMessageMutation(
                    any(),
                )
            } doAnswer {
                sendEncryptedMutationResponse
            }
            onBlocking {
                getConfiguredEmailDomainsQuery()
            } doAnswer {
                getConfiguredDomainsQueryResponse
            }
            onBlocking {
                lookupEmailAddressesPublicInfoQuery(
                    any(),
                )
            } doAnswer {
                lookupEmailAddressesPublicInfoQueryResponse
            }
            onBlocking {
                getEmailConfigQuery()
            } doAnswer {
                getEmailConfigQueryResponse
            }
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { getPassword(anyString()) } doReturn ByteArray(42)
            on { getPublicKeyData(anyString()) } doReturn ByteArray(42)
            on { getPrivateKeyData(anyString()) } doReturn ByteArray(42)
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

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking { upload(any(), anyString(), anyOrNull()) } doReturn "42"
        }
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>().stub {
            on {
                encodeToInternetMessageData(
                    anyString(),
                    any(),
                    any(),
                    any(),
                    anyString(),
                    anyString(),
                    any(),
                    any(),
                    any(),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                )
            } doReturn ByteArray(42)
        }
    }

    private val mockSealingService by before {
        DefaultSealingService(
            mockServiceKeyManager,
            mockLogger,
        )
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>().stub {
            onBlocking { encrypt(any<ByteArray>(), any()) } doReturn
                SecurePackage(
                    setOf(
                        emailAttachment,
                    ),
                    emailAttachment,
                )
        }
    }

    private val client by before {
        DefaultSudoEmailClient(
            context,
            mockApiClient,
            mockUserClient,
            mockLogger,
            mockServiceKeyManager,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
            "region",
            "identityBucket",
            "transientBucket",
            null,
            mockS3Client,
            mockS3Client,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockUserClient,
            mockKeyManager,
            mockApiClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `sendEmailMessage() should return results for non-E2E encrypted send when no error present`() =
        runTest {
            val input =
                SendEmailMessageInput(
                    "emailAddressId",
                    InternetMessageFormatHeader(
                        EmailMessage.EmailAddress("from@bar.com"),
                        listOf(EmailMessage.EmailAddress("to@bar.com")),
                        listOf(EmailMessage.EmailAddress("cc@bar.com")),
                        listOf(EmailMessage.EmailAddress("bcc@bar.com")),
                        listOf(EmailMessage.EmailAddress("replyTo@bar.com")),
                        "email message subject",
                    ),
                    "email message body",
                    emptyList(),
                    emptyList(),
                )
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.sendEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).sendEmailMessageMutation(
                check { mutationInput ->
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                },
            )
            verify(mockEmailMessageProcessor).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                isNull(),
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should return results for non-E2E encrypted send with attachments when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.sendEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).sendEmailMessageMutation(
                check { mutationInput ->
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                },
            )
            verify(mockEmailMessageProcessor).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                isNull(),
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should return results for non-E2E encrypted send with no recipients`() =
        runTest {
            val input =
                SendEmailMessageInput(
                    "emailAddressId",
                    InternetMessageFormatHeader(
                        EmailMessage.EmailAddress("from@bar.com"),
                        emptyList(),
                        emptyList(),
                        emptyList(),
                        emptyList(),
                        "email message subject",
                    ),
                    "email message body",
                    emptyList(),
                    emptyList(),
                )
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.sendEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).sendEmailMessageMutation(
                check { mutationInput ->
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                },
            )
            verify(mockEmailMessageProcessor).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                isNull(),
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should return results for non-E2E encrypted send with replyingMessageId`() =
        runTest {
            val replyingMessageId = "replying message id"
            val input =
                SendEmailMessageInput(
                    "emailAddressId",
                    InternetMessageFormatHeader(
                        EmailMessage.EmailAddress("from@bar.com"),
                        listOf(EmailMessage.EmailAddress("to@bar.com")),
                        listOf(EmailMessage.EmailAddress("cc@bar.com")),
                        listOf(EmailMessage.EmailAddress("bcc@bar.com")),
                        listOf(EmailMessage.EmailAddress("replyTo@bar.com")),
                        "email message subject",
                    ),
                    "email message body",
                    emptyList(),
                    emptyList(),
                    replyingMessageId = replyingMessageId,
                )
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.sendEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).sendEmailMessageMutation(
                check { mutationInput ->
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                },
            )
            verify(mockEmailMessageProcessor).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                eq(replyingMessageId),
                isNull(),
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should return results for non-E2E encrypted send with forwardingMessageId`() =
        runTest {
            val forwardingMessageId = "forwarding message id"
            val input =
                SendEmailMessageInput(
                    "emailAddressId",
                    InternetMessageFormatHeader(
                        EmailMessage.EmailAddress("from@bar.com"),
                        listOf(EmailMessage.EmailAddress("to@bar.com")),
                        listOf(EmailMessage.EmailAddress("cc@bar.com")),
                        listOf(EmailMessage.EmailAddress("bcc@bar.com")),
                        listOf(EmailMessage.EmailAddress("replyTo@bar.com")),
                        "email message subject",
                    ),
                    "email message body",
                    emptyList(),
                    emptyList(),
                    forwardingMessageId = forwardingMessageId,
                )
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.sendEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).sendEmailMessageMutation(
                check { mutationInput ->
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                },
            )
            verify(mockEmailMessageProcessor).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                eq(forwardingMessageId),
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should return results for E2E encrypted send when no error present`() =
        runTest {
            val input =
                SendEmailMessageInput(
                    "emailAddressId",
                    InternetMessageFormatHeader(
                        EmailMessage.EmailAddress("from@bear.com"),
                        listOf(EmailMessage.EmailAddress("to@bear.com")),
                        listOf(EmailMessage.EmailAddress("cc@bear.com")),
                        listOf(EmailMessage.EmailAddress("bcc@bear.com")),
                        listOf(EmailMessage.EmailAddress("replyTo@bear.com")),
                        "email message subject",
                    ),
                    "email message body",
                    emptyList(),
                    emptyList(),
                )
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.sendEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(
                check { queryInput ->
                    queryInput.emailAddresses shouldBe listOf("to@bear.com", "cc@bear.com", "bcc@bear.com", "from@bear.com")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).sendEncryptedEmailMessageMutation(
                check { mutationInput ->
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                    mutationInput.rfc822Header shouldBe rfc822HeaderInput
                },
            )
            verify(mockEmailMessageProcessor, times(2)).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                isNull(),
            )
            verify(mockEmailCryptoService).encrypt(
                any(),
                any(),
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should return results for E2E encrypted send with replyingMessageId`() =
        runTest {
            val replyingMessageId = "replying message id"
            val input =
                SendEmailMessageInput(
                    "emailAddressId",
                    InternetMessageFormatHeader(
                        EmailMessage.EmailAddress("from@bear.com"),
                        listOf(EmailMessage.EmailAddress("to@bear.com")),
                        listOf(EmailMessage.EmailAddress("cc@bear.com")),
                        listOf(EmailMessage.EmailAddress("bcc@bear.com")),
                        listOf(EmailMessage.EmailAddress("replyTo@bear.com")),
                        "email message subject",
                    ),
                    "email message body",
                    emptyList(),
                    emptyList(),
                    replyingMessageId = replyingMessageId,
                )
            val rfc822Header = rfc822HeaderInput.copy(inReplyTo = Optional.presentIfNotNull(replyingMessageId))
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.sendEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(
                check { queryInput ->
                    queryInput.emailAddresses shouldBe listOf("to@bear.com", "cc@bear.com", "bcc@bear.com", "from@bear.com")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).sendEncryptedEmailMessageMutation(
                check { mutationInput ->
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                    mutationInput.rfc822Header shouldBe rfc822Header
                },
            )
            verify(mockEmailMessageProcessor, times(2)).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                eq(replyingMessageId),
                isNull(),
            )
            verify(mockEmailCryptoService).encrypt(
                any(),
                any(),
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should return results for E2E encrypted send with forwardingMessageId`() =
        runTest {
            val forwardingMessageId = "forwarding message id"
            val input =
                SendEmailMessageInput(
                    "emailAddressId",
                    InternetMessageFormatHeader(
                        EmailMessage.EmailAddress("from@bear.com"),
                        listOf(EmailMessage.EmailAddress("to@bear.com")),
                        listOf(EmailMessage.EmailAddress("cc@bear.com")),
                        listOf(EmailMessage.EmailAddress("bcc@bear.com")),
                        listOf(EmailMessage.EmailAddress("replyTo@bear.com")),
                        "email message subject",
                    ),
                    "email message body",
                    emptyList(),
                    emptyList(),
                    forwardingMessageId = forwardingMessageId,
                )
            val rfc822Header = rfc822HeaderInput.copy(references = Optional.presentIfNotNull(listOf(forwardingMessageId)))
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.sendEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(
                check { queryInput ->
                    queryInput.emailAddresses shouldBe listOf("to@bear.com", "cc@bear.com", "bcc@bear.com", "from@bear.com")
                },
            )
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).sendEncryptedEmailMessageMutation(
                check { mutationInput ->
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                    mutationInput.rfc822Header shouldBe rfc822Header
                },
            )
            verify(mockEmailMessageProcessor, times(2)).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                eq(forwardingMessageId),
            )
            verify(mockEmailCryptoService).encrypt(
                any(),
                any(),
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage should return results for in-network and out-of-network recipient send when no error present`() =
        runTest {
            val input =
                SendEmailMessageInput(
                    "emailAddressId",
                    InternetMessageFormatHeader(
                        EmailMessage.EmailAddress("from@bear.com"),
                        listOf(EmailMessage.EmailAddress("to@bear.com")),
                        listOf(EmailMessage.EmailAddress("cc@bar.com")),
                        listOf(EmailMessage.EmailAddress("bcc@bar.com")),
                        listOf(EmailMessage.EmailAddress("replyTo@bar.com")),
                        "email message subject",
                    ),
                    "email message body",
                    emptyList(),
                    emptyList(),
                )
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.sendEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).sendEmailMessageMutation(
                check { mutationInput ->
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                },
            )
            verify(mockEmailMessageProcessor).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                isNull(),
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should throw when send email mutation response is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    sendEmailMessageMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, null)
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

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).sendEmailMessageMutation(
                check { mutationInput ->
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                },
            )
            verify(mockEmailMessageProcessor).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                isNull(),
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should throw when send encrypted email mutation response is null`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    sendEncryptedEmailMessageMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, null)
                }
            }

            val input =
                SendEmailMessageInput(
                    "emailAddressId",
                    InternetMessageFormatHeader(
                        EmailMessage.EmailAddress("from@bear.com"),
                        listOf(EmailMessage.EmailAddress("to@bear.com")),
                        listOf(EmailMessage.EmailAddress("cc@bear.com")),
                        listOf(EmailMessage.EmailAddress("bcc@bear.com")),
                        listOf(EmailMessage.EmailAddress("replyTo@bear.com")),
                        "email message subject",
                    ),
                    "email message body",
                    emptyList(),
                    emptyList(),
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                        client.sendEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(
                check { queryInput ->
                    queryInput.emailAddresses shouldBe listOf("to@bear.com", "cc@bear.com", "bcc@bear.com", "from@bear.com")
                },
            )
            verify(mockApiClient).sendEncryptedEmailMessageMutation(
                check { mutationInput ->
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                    mutationInput.rfc822Header shouldBe rfc822HeaderInput
                },
            )
            verify(mockEmailMessageProcessor, times(2)).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                isNull(),
            )
            verify(mockEmailCryptoService).encrypt(
                any(),
                any(),
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should not block coroutine cancellation exception`() =
        runTest {
            mockS3Client.stub {
                onBlocking {
                    upload(
                        any(),
                        anyString(),
                        anyOrNull(),
                    )
                } doThrow CancellationException("mock")
            }

            val input =
                SendEmailMessageInput(
                    "emailAddressId",
                    InternetMessageFormatHeader(
                        EmailMessage.EmailAddress("from@bar.com"),
                        listOf(EmailMessage.EmailAddress("to@bar.com")),
                        listOf(EmailMessage.EmailAddress("cc@bar.com")),
                        listOf(EmailMessage.EmailAddress("bcc@bar.com")),
                        listOf(EmailMessage.EmailAddress("replyTo@bar.com")),
                        "email message subject",
                    ),
                    "email message body",
                    emptyList(),
                    emptyList(),
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<CancellationException> {
                        client.sendEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockEmailMessageProcessor).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                isNull(),
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
        }

    @Test
    fun `sendEmailMessage should throw when any in-network recipient email address does not exist`() =
        runTest {
            val input =
                SendEmailMessageInput(
                    "emailAddressId",
                    InternetMessageFormatHeader(
                        EmailMessage.EmailAddress("from@bear.com"),
                        listOf(EmailMessage.EmailAddress("to@bear.com")),
                        listOf(EmailMessage.EmailAddress("cc@bear.com")),
                        listOf(EmailMessage.EmailAddress("bcc@foo.com")),
                        listOf(EmailMessage.EmailAddress("replyTo@bar.com")),
                        "email message subject",
                    ),
                    "email message body",
                    emptyList(),
                    emptyList(),
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.InNetworkAddressNotFoundException> {
                        client.sendEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(
                check { queryInput ->
                    queryInput.emailAddresses shouldBe listOf("to@bear.com", "cc@bear.com", "bcc@foo.com", "from@bear.com")
                },
            )
        }

    @Test
    fun `sendEmailMessage() should throw when non-E2E send response has various errors`() =
        runTest {
            testSendException<SudoEmailClient.EmailMessageException.InvalidMessageContentException>(
                "InvalidEmailContents",
            )
            testSendException<SudoEmailClient.EmailMessageException.LimitExceededException>(
                "ServiceQuotaExceededError",
            )
            testSendException<SudoEmailClient.EmailMessageException.UnauthorizedAddressException>("UnauthorizedAddress")
            testSendException<SudoEmailClient.EmailMessageException.FailedException>("blah")

            verify(mockApiClient, times(4)).getConfiguredEmailDomainsQuery()
            verify(mockApiClient, times(4)).getEmailConfigQuery()
            verify(mockApiClient, times(4)).sendEmailMessageMutation(
                any(),
            )
            verify(mockEmailMessageProcessor, times(4)).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                isNull(),
            )
            verify(mockS3Client, times(4)).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client, times(4)).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should throw when E2E send response has various errors`() =
        runTest {
            testEncryptedSendException<SudoEmailClient.EmailMessageException.InvalidMessageContentException>(
                "InvalidEmailContents",
            )
            testEncryptedSendException<SudoEmailClient.EmailMessageException.LimitExceededException>(
                "ServiceQuotaExceededError",
            )
            testEncryptedSendException<SudoEmailClient.EmailMessageException.UnauthorizedAddressException>(
                "UnauthorizedAddress",
            )
            testEncryptedSendException<SudoEmailClient.EmailMessageException.FailedException>("blah")

            verify(mockApiClient, times(4)).getConfiguredEmailDomainsQuery()
            verify(mockApiClient, times(4)).getEmailConfigQuery()
            verify(mockApiClient, times(4)).lookupEmailAddressesPublicInfoQuery(
                any(),
            )
            verify(mockApiClient, times(4)).sendEncryptedEmailMessageMutation(
                any(),
            )
            verify(mockEmailMessageProcessor, times(8)).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                isNull(),
            )
            verify(mockEmailCryptoService, times(4)).encrypt(
                any(),
                any(),
            )
            verify(mockS3Client, times(4)).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client, times(4)).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should throw emailMessageSizeLimitExceededError when E2E message is too big`() =
        runTest {
            val limit = 10485769
            val getEmailConfigQueryResponse =
                DataFactory.getEmailConfigQueryResponse(
                    emailMessageMaxOutboundMessageSize = limit,
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailConfigQuery()
                }.thenAnswer {
                    getEmailConfigQueryResponse
                }
            }
            mockEmailMessageProcessor.stub {
                on {
                    encodeToInternetMessageData(
                        anyString(),
                        any(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        any(),
                        any(),
                        isNull(),
                        isNull(),
                    )
                } doReturn ByteArray(limit + 1)
            }

            val input =
                SendEmailMessageInput(
                    "emailAddressId",
                    InternetMessageFormatHeader(
                        EmailMessage.EmailAddress("from@bear.com"),
                        listOf(EmailMessage.EmailAddress("to@bear.com")),
                        listOf(EmailMessage.EmailAddress("cc@bear.com")),
                        listOf(EmailMessage.EmailAddress("bcc@bear.com")),
                        listOf(EmailMessage.EmailAddress("replyTo@bear.com")),
                        "email message subject",
                    ),
                    "email message body",
                    emptyList(),
                    emptyList(),
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageSizeLimitExceededException> {
                        client.sendEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(
                any(),
            )
            verify(mockEmailMessageProcessor, times(2)).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                isNull(),
            )
            verify(mockEmailCryptoService).encrypt(
                any(),
                any(),
            )
        }

    @Test
    fun `sendEmailMessage() should throw emailMessageSizeLimitExceededError when non-E2E message is too big`() =
        runTest {
            val limit = 10485769
            val getEmailConfigQueryResponse =
                DataFactory.getEmailConfigQueryResponse(
                    emailMessageMaxOutboundMessageSize = limit,
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailConfigQuery()
                }.thenAnswer {
                    getEmailConfigQueryResponse
                }
            }
            mockEmailMessageProcessor.stub {
                on {
                    encodeToInternetMessageData(
                        anyString(),
                        any(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        any(),
                        any(),
                        isNull(),
                        isNull(),
                    )
                } doReturn ByteArray(limit + 1)
            }

            val input =
                SendEmailMessageInput(
                    "emailAddressId",
                    InternetMessageFormatHeader(
                        EmailMessage.EmailAddress("from@bar.com"),
                        listOf(EmailMessage.EmailAddress("to@bar.com")),
                        listOf(EmailMessage.EmailAddress("cc@bar.com")),
                        listOf(EmailMessage.EmailAddress("bcc@bar.com")),
                        listOf(EmailMessage.EmailAddress("replyTo@bar.com")),
                        "email message subject",
                    ),
                    "email message body",
                    emptyList(),
                    emptyList(),
                )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageSizeLimitExceededException> {
                        client.sendEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockEmailMessageProcessor).encodeToInternetMessageData(
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                isNull(),
                isNull(),
            )
        }

    private inline fun <reified T : Exception> testSendException(apolloError: String) =
        runTest {
            val errorSendResponse =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to apolloError),
                )
            mockApiClient.stub {
                onBlocking {
                    sendEmailMessageMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, listOf(errorSendResponse))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<T> {
                        client.sendEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()
        }

    private inline fun <reified T : Exception> testEncryptedSendException(apolloError: String) =
        runTest {
            val errorSendResponse =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to apolloError),
                )
            mockApiClient.stub {
                onBlocking {
                    sendEncryptedEmailMessageMutation(
                        any(),
                    )
                }.thenAnswer {
                    GraphQLResponse(null, listOf(errorSendResponse))
                }
            }

            val input =
                SendEmailMessageInput(
                    "senderEmailAddressId",
                    InternetMessageFormatHeader(
                        EmailMessage.EmailAddress("from@bear.com"),
                        listOf(EmailMessage.EmailAddress("to@bear.com")),
                        listOf(EmailMessage.EmailAddress("cc@bear.com")),
                        listOf(EmailMessage.EmailAddress("bcc@bear.com")),
                        listOf(EmailMessage.EmailAddress("replyTo@bear.com")),
                        "email message subject",
                    ),
                    "email message body",
                    emptyList(),
                    emptyList(),
                )
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<T> {
                        client.sendEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()
        }
}
