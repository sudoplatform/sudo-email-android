/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.secure.types.SecureEmailAttachmentType
import com.sudoplatform.sudoemail.secure.types.SecurePackage
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudoemail.util.SimplifiedEmailMessage
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.string.shouldMatch
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
 * Test the correct operation of [SudoEmailClient.createDraftEmailMessage]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailCreateDraftEmailMessageTest : BaseTests() {
    private val uuidRegex = Regex("^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}\$")

    private val invalidEmailAttachment =
        EmailAttachment(
            "fileName.js", // Prohibited type
            "contentId",
            "mimeType",
            false,
            ByteArray(1),
        )

    private val secureKeyAttachment =
        EmailAttachment(
            SecureEmailAttachmentType.KEY_EXCHANGE.fileName,
            SecureEmailAttachmentType.KEY_EXCHANGE.contentId,
            SecureEmailAttachmentType.KEY_EXCHANGE.mimeType,
            true,
            ByteArray(1),
        )

    private val secureBodyAttachment =
        EmailAttachment(
            SecureEmailAttachmentType.BODY.fileName,
            SecureEmailAttachmentType.BODY.contentId,
            SecureEmailAttachmentType.BODY.mimeType,
            true,
            ByteArray(1),
        )

    private val outNetworkEmailMessage =
        SimplifiedEmailMessage(
            listOf("from@internal.com"),
            listOf("to@external.com"),
            listOf("cc@external.com"),
            listOf("bcc@external.com"),
            "email message subject",
            "email message body",
            false,
        )

    private val messageWithInvalidEmailAttachment =
        SimplifiedEmailMessage(
            listOf("from@internal.com"),
            listOf("to@external.com"),
            listOf("cc@external.com"),
            listOf("bcc@external.com"),
            "email message subject",
            "email message body",
            false,
            attachments = listOf(invalidEmailAttachment),
        )

    private val inNetworkEmailMessage =
        SimplifiedEmailMessage(
            listOf("from@internal.com"),
            listOf("to@internal.com"),
            emptyList(),
            emptyList(),
            "email message subject",
            "email message body",
            false,
        )

    private val inNetworkMessageWithDisplayName =
        SimplifiedEmailMessage(
            listOf("from@internal.com"),
            listOf("Recipient Name <to@internal.com>"),
            emptyList(),
            emptyList(),
            "email message subject",
            "email message body",
            false,
        )

    private val encodeToInternetMessageDataResponse by before {
        ByteArray(42)
    }

    private val domains = listOf("foo.com", "internal.com")
    private val getConfiguredDomainsQueryResponse by before {
        DataFactory.getConfiguredEmailDomainsQueryResponse(domains)
    }

    private val getEmailConfigQueryResponse by before {
        DataFactory.getEmailConfigQueryResponse()
    }

    private val lookupEmailAddressesPublicInfoQueryResponse by before {
        DataFactory.lookupEmailAddressPublicInfoQueryResponse(
            listOf(
                DataFactory.getEmailAddressPublicInfo("from@internal.com"),
                DataFactory.getEmailAddressPublicInfo("to@internal.com"),
            ),
        )
    }

    private val input by before {
        CreateDraftEmailMessageInput(
            "rfc822data".toByteArray(),
            "senderEmailAddressId",
        )
    }

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { encryptWithSymmetricKey(anyString(), any()) } doReturn ByteArray(42)
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { getCurrentSymmetricKeyId() } doReturn "symmetricKeyId"
            on { generateNewCurrentSymmetricKey() } doReturn "newSymmetricKeyId"
        }
    }

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                getEmailAddressQuery(
                    any(),
                )
            } doAnswer {
                DataFactory.getEmailAddressQueryResponse()
            }
            onBlocking {
                getEmailConfigQuery()
            } doAnswer {
                getEmailConfigQueryResponse
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
        }
    }

    private val mockUploadResponse by before {
        "42"
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking {
                upload(
                    any(),
                    any(),
                    anyOrNull(),
                )
            } doReturn mockUploadResponse
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
            } doReturn encodeToInternetMessageDataResponse
            on { parseInternetMessageData(any()) } doReturn outNetworkEmailMessage
        }
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on { sealString(any(), any()) } doReturn "sealString".toByteArray()
        }
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>().stub {
            onBlocking { encrypt(any<ByteArray>(), any()) } doReturn
                SecurePackage(
                    setOf(
                        secureKeyAttachment,
                    ),
                    secureBodyAttachment,
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
            mockServiceKeyManager,
            mockApiClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `createDraftEmailMessage() should log and throw an error if send address not found`() =
        runTest {
            val error =
                GraphQLResponse.Error(
                    "mock",
                    null,
                    null,
                    mapOf("errorType" to "AddressNotFound"),
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailAddressQuery(
                        any(),
                    )
                } doAnswer {
                    GraphQLResponse(null, listOf(error))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                        client.createDraftEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
        }

    @Test
    fun `createDraftEmailMessage() should log and throw InvalidMessageContentException if message includes invalid attachment type`() =
        runTest {
            mockEmailMessageProcessor.stub {
                onBlocking {
                    parseInternetMessageData(any())
                } doReturn messageWithInvalidEmailAttachment
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
                        client.createDraftEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockEmailMessageProcessor).parseInternetMessageData(any())
        }

    @Test
    fun `createDraftEmailMessage() should log and throw an error if s3 upload errors`() =
        runTest {
            val error = CancellationException("Unknown exception")

            mockS3Client.stub {
                onBlocking {
                    upload(
                        any(),
                        any(),
                        anyOrNull(),
                    )
                } doThrow error
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<CancellationException> {
                        client.createDraftEmailMessage(input)
                    }
                }

            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockEmailMessageProcessor).parseInternetMessageData(any())
            verify(mockSealingService).sealString(
                check {
                    it shouldBe "symmetricKeyId"
                },
                check {
                    it shouldBe "rfc822data".toByteArray()
                },
            )
            verify(mockS3Client).upload(
                check {
                    it shouldNotBe null
                },
                check {
                    it shouldContain input.senderEmailAddressId
                },
                check {
                    it shouldNotBe null
                },
            )
        }

    @Test
    fun `createDraftEmailMessage() should return uuid on success`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.createDraftEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()
            result shouldMatch uuidRegex

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockEmailMessageProcessor).parseInternetMessageData(any())
            verify(mockSealingService).sealString("symmetricKeyId", input.rfc822Data)
            verify(mockS3Client).upload(
                check {
                    it shouldNotBe null
                },
                check {
                    it shouldContain input.senderEmailAddressId
                },
                check {
                    it shouldNotBe null
                },
            )
        }

    // E2EE path
    @Test
    fun `createDraftEmailMessage() should throw LimitExceededException if too many recipients`() =
        runTest {
            val numRecipients =
                DataFactory
                    .getEmailConfigQueryResponse()
                    .data.getEmailConfig.emailConfigurationData.encryptedEmailMessageRecipientsLimit + 1
            val inNetworkEmailMessage =
                SimplifiedEmailMessage(
                    listOf("from@internal.com"),
                    // Add numRecipients to 'to' field
                    to = List(numRecipients) { "to-$it@internal.com" },
                    emptyList(),
                    emptyList(),
                    "email message subject",
                    "email message body",
                    false,
                )

            mockEmailMessageProcessor.stub {
                onBlocking {
                    parseInternetMessageData(any())
                } doReturn inNetworkEmailMessage
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.LimitExceededException> {
                        client.createDraftEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockEmailMessageProcessor).parseInternetMessageData(any())
        }

    @Test
    fun `createDraftEmailMessage() should throw InNetworkAddressNotFoundException if recipient not in network`() =
        runTest {
            mockEmailMessageProcessor.stub {
                onBlocking {
                    parseInternetMessageData(any())
                } doReturn inNetworkEmailMessage
            }
            mockApiClient.stub {
                onBlocking {
                    lookupEmailAddressesPublicInfoQuery(
                        any(),
                    )
                } doAnswer {
                    // Return empty list of addresses to simulate no in-network recipients
                    DataFactory.lookupEmailAddressPublicInfoQueryResponse(emptyList())
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.InNetworkAddressNotFoundException> {
                        client.createDraftEmailMessage(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockEmailMessageProcessor).parseInternetMessageData(any())
            verify(mockEmailMessageProcessor, times(1)).encodeToInternetMessageData(
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
            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(
                any(),
            )
        }

    @Test
    fun `createDraftEmailMessage() should return uuid on success with E2EE message`() =
        runTest {
            mockEmailMessageProcessor.stub {
                onBlocking {
                    parseInternetMessageData(any())
                } doReturn inNetworkEmailMessage
            }

            val result = client.createDraftEmailMessage(input)
            result shouldMatch uuidRegex

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockEmailMessageProcessor).parseInternetMessageData(any())
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
                anyOrNull(),
                anyOrNull(),
            )
            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(
                any(),
            )
            verify(mockEmailCryptoService).encrypt(
                any(),
                any(),
            )
            verify(mockSealingService).sealString("symmetricKeyId", encodeToInternetMessageDataResponse)
            verify(mockS3Client).upload(
                check {
                    it shouldNotBe null
                },
                check {
                    it shouldContain input.senderEmailAddressId
                },
                check {
                    it shouldNotBe null
                },
            )
        }

    @Test
    fun `createDraftEmailMessage() should handle display names in in-network addresses`() =
        runTest {
            mockEmailMessageProcessor.stub {
                onBlocking {
                    parseInternetMessageData(any())
                } doReturn inNetworkMessageWithDisplayName
            }

            val result = client.createDraftEmailMessage(input)
            result shouldMatch uuidRegex

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockApiClient).getEmailConfigQuery()
            verify(mockApiClient).getConfiguredEmailDomainsQuery()
            verify(mockEmailMessageProcessor).parseInternetMessageData(any())
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
                anyOrNull(),
                anyOrNull(),
            )
            verify(mockApiClient).lookupEmailAddressesPublicInfoQuery(
                check {
                    it.emailAddresses[0] shouldBe "to@internal.com"
                },
            )
            verify(mockEmailCryptoService).encrypt(
                any(),
                any(),
            )
            verify(mockSealingService).sealString("symmetricKeyId", encodeToInternetMessageDataResponse)
            verify(mockS3Client).upload(
                check {
                    it shouldNotBe null
                },
                check {
                    it shouldContain input.senderEmailAddressId
                },
                check {
                    it shouldNotBe null
                },
            )
        }
}
