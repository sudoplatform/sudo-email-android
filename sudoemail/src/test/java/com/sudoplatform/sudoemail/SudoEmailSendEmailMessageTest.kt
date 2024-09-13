/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.apollographql.apollo3.api.Optional
import com.sudoplatform.sudoemail.graphql.GetConfiguredEmailDomainsQuery
import com.sudoplatform.sudoemail.graphql.GetEmailConfigQuery
import com.sudoplatform.sudoemail.graphql.LookupEmailAddressesPublicInfoQuery
import com.sudoplatform.sudoemail.graphql.SendEmailMessageMutation
import com.sudoplatform.sudoemail.graphql.SendEncryptedEmailMessageMutation
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
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
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
import java.util.concurrent.CancellationException
import com.sudoplatform.sudoemail.graphql.type.LookupEmailAddressesPublicInfoInput as LookupEmailAddressesPublicInfoRequest
import com.sudoplatform.sudoemail.graphql.type.SendEmailMessageInput as SendEmailMessageRequest
import com.sudoplatform.sudoemail.graphql.type.SendEncryptedEmailMessageInput as SendEncryptedEmailMessageRequest

/**
 * Test the correct operation of [SudoEmailClient.sendEmailMessage]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailSendEmailMessageTest : BaseTests() {

    private val emailAddressId = "emailAddressId"
    private val emailAttachment = EmailAttachment(
        "fileName.jpg",
        "contentId",
        "mimeType",
        false,
        ByteArray(1),
    )
    private val headers = InternetMessageFormatHeader(
        EmailMessage.EmailAddress("from@bar.com"),
        listOf(EmailMessage.EmailAddress("to@bar.com")),
        listOf(EmailMessage.EmailAddress("cc@bar.com")),
        listOf(EmailMessage.EmailAddress("bcc@bar.com")),
        listOf(EmailMessage.EmailAddress("replyTo@bar.com")),
        "email message subject",
    )
    private val domains = listOf("foo.com", "bear.com")
    private val rfc822HeaderInput = Rfc822HeaderInput(
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
        JSONObject(
            """
                {
                    'sendEmailMessageV2': {
                        '__typename': 'SendEmailMessageResult',
                        'id': 'sendEmailMessage',
                        'createdAtEpochMs': 1.0
                    }
                }
            """.trimIndent(),
        )
    }

    private val sendEncryptedMutationResponse by before {
        JSONObject(
            """
                {
                    'sendEncryptedEmailMessage': {
                        '__typename': 'SendEmailMessageResult',
                        'id': 'sendEncryptedEmailMessage',
                        'createdAtEpochMs': 1.0
                    }
                }
            """.trimIndent(),
        )
    }

    private val lookupEmailAddressesPublicInfoQueryResponse by before {
        JSONObject(
            """
                {
                    'lookupEmailAddressesPublicInfo': {
                        'items': [{
                            '__typename': 'EmailAddressPublicInfo',
                            'emailAddress': 'to@bear.com',
                            'keyId': 'keyId',
                            'publicKey': 'publicKey'
                        },
                        {
                            '__typename': 'EmailAddressPublicInfo',
                            'emailAddress': 'cc@bear.com',
                            'keyId': 'keyId',
                            'publicKey': 'publicKey'
                        },
                        {
                            '__typename': 'EmailAddressPublicInfo',
                            'emailAddress': 'bcc@bear.com',
                            'keyId': 'keyId',
                            'publicKey': 'publicKey'
                        }],
                        'nextToken': null
                    }
                }
            """.trimIndent(),
        )
    }

    private val getConfiguredDomainsQueryResponse by before {
        JSONObject(
            """
                {
                    'getConfiguredEmailDomains': {
                        '__typename': 'GetConfiguredEmailDomains',
                        'domains': $domains
                    }
                }
            """.trimIndent(),
        )
    }

    private val getEmailConfigQueryResponse by before {
        JSONObject(
            """
                {
                    'getEmailConfig': {
                        '__typename': 'EmailConfigurationData',
                        'deleteEmailMessagesLimit': 10,
                        'updateEmailMessagesLimit': 5,
                        'emailMessageMaxInboundMessageSize': 200,
                        'emailMessageMaxOutboundMessageSize': 100,
                        'emailMessageRecipientsLimit': 5,
                        'encryptedEmailMessageRecipientsLimit': 10
                    }
                }
            """.trimIndent(),
        )
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

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(SendEmailMessageMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(sendMutationResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
            }
            on {
                mutate<String>(
                    argThat { this.query.equals(SendEncryptedEmailMessageMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(sendEncryptedMutationResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
            }
            on {
                query<String>(
                    argThat { this.query.equals(GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(getConfiguredDomainsQueryResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
            }
            on {
                query<String>(
                    argThat { this.query.equals(LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(lookupEmailAddressesPublicInfoQueryResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
            }
            on {
                query<String>(
                    argThat { this.query.equals(GetEmailConfigQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(getEmailConfigQueryResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
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
            onBlocking { encrypt(any<ByteArray>(), any()) } doReturn SecurePackage(
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
            GraphQLClient(mockApiCategory),
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
            mockApiCategory,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `sendEmailMessage() should return results for non-E2E encrypted send when no error present`() =
        runTest {
            val input = SendEmailMessageInput(
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
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.sendEmailMessage(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe SendEmailMessageMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as SendEmailMessageRequest
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                },
                any(),
                any(),
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
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should return results for non-E2E encrypted send with attachments when no error present`() =
        runTest {
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.sendEmailMessage(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe SendEmailMessageMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as SendEmailMessageRequest
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                },
                any(),
                any(),
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
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should return results for non-E2E encrypted send with no recipients`() =
        runTest {
            val input = SendEmailMessageInput(
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
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.sendEmailMessage(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe SendEmailMessageMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as SendEmailMessageRequest
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                },
                any(),
                any(),
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
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should return results for E2E encrypted send when no error present`() =
        runTest {
            val input = SendEmailMessageInput(
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
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.sendEmailMessage(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT
                    val queryInput = it.variables["input"] as LookupEmailAddressesPublicInfoRequest
                    queryInput.emailAddresses shouldBe listOf("to@bear.com", "cc@bear.com", "bcc@bear.com", "from@bear.com")
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe SendEncryptedEmailMessageMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as SendEncryptedEmailMessageRequest
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                    mutationInput.rfc822Header shouldBe rfc822HeaderInput
                },
                any(),
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
            val input = SendEmailMessageInput(
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
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.sendEmailMessage(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT
                    val queryInput = it.variables["input"] as LookupEmailAddressesPublicInfoRequest
                    queryInput.emailAddresses shouldBe listOf("to@bear.com", "from@bear.com")
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe SendEmailMessageMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as SendEmailMessageRequest
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                },
                any(),
                any(),
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
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should throw when send email mutation response is null`() =
        runTest {
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(SendEmailMessageMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                }.thenAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(null, null),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                    client.sendEmailMessage(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe SendEmailMessageMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as SendEmailMessageRequest
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                },
                any(),
                any(),
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
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
            verify(mockS3Client).delete(anyString())
        }

    @Test
    fun `sendEmailMessage() should throw when send encrypted email mutation response is null`() =
        runTest {
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(SendEncryptedEmailMessageMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                }.thenAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(null, null),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val input = SendEmailMessageInput(
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

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                    client.sendEmailMessage(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT
                    val queryInput = it.variables["input"] as LookupEmailAddressesPublicInfoRequest
                    queryInput.emailAddresses shouldBe listOf("to@bear.com", "cc@bear.com", "bcc@bear.com", "from@bear.com")
                },
                any(),
                any(),
            )
            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe SendEncryptedEmailMessageMutation.OPERATION_DOCUMENT
                    val mutationInput = it.variables["input"] as SendEncryptedEmailMessageRequest
                    mutationInput.emailAddressId shouldBe emailAddressId
                    mutationInput.message shouldBe S3EmailObjectInput("transientBucket", "42", "region")
                    mutationInput.rfc822Header shouldBe rfc822HeaderInput
                },
                any(),
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

            val input = SendEmailMessageInput(
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

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<CancellationException> {
                    client.sendEmailMessage(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
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
            )
            verify(mockS3Client).upload(any(), anyString(), anyOrNull())
        }

    @Test
    fun `sendEmailMessage should throw when any in-network recipient email address does not exist`() =
        runTest {
            val input = SendEmailMessageInput(
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

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.InNetworkAddressNotFoundException> {
                    client.sendEmailMessage(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT
                    val queryInput = it.variables["input"] as LookupEmailAddressesPublicInfoRequest
                    queryInput.emailAddresses shouldBe listOf("to@bear.com", "cc@bear.com", "bcc@foo.com", "from@bear.com")
                },
                any(),
                any(),
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

            verify(mockApiCategory, times(4)).query<String>(
                check {
                    it.query shouldBe GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory, times(4)).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory, times(4)).mutate<String>(
                check {
                    it.query shouldBe SendEmailMessageMutation.OPERATION_DOCUMENT
                },
                any(),
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

            verify(mockApiCategory, times(4)).query<String>(
                check {
                    it.query shouldBe GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory, times(4)).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory, times(4)).query<String>(
                check {
                    it.query shouldBe LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory, times(4)).mutate<String>(
                check {
                    it.query shouldBe SendEncryptedEmailMessageMutation.OPERATION_DOCUMENT
                },
                any(),
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
            val getEmailConfigQueryResponse = JSONObject(
                """
                {
                    'getEmailConfig': {
                        '__typename': 'EmailConfigurationData',
                        'deleteEmailMessagesLimit': 10,
                        'updateEmailMessagesLimit': 5,
                        'emailMessageMaxInboundMessageSize': 200,
                        'emailMessageMaxOutboundMessageSize': $limit,
                        'emailMessageRecipientsLimit': 5,
                        'encryptedEmailMessageRecipientsLimit': 10
                    }
                }
                """.trimIndent(),
            )
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(GetEmailConfigQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                }.thenAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(getEmailConfigQueryResponse.toString(), null),
                    )
                    mock<GraphQLOperation<String>>()
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
                    )
                } doReturn ByteArray(limit + 1)
            }

            val input = SendEmailMessageInput(
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

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageSizeLimitExceededException> {
                    client.sendEmailMessage(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT
                },
                any(),
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
            val getEmailConfigQueryResponse = JSONObject(
                """
                {
                    'getEmailConfig': {
                        '__typename': 'EmailConfigurationData',
                        'deleteEmailMessagesLimit': 10,
                        'updateEmailMessagesLimit': 5,
                        'emailMessageMaxInboundMessageSize': 200,
                        'emailMessageMaxOutboundMessageSize': $limit,
                        'emailMessageRecipientsLimit': 5,
                        'encryptedEmailMessageRecipientsLimit': 10
                    }
                }
                """.trimIndent(),
            )
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(GetEmailConfigQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                }.thenAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(getEmailConfigQueryResponse.toString(), null),
                    )
                    mock<GraphQLOperation<String>>()
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
                    )
                } doReturn ByteArray(limit + 1)
            }

            val input = SendEmailMessageInput(
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

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageSizeLimitExceededException> {
                    client.sendEmailMessage(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailConfigQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
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
            )
        }

    private inline fun <reified T : Exception> testSendException(apolloError: String) =
        runTest {
            val errorSendResponse = GraphQLResponse.Error(
                "Test generated error",
                emptyList(),
                emptyList(),
                mapOf("errorType" to apolloError),
            )
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(SendEmailMessageMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                }.thenAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(null, listOf(errorSendResponse)),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<T> {
                    client.sendEmailMessage(input)
                }
            }
            deferredResult.start()
            deferredResult.await()
        }

    private inline fun <reified T : Exception> testEncryptedSendException(apolloError: String) =
        runTest {
            val errorSendResponse = GraphQLResponse.Error(
                "Test generated error",
                emptyList(),
                emptyList(),
                mapOf("errorType" to apolloError),
            )
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(SendEncryptedEmailMessageMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                }.thenAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(null, listOf(errorSendResponse)),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val input = SendEmailMessageInput(
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
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<T> {
                    client.sendEmailMessage(input)
                }
            }
            deferredResult.start()
            deferredResult.await()
        }
}
