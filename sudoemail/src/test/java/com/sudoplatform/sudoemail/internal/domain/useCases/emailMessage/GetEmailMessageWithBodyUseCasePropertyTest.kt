/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.amazonaws.services.s3.model.ObjectMetadata
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SimplifiedEmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.CacheGetResult
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.EmailMessageBodyCache
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import io.kotest.property.Arb
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Property-based tests for [GetEmailMessageWithBodyUseCase] cache integration.
 */
@RunWith(RobolectricTestRunner::class)
class GetEmailMessageWithBodyUseCasePropertyTest : BaseTests() {
    private val rfc822Data = DataFactory.unsealedHeaderDetailsString.toByteArray()
    private val sealedRfc822Data = mockSeal("sealed RFC822 data")
    private val mockRfc822Metadata: ObjectMetadata = ObjectMetadata()
    private val messageBody = "This is the message body"

    private val mockSimplifiedEmailMessage by before {
        SimplifiedEmailMessageEntity(
            from = listOf(mockSenderAddress),
            to = listOf(mockExternalRecipientAddress),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Test Subject",
            body = messageBody,
            isHtml = false,
            attachments = emptyList(),
            inlineAttachments = emptyList(),
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
            } doReturn rfc822Data
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

    private val mockS3EmailClient by before {
        mock<S3Client>().stub {
            onBlocking { download(any(), any()) } doReturn sealedRfc822Data.toByteArray()
            onBlocking { getObjectMetadata(any(), any()) } doReturn
                mockRfc822Metadata.apply {
                    contentEncoding = "${StringConstants.BINARY_DATA_CONTENT_ENCODING},${StringConstants.CRYPTO_CONTENT_ENCODING}"
                }
        }
    }

    private val mockEmailMessageDataProcessor by before {
        mock<EmailMessageDataProcessor>().stub {
            on { parseInternetMessageData(any()) } doReturn mockSimplifiedEmailMessage
        }
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>()
    }

    private val mockEmailMessageService by before {
        mock<EmailMessageService>()
    }

    private val mockEmailMessageBodyCache by before {
        mock<EmailMessageBodyCache>()
    }

    private val useCase by before {
        GetEmailMessageWithBodyUseCase(
            emailMessageService = mockEmailMessageService,
            s3EmailClient = mockS3EmailClient,
            serviceKeyManager = mockServiceKeyManager,
            emailMessageDataProcessor = mockEmailMessageDataProcessor,
            emailCryptoService = mockEmailCryptoService,
            logger = mockLogger,
            emailMessageBodyCache = mockEmailMessageBodyCache,
        )
    }

    // Feature: pemc-1738, Property 1: Cache hit returns correct blob and avoids S3
    @Test
    fun `Property 1 - cache hit returns correct blob and avoids S3 for any message`() =
        runTest {
            checkAll(
                100,
                Arb.uuid().map { it.toString() },
                Arb.uuid().map { it.toString() },
                Arb.uuid().map { it.toString() },
                Arb.of(
                    "sudoplatform-crypto,sudoplatform-binary-data",
                    "sudoplatform-binary-data,sudoplatform-crypto",
                    null,
                ),
            ) { messageId, sudoId, emailAddressId, contentEncoding ->
                clearInvocations(
                    mockS3EmailClient,
                    mockKeyManager,
                    mockEmailMessageBodyCache,
                    mockEmailMessageService,
                    mockEmailMessageDataProcessor,
                )

                val cacheResult = CacheGetResult(messageId, sudoId, emailAddressId, sealedRfc822Data.toByteArray(), contentEncoding)

                // Stub cache to return a hit
                mockEmailMessageBodyCache.stub {
                    onBlocking { get(messageId) } doReturn cacheResult
                }

                val emailMessage =
                    EntityDataFactory.getSealedEmailMessageEntity(
                        id = messageId,
                        emailAddressId = emailAddressId,
                        owners = listOf(OwnerEntity(id = sudoId, issuer = "sudoplatform.sudoservice")),
                        rfc822Header =
                            SealedAttributeEntity(
                                keyId = mockKeyId,
                                algorithm = mockAlgorithm,
                                base64EncodedSealedData = sealedRfc822Data,
                                plainTextType = "string",
                            ),
                        encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
                    )

                // Stub email message service to return the entity
                mockEmailMessageService.stub {
                    onBlocking { get(any()) } doReturn emailMessage
                }

                val input =
                    GetEmailMessageWithBodyUseCaseInput(
                        id = messageId,
                        emailAddressId = emailAddressId,
                    )

                val result = useCase.execute(input)

                // Result should be non-null (message was found and decoded)
                result shouldNotBe null

                // S3 should NOT have been called
                verifyNoInteractions(mockS3EmailClient)
            }
        }

    // Feature: pemc-1738, Property 9: Zero cache size disables caching / cache miss always calls S3
    @Test
    fun `Property 9 - when cache get returns null, S3 is always called`() =
        runTest {
            checkAll(
                100,
                Arb.uuid().map { it.toString() },
            ) { messageId ->
                clearInvocations(
                    mockS3EmailClient,
                    mockKeyManager,
                    mockEmailMessageBodyCache,
                    mockEmailMessageService,
                    mockEmailMessageDataProcessor,
                )

                val emailAddressId = "addr-1"

                // Stub cache to return null (simulates cache disabled / miss)
                mockEmailMessageBodyCache.stub {
                    onBlocking { get(messageId) } doReturn null
                }

                val emailMessage =
                    EntityDataFactory.getSealedEmailMessageEntity(
                        id = messageId,
                        emailAddressId = emailAddressId,
                        owners = listOf(OwnerEntity(id = "sudoId", issuer = "sudoplatform.sudoservice")),
                        rfc822Header =
                            SealedAttributeEntity(
                                keyId = mockKeyId,
                                algorithm = mockAlgorithm,
                                base64EncodedSealedData = sealedRfc822Data,
                                plainTextType = "string",
                            ),
                        encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
                    )

                // Stub email message service to return the entity
                mockEmailMessageService.stub {
                    onBlocking { get(any()) } doReturn emailMessage
                }

                val input =
                    GetEmailMessageWithBodyUseCaseInput(
                        id = messageId,
                        emailAddressId = emailAddressId,
                    )

                val result = useCase.execute(input)

                result shouldNotBe null

                // S3 SHOULD have been called
                verify(mockS3EmailClient).download(any(), any())
                verify(mockS3EmailClient).getObjectMetadata(any(), any())
            }
        }
}
