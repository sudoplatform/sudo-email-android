/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
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
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.CacheGetResult
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.EmailMessageBodyCache
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import io.kotlintest.shouldBe
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
 * Property-based tests for [RetrieveAndDecodeEmailMessageUseCase] cache integration.
 */
@RunWith(RobolectricTestRunner::class)
class RetrieveAndDecodeEmailMessageUseCasePropertyTest : BaseTests() {
    private val rfc822Data = DataFactory.unsealedHeaderDetailsString.toByteArray()
    private val sealedRfc822Data = mockSeal("sealed RFC822 data")
    private val mockRfc822Metadata: ObjectMetadata = ObjectMetadata()

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

    private val mockEmailMessageBodyCache by before {
        mock<EmailMessageBodyCache>()
    }

    private val useCase by before {
        RetrieveAndDecodeEmailMessageUseCase(
            s3EmailClient = mockS3EmailClient,
            serviceKeyManager = mockServiceKeyManager,
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
                Arb.bind(
                    Arb.uuid().map { it.toString() },
                    Arb.uuid().map { it.toString() },
                    Arb.uuid().map { it.toString() },
                    Arb.of("sudoplatform-crypto,sudoplatform-binary-data", "sudoplatform-binary-data,sudoplatform-crypto", null),
                ) { messageId, sudoId, emailAddressId, contentEncoding ->
                    Triple(
                        messageId,
                        CacheGetResult(messageId, sudoId, emailAddressId, sealedRfc822Data.toByteArray(), contentEncoding),
                        emailAddressId,
                    )
                },
            ) { (messageId, cacheResult, emailAddressId) ->
                clearInvocations(mockS3EmailClient, mockKeyManager, mockEmailMessageBodyCache)

                // Stub cache to return a hit
                mockEmailMessageBodyCache.stub {
                    onBlocking { get(messageId) } doReturn cacheResult
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

                val result = useCase.execute(emailMessage)

                // Result should be the decoded data (unsealing still happens)
                result shouldBe rfc822Data

                // S3 should NOT have been called
                verifyNoInteractions(mockS3EmailClient)
            }
        }

    // Feature: pemc-1738, Property 9: Zero cache size disables caching
    @Test
    fun `Property 9 - when cache get returns null, S3 is always called`() =
        runTest {
            checkAll(
                100,
                Arb.uuid().map { it.toString() },
            ) { messageId ->
                clearInvocations(mockS3EmailClient, mockKeyManager, mockEmailMessageBodyCache)

                // Stub cache to return null (simulates cache disabled / miss)
                mockEmailMessageBodyCache.stub {
                    onBlocking { get(messageId) } doReturn null
                }

                val emailMessage =
                    EntityDataFactory.getSealedEmailMessageEntity(
                        id = messageId,
                        emailAddressId = "addr-1",
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

                val result = useCase.execute(emailMessage)

                result shouldBe rfc822Data

                // S3 SHOULD have been called
                verify(mockS3EmailClient).download(any(), any())
                verify(mockS3EmailClient).getObjectMetadata(any(), any())
            }
        }
}
