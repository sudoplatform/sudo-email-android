/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.GetEmailConfigQuery
import com.sudoplatform.sudoemail.graphql.GetEmailDomainsQuery
import com.sudoplatform.sudoemail.graphql.LookupEmailAddressesPublicInfoQuery
import com.sudoplatform.sudoemail.graphql.SendEmailMessageMutation
import com.sudoplatform.sudoemail.graphql.SendEncryptedEmailMessageMutation
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressPublicInfo
import com.sudoplatform.sudoemail.graphql.fragment.EmailConfigurationData
import com.sudoplatform.sudoemail.graphql.fragment.SendEmailMessageResult
import com.sudoplatform.sudoemail.graphql.type.LookupEmailAddressesPublicInfoInput
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CancellationException
import com.sudoplatform.sudoemail.graphql.type.SendEmailMessageInput as SendEmailMessageRequest
import com.sudoplatform.sudoemail.graphql.type.SendEncryptedEmailMessageInput as SendEncryptedEmailMessageRequest

/**
 * Test the correct operation of [SudoEmailClient.sendEmailMessage]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailSendEmailMessageTest : BaseTests() {

    private val emailAddressId = "emailAddressId"
    private val clientRefId = "clientRefId"
    private val emailAttachment = EmailAttachment(
        "fileName.jpg",
        "contentId",
        "mimeType",
        false,
        ByteArray(1),
    )

    private val message by before {
        S3EmailObjectInput.builder()
            .bucket("bucket")
            .key("key")
            .region("region")
            .build()
    }

    private val supportedDomains by before {
        GetEmailDomainsQuery.GetEmailDomains(
            "typename",
            listOf("foo.com", "bear.com"),
        )
    }

    private val emailAddressPublicInfo by before {
        EmailAddressPublicInfo(
            "typename",
            "to@bear.com",
            "keyId",
            "publicKey",
        )
    }

    private val sendInput by before {
        SendEmailMessageRequest.builder()
            .emailAddressId(emailAddressId)
            .clientRefId(clientRefId)
            .message(message)
            .build()
    }

    private val rfc822HeaderInput by before {
        Rfc822HeaderInput.builder()
            .from("from@bar.com")
            .to(listOf("to@bar.com"))
            .cc(listOf("cc@bar.com"))
            .bcc(listOf("bcc@bar.com"))
            .replyTo(listOf("replyTo@bar.com"))
            .subject("email message subject")
            .build()
    }
    private val sendEncryptedInput by before {
        SendEncryptedEmailMessageRequest.builder()
            .emailAddressId(emailAddressId)
            .clientRefId(clientRefId)
            .message(message)
            .rfc822Header(rfc822HeaderInput)
            .build()
    }

    private val lookupPublicInfoInput by before {
        LookupEmailAddressesPublicInfoInput.builder()
            .emailAddresses(mutableListOf("emailAddress"))
            .build()
    }

    private val sendMutationResult by before {
        SendEmailMessageMutation.SendEmailMessageV2(
            "__typename",
            SendEmailMessageMutation.SendEmailMessageV2.Fragments(
                SendEmailMessageResult(
                    "__typename",
                    "sendEmailMessage",
                    1.0,
                ),
            ),
        )
    }

    private val sendMutationResponse by before {
        Response.builder<SendEmailMessageMutation.Data>(SendEmailMessageMutation(sendInput))
            .data(SendEmailMessageMutation.Data(sendMutationResult))
            .build()
    }

    private val sendEncryptedMutationResult by before {
        SendEncryptedEmailMessageMutation.SendEncryptedEmailMessage(
            "__typename",
            SendEncryptedEmailMessageMutation.SendEncryptedEmailMessage.Fragments(
                SendEmailMessageResult(
                    "__typename",
                    "sendEncryptedEmailMessage",
                    1.0,
                ),
            ),
        )
    }

    private val sendEncryptedMutationResponse by before {
        Response.builder<SendEncryptedEmailMessageMutation.Data>(
            SendEncryptedEmailMessageMutation(
                sendEncryptedInput,
            ),
        )
            .data(SendEncryptedEmailMessageMutation.Data(sendEncryptedMutationResult))
            .build()
    }

    private val lookupPublicInfoQueryResult by before {
        LookupEmailAddressesPublicInfoQuery.LookupEmailAddressesPublicInfo(
            "typename",
            listOf(
                LookupEmailAddressesPublicInfoQuery.Item(
                    "typename",
                    LookupEmailAddressesPublicInfoQuery.Item.Fragments(emailAddressPublicInfo),
                ),
            ),
        )
    }

    private val lookupPublicInfoQueryItemsResult by before {
        LookupEmailAddressesPublicInfoQuery.LookupEmailAddressesPublicInfo(
            "typename",
            listOf(
                LookupEmailAddressesPublicInfoQuery.Item(
                    "typename",
                    LookupEmailAddressesPublicInfoQuery.Item.Fragments(
                        EmailAddressPublicInfo(
                            "typename",
                            "to@bear.com",
                            "keyId",
                            "publicKey",
                        ),
                    ),
                ),
                LookupEmailAddressesPublicInfoQuery.Item(
                    "typename",
                    LookupEmailAddressesPublicInfoQuery.Item.Fragments(
                        EmailAddressPublicInfo(
                            "typename",
                            "cc@bear.com",
                            "keyId",
                            "publicKey",
                        ),
                    ),
                ),
                LookupEmailAddressesPublicInfoQuery.Item(
                    "typename",
                    LookupEmailAddressesPublicInfoQuery.Item.Fragments(
                        EmailAddressPublicInfo(
                            "typename",
                            "bcc@bear.com",
                            "keyId",
                            "publicKey",
                        ),
                    ),
                ),
            ),
        )
    }

    private val getSupportedDomainsQueryResponse by before {
        Response.builder<GetEmailDomainsQuery.Data>(GetEmailDomainsQuery())
            .data(GetEmailDomainsQuery.Data(supportedDomains))
            .build()
    }

    private val lookupPublicInfoResponse by before {
        Response.builder<LookupEmailAddressesPublicInfoQuery.Data>(
            LookupEmailAddressesPublicInfoQuery(lookupPublicInfoInput),
        )
            .data(LookupEmailAddressesPublicInfoQuery.Data(lookupPublicInfoQueryResult))
            .build()
    }

    private val getConfigDataQueryResult by before {
        GetEmailConfigQuery.GetEmailConfig(
            "typeName",
            GetEmailConfigQuery.GetEmailConfig.Fragments(
                EmailConfigurationData(
                    "typename",
                    10,
                    5,
                    200,
                    100,
                    5,
                    10,
                ),
            ),
        )
    }

    private val getConfigDataQueryResponse by before {
        Response.builder<GetEmailConfigQuery.Data>(GetEmailConfigQuery())
            .data(GetEmailConfigQuery.Data(getConfigDataQueryResult))
            .build()
    }

    private val sendHolder = CallbackHolder<SendEmailMessageMutation.Data>()
    private val sendEncryptedHolder = CallbackHolder<SendEncryptedEmailMessageMutation.Data>()
    private val getSupportedDomainsHolder = CallbackHolder<GetEmailDomainsQuery.Data>()
    private val lookupPublicInfoHolder = CallbackHolder<LookupEmailAddressesPublicInfoQuery.Data>()
    private val getConfigDataHolder = CallbackHolder<GetEmailConfigQuery.Data>()

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "subject"
            on { getRefreshToken() } doReturn "refreshToken"
        }
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<SendEmailMessageMutation>()) } doReturn sendHolder.mutationOperation
            on { mutate(any<SendEncryptedEmailMessageMutation>()) } doReturn sendEncryptedHolder.mutationOperation
            on { query(any<GetEmailDomainsQuery>()) } doReturn getSupportedDomainsHolder.queryOperation
            on { query(any<LookupEmailAddressesPublicInfoQuery>()) } doReturn lookupPublicInfoHolder.queryOperation
            on { query(any<GetEmailConfigQuery>()) } doReturn getConfigDataHolder.queryOperation
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
            mockAppSyncClient,
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

    @Before
    fun init() {
        getConfigDataHolder.callback = null
        getSupportedDomainsHolder.callback = null
        lookupPublicInfoHolder.callback = null
        sendHolder.callback = null
        sendEncryptedHolder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockUserClient,
            mockKeyManager,
            mockAppSyncClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `sendEmailMessage() should return results for non-E2E encrypted send when no error present`() =
        runTest {
            getSupportedDomainsHolder.callback = null
            getConfigDataHolder.callback = null
            sendHolder.callback = null

            val input = SendEmailMessageInput(
                "senderEmailAddressId",
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

            delay(100L)
            getSupportedDomainsHolder.callback shouldNotBe null
            getSupportedDomainsHolder.callback?.onResponse(getSupportedDomainsQueryResponse)

            delay(100L)
            getConfigDataHolder.callback shouldNotBe null
            getConfigDataHolder.callback?.onResponse(getConfigDataQueryResponse)

            delay(100L)
            sendHolder.callback shouldNotBe null
            sendHolder.callback?.onResponse(sendMutationResponse)

            val result = deferredResult.await()
            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockAppSyncClient).query(any<GetEmailConfigQuery>())
            verify(mockAppSyncClient).query(any<GetEmailDomainsQuery>())
            verify(mockAppSyncClient).mutate(any<SendEmailMessageMutation>())
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
            getSupportedDomainsHolder.callback = null
            getConfigDataHolder.callback = null
            sendHolder.callback = null

            val input = SendEmailMessageInput(
                "senderEmailAddressId",
                InternetMessageFormatHeader(
                    EmailMessage.EmailAddress("from@bar.com"),
                    listOf(EmailMessage.EmailAddress("to@bar.com")),
                    listOf(EmailMessage.EmailAddress("cc@bar.com")),
                    listOf(EmailMessage.EmailAddress("bcc@bar.com")),
                    listOf(EmailMessage.EmailAddress("replyTo@bar.com")),
                    "email message subject",
                ),
                "email message body",
                listOf(emailAttachment),
                listOf(emailAttachment),
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.sendEmailMessage(input)
            }
            deferredResult.start()

            delay(100L)
            getSupportedDomainsHolder.callback shouldNotBe null
            getSupportedDomainsHolder.callback?.onResponse(getSupportedDomainsQueryResponse)

            delay(100L)
            getConfigDataHolder.callback shouldNotBe null
            getConfigDataHolder.callback?.onResponse(getConfigDataQueryResponse)

            delay(100L)
            sendHolder.callback shouldNotBe null
            sendHolder.callback?.onResponse(sendMutationResponse)

            val result = deferredResult.await()
            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockAppSyncClient).query(any<GetEmailDomainsQuery>())
            verify(mockAppSyncClient).query(any<GetEmailConfigQuery>())
            verify(mockAppSyncClient).mutate(any<SendEmailMessageMutation>())
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
            getSupportedDomainsHolder.callback = null
            getConfigDataHolder.callback = null
            sendHolder.callback = null

            val input = SendEmailMessageInput(
                "senderEmailAddressId",
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

            delay(100L)
            getSupportedDomainsHolder.callback shouldNotBe null
            getSupportedDomainsHolder.callback?.onResponse(getSupportedDomainsQueryResponse)

            delay(100L)
            getConfigDataHolder.callback shouldNotBe null
            getConfigDataHolder.callback?.onResponse(getConfigDataQueryResponse)

            delay(100L)
            sendHolder.callback shouldNotBe null
            sendHolder.callback?.onResponse(sendMutationResponse)

            val result = deferredResult.await()
            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockAppSyncClient).query(any<GetEmailConfigQuery>())
            verify(mockAppSyncClient).query(any<GetEmailDomainsQuery>())
            verify(mockAppSyncClient).mutate(any<SendEmailMessageMutation>())
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
            val lookupPublicInfoResponse by before {
                Response.builder<LookupEmailAddressesPublicInfoQuery.Data>(
                    LookupEmailAddressesPublicInfoQuery(lookupPublicInfoInput),
                )
                    .data(LookupEmailAddressesPublicInfoQuery.Data(lookupPublicInfoQueryItemsResult))
                    .build()
            }

            getSupportedDomainsHolder.callback = null
            getConfigDataHolder.callback = null
            lookupPublicInfoHolder.callback = null
            sendEncryptedHolder.callback = null

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
                client.sendEmailMessage(input)
            }
            deferredResult.start()

            delay(100L)
            getSupportedDomainsHolder.callback shouldNotBe null
            getSupportedDomainsHolder.callback?.onResponse(getSupportedDomainsQueryResponse)

            delay(100L)
            lookupPublicInfoHolder.callback shouldNotBe null
            lookupPublicInfoHolder.callback?.onResponse(lookupPublicInfoResponse)

            delay(100L)
            getConfigDataHolder.callback shouldNotBe null
            getConfigDataHolder.callback?.onResponse(getConfigDataQueryResponse)

            delay(100L)
            sendEncryptedHolder.callback shouldNotBe null
            sendEncryptedHolder.callback?.onResponse(sendEncryptedMutationResponse)

            val result = deferredResult.await()
            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockAppSyncClient).query(any<GetEmailDomainsQuery>())
            verify(mockAppSyncClient).query(any<GetEmailConfigQuery>())
            verify(mockAppSyncClient).query(any<LookupEmailAddressesPublicInfoQuery>())
            verify(mockAppSyncClient).mutate(any<SendEncryptedEmailMessageMutation>())
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
            val lookupPublicInfoResponse by before {
                Response.builder<LookupEmailAddressesPublicInfoQuery.Data>(
                    LookupEmailAddressesPublicInfoQuery(lookupPublicInfoInput),
                )
                    .data(LookupEmailAddressesPublicInfoQuery.Data(lookupPublicInfoQueryItemsResult))
                    .build()
            }

            getSupportedDomainsHolder.callback = null
            getConfigDataHolder.callback = null
            lookupPublicInfoHolder.callback = null
            sendHolder.callback = null
            sendEncryptedHolder.callback = null

            val input = SendEmailMessageInput(
                "senderEmailAddressId",
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

            delay(100L)
            getSupportedDomainsHolder.callback shouldNotBe null
            getSupportedDomainsHolder.callback?.onResponse(getSupportedDomainsQueryResponse)

            delay(100L)
            lookupPublicInfoHolder.callback shouldNotBe null
            lookupPublicInfoHolder.callback?.onResponse(lookupPublicInfoResponse)

            delay(100L)
            getConfigDataHolder.callback shouldNotBe null
            getConfigDataHolder.callback?.onResponse(getConfigDataQueryResponse)

            delay(100L)
            sendHolder.callback shouldNotBe null
            sendHolder.callback?.onResponse(sendMutationResponse)

            val result = deferredResult.await()
            result shouldNotBe null
            result.id.isBlank() shouldBe false

            verify(mockAppSyncClient).query(any<GetEmailDomainsQuery>())
            verify(mockAppSyncClient).query(any<GetEmailConfigQuery>())
            verify(mockAppSyncClient).query(any<LookupEmailAddressesPublicInfoQuery>())
            verify(mockAppSyncClient).mutate(any<SendEmailMessageMutation>())
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
            val nullSendResponse by before {
                Response.builder<SendEmailMessageMutation.Data>(SendEmailMessageMutation(sendInput))
                    .data(null)
                    .build()
            }

            getSupportedDomainsHolder.callback = null
            getConfigDataHolder.callback = null
            sendHolder.callback = null

            val input = SendEmailMessageInput(
                "senderEmailAddressId",
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
                shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                    client.sendEmailMessage(input)
                }
            }
            deferredResult.start()

            delay(100L)
            getSupportedDomainsHolder.callback shouldNotBe null
            getSupportedDomainsHolder.callback?.onResponse(getSupportedDomainsQueryResponse)

            delay(100L)
            getConfigDataHolder.callback shouldNotBe null
            getConfigDataHolder.callback?.onResponse(getConfigDataQueryResponse)

            delay(100L)
            sendHolder.callback shouldNotBe null
            sendHolder.callback?.onResponse(nullSendResponse)

            deferredResult.await()

            verify(mockAppSyncClient).query(any<GetEmailDomainsQuery>())
            verify(mockAppSyncClient).query(any<GetEmailConfigQuery>())
            verify(mockAppSyncClient).mutate(any<SendEmailMessageMutation>())
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
            val lookupPublicInfoResponse by before {
                Response.builder<LookupEmailAddressesPublicInfoQuery.Data>(
                    LookupEmailAddressesPublicInfoQuery(lookupPublicInfoInput),
                )
                    .data(LookupEmailAddressesPublicInfoQuery.Data(lookupPublicInfoQueryItemsResult))
                    .build()
            }
            val nullEncryptedSendResponse by before {
                Response.builder<SendEncryptedEmailMessageMutation.Data>(
                    SendEncryptedEmailMessageMutation(sendEncryptedInput),
                )
                    .data(null)
                    .build()
            }

            getSupportedDomainsHolder.callback = null
            getConfigDataHolder.callback = null
            lookupPublicInfoHolder.callback = null
            sendEncryptedHolder.callback = null

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
                shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                    client.sendEmailMessage(input)
                }
            }
            deferredResult.start()

            delay(100L)
            getSupportedDomainsHolder.callback shouldNotBe null
            getSupportedDomainsHolder.callback?.onResponse(getSupportedDomainsQueryResponse)

            delay(100L)
            lookupPublicInfoHolder.callback shouldNotBe null
            lookupPublicInfoHolder.callback?.onResponse(lookupPublicInfoResponse)

            delay(100L)
            getConfigDataHolder.callback shouldNotBe null
            getConfigDataHolder.callback?.onResponse(getConfigDataQueryResponse)

            delay(100L)
            sendEncryptedHolder.callback shouldNotBe null
            sendEncryptedHolder.callback?.onResponse(nullEncryptedSendResponse)

            deferredResult.await()

            verify(mockAppSyncClient).query(any<GetEmailDomainsQuery>())
            verify(mockAppSyncClient).query(any<GetEmailConfigQuery>())
            verify(mockAppSyncClient).query(any<LookupEmailAddressesPublicInfoQuery>())
            verify(mockAppSyncClient).mutate(any<SendEncryptedEmailMessageMutation>())
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
                "senderEmailAddressId",
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

            delay(100L)
            getSupportedDomainsHolder.callback shouldNotBe null
            getSupportedDomainsHolder.callback?.onResponse(getSupportedDomainsQueryResponse)

            delay(100L)
            getConfigDataHolder.callback shouldNotBe null
            getConfigDataHolder.callback?.onResponse(getConfigDataQueryResponse)

            deferredResult.await()

            verify(mockAppSyncClient).query(any<GetEmailDomainsQuery>())
            verify(mockAppSyncClient).query(any<GetEmailConfigQuery>())
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
            getSupportedDomainsHolder.callback = null
            lookupPublicInfoHolder.callback = null

            val input = SendEmailMessageInput(
                "senderEmailAddressId",
                InternetMessageFormatHeader(
                    EmailMessage.EmailAddress("from@bear.com"),
                    listOf(EmailMessage.EmailAddress("to@bear.com")),
                    listOf(EmailMessage.EmailAddress("cc@bear.com")),
                    listOf(EmailMessage.EmailAddress("bcc@bar.com")),
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

            delay(100L)
            getSupportedDomainsHolder.callback shouldNotBe null
            getSupportedDomainsHolder.callback?.onResponse(getSupportedDomainsQueryResponse)

            delay(100L)
            lookupPublicInfoHolder.callback shouldNotBe null
            lookupPublicInfoHolder.callback?.onResponse(lookupPublicInfoResponse)

            deferredResult.await()

            verify(mockAppSyncClient).query(any<GetEmailDomainsQuery>())
            verify(mockAppSyncClient).query(any<LookupEmailAddressesPublicInfoQuery>())
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

            verify(mockAppSyncClient, times(4)).query(any<GetEmailDomainsQuery>())
            verify(mockAppSyncClient, times(4)).query(any<GetEmailConfigQuery>())
            verify(mockAppSyncClient, times(4)).mutate(any<SendEmailMessageMutation>())
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

            verify(mockAppSyncClient, times(4)).query(any<GetEmailDomainsQuery>())
            verify(mockAppSyncClient, times(4)).query(any<GetEmailConfigQuery>())
            verify(mockAppSyncClient, times(4)).query(any<LookupEmailAddressesPublicInfoQuery>())
            verify(mockAppSyncClient, times(4)).mutate(any<SendEncryptedEmailMessageMutation>())
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
            val getConfigDataQueryResult by before {
                GetEmailConfigQuery.GetEmailConfig(
                    "typeName",
                    GetEmailConfigQuery.GetEmailConfig.Fragments(
                        EmailConfigurationData(
                            "typename",
                            10,
                            5,
                            200,
                            limit,
                            5,
                            10,
                        ),
                    ),
                )
            }

            val getConfigDataQueryResponse by before {
                Response.builder<GetEmailConfigQuery.Data>(GetEmailConfigQuery())
                    .data(GetEmailConfigQuery.Data(getConfigDataQueryResult))
                    .build()
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
            val lookupPublicInfoResponse by before {
                Response.builder<LookupEmailAddressesPublicInfoQuery.Data>(
                    LookupEmailAddressesPublicInfoQuery(lookupPublicInfoInput),
                )
                    .data(LookupEmailAddressesPublicInfoQuery.Data(lookupPublicInfoQueryItemsResult))
                    .build()
            }

            getSupportedDomainsHolder.callback = null
            getConfigDataHolder.callback = null
            lookupPublicInfoHolder.callback = null
            sendHolder.callback = null
            sendEncryptedHolder.callback = null

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
                shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageSizeLimitExceededException> {
                    client.sendEmailMessage(input)
                }
            }
            deferredResult.start()

            delay(100L)
            getSupportedDomainsHolder.callback shouldNotBe null
            getSupportedDomainsHolder.callback?.onResponse(getSupportedDomainsQueryResponse)

            delay(100L)
            lookupPublicInfoHolder.callback shouldNotBe null
            lookupPublicInfoHolder.callback?.onResponse(lookupPublicInfoResponse)

            delay(100L)
            getConfigDataHolder.callback shouldNotBe null
            getConfigDataHolder.callback?.onResponse(getConfigDataQueryResponse)

            deferredResult.await()

            verify(mockAppSyncClient).query(any<GetEmailDomainsQuery>())
            verify(mockAppSyncClient).query(any<GetEmailConfigQuery>())
            verify(mockAppSyncClient).query(any<LookupEmailAddressesPublicInfoQuery>())
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
            val getConfigDataQueryResult by before {
                GetEmailConfigQuery.GetEmailConfig(
                    "typeName",
                    GetEmailConfigQuery.GetEmailConfig.Fragments(
                        EmailConfigurationData(
                            "typename",
                            10,
                            5,
                            200,
                            limit,
                            5,
                            10,
                        ),
                    ),
                )
            }

            val getConfigDataQueryResponse by before {
                Response.builder<GetEmailConfigQuery.Data>(GetEmailConfigQuery())
                    .data(GetEmailConfigQuery.Data(getConfigDataQueryResult))
                    .build()
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

            getSupportedDomainsHolder.callback = null
            getConfigDataHolder.callback = null
            lookupPublicInfoHolder.callback = null
            sendHolder.callback = null
            sendEncryptedHolder.callback = null

            val input = SendEmailMessageInput(
                "senderEmailAddressId",
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

            delay(100L)
            getSupportedDomainsHolder.callback shouldNotBe null
            getSupportedDomainsHolder.callback?.onResponse(getSupportedDomainsQueryResponse)

            delay(100L)
            getConfigDataHolder.callback shouldNotBe null
            getConfigDataHolder.callback?.onResponse(getConfigDataQueryResponse)

            deferredResult.await()

            verify(mockAppSyncClient).query(any<GetEmailDomainsQuery>())
            verify(mockAppSyncClient).query(any<GetEmailConfigQuery>())
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
            getSupportedDomainsHolder.callback = null
            getConfigDataHolder.callback = null
            lookupPublicInfoHolder.callback = null
            sendHolder.callback = null
            sendEncryptedHolder.callback = null

            val errorSendResponse by before {
                val error = com.apollographql.apollo.api.Error(
                    "mock",
                    emptyList(),
                    mapOf("errorType" to apolloError),
                )
                Response.builder<SendEmailMessageMutation.Data>(SendEmailMessageMutation(sendInput))
                    .errors(listOf(error))
                    .build()
            }

            val input = SendEmailMessageInput(
                "senderEmailAddressId",
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
                shouldThrow<T> {
                    client.sendEmailMessage(input)
                }
            }
            deferredResult.start()

            delay(100L)
            getSupportedDomainsHolder.callback shouldNotBe null
            getSupportedDomainsHolder.callback?.onResponse(getSupportedDomainsQueryResponse)

            delay(100L)
            getConfigDataHolder.callback shouldNotBe null
            getConfigDataHolder.callback?.onResponse(getConfigDataQueryResponse)

            delay(100L)
            sendHolder.callback shouldNotBe null
            sendHolder.callback?.onResponse(errorSendResponse)

            deferredResult.await()
        }

    private inline fun <reified T : Exception> testEncryptedSendException(apolloError: String) =
        runTest {
            val lookupPublicInfoResponse by before {
                Response.builder<LookupEmailAddressesPublicInfoQuery.Data>(
                    LookupEmailAddressesPublicInfoQuery(lookupPublicInfoInput),
                )
                    .data(LookupEmailAddressesPublicInfoQuery.Data(lookupPublicInfoQueryItemsResult))
                    .build()
            }

            getSupportedDomainsHolder.callback = null
            getConfigDataHolder.callback = null
            lookupPublicInfoHolder.callback = null
            sendHolder.callback = null
            sendEncryptedHolder.callback = null

            val errorSendResponse by before {
                val error = com.apollographql.apollo.api.Error(
                    "mock",
                    emptyList(),
                    mapOf("errorType" to apolloError),
                )
                Response.builder<SendEncryptedEmailMessageMutation.Data>(
                    SendEncryptedEmailMessageMutation(sendEncryptedInput),
                )
                    .errors(listOf(error))
                    .build()
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

            delay(100L)
            getSupportedDomainsHolder.callback shouldNotBe null
            getSupportedDomainsHolder.callback?.onResponse(getSupportedDomainsQueryResponse)

            delay(100L)
            lookupPublicInfoHolder.callback shouldNotBe null
            lookupPublicInfoHolder.callback?.onResponse(lookupPublicInfoResponse)

            delay(100L)
            getConfigDataHolder.callback shouldNotBe null
            getConfigDataHolder.callback?.onResponse(getConfigDataQueryResponse)

            delay(100L)
            sendEncryptedHolder.callback shouldNotBe null
            sendEncryptedHolder.callback?.onResponse(errorSendResponse)

            deferredResult.await()
        }
}
