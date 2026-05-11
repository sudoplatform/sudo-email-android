/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
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
import com.sudoplatform.sudoemail.s3.S3Exception
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import io.kotlintest.shouldBe
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
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Test the correct operation of [RetrieveAndDecodeEmailMessageUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class RetrieveAndDecodeEmailMessageUseCaseTest : BaseTests() {
    private val rfc822Data = DataFactory.unsealedHeaderDetailsString.toByteArray()
    private val sealedRfc822Data = mockSeal("sealed RFC822 data")
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
        mock<EmailMessageBodyCache>().stub {
            onBlocking { get(any()) } doReturn null
            onBlocking { getCacheSizeLimit() } doReturn 300L * 1024 * 1024
        }
    }

    private val useCase by before {
        RetrieveAndDecodeEmailMessageUseCase(
            s3EmailClient = mockS3EmailClient,
            serviceKeyManager = mockServiceKeyManager,
            logger = mockLogger,
            emailMessageBodyCache = mockEmailMessageBodyCache,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockS3EmailClient,
            mockKeyManager,
            mockEmailMessageBodyCache,
        )
    }

    @Test
    fun `execute() should retrieve and decode email message with default content encoding`() =
        runTest {
            val result = useCase.execute(emailMessageEntity)

            result shouldBe rfc822Data

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe
                        "${StringConstants.BINARY_DATA_CONTENT_ENCODING},${StringConstants.CRYPTO_CONTENT_ENCODING}"
                },
            )
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should retrieve and decode with explicit crypto and binary encoding`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = "${StringConstants.CRYPTO_CONTENT_ENCODING},${StringConstants.BINARY_DATA_CONTENT_ENCODING}"
                    }
            }

            val result = useCase.execute(emailMessageEntity)

            result shouldBe rfc822Data

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe
                        "${StringConstants.CRYPTO_CONTENT_ENCODING},${StringConstants.BINARY_DATA_CONTENT_ENCODING}"
                },
            )
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should retrieve and decode with compression encoding`() =
        runTest {
            // Create compressed data
            val compressedData =
                ByteArrayOutputStream().use { byteStream ->
                    GZIPOutputStream(byteStream).use { gzipStream ->
                        gzipStream.write(rfc822Data)
                    }
                    Base64.encode(byteStream.toByteArray())
                }

            mockS3EmailClient.stub {
                onBlocking { download(any(), any()) } doReturn compressedData
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = StringConstants.COMPRESSION_CONTENT_ENCODING
                    }
            }

            val result = useCase.execute(emailMessageEntity)

            result shouldBe rfc822Data

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe compressedData
                    input.contentEncoding shouldBe StringConstants.COMPRESSION_CONTENT_ENCODING
                },
            )
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
        }

    @Test
    fun `execute() should retrieve and decode with compression then crypto encoding`() =
        runTest {
            // Create compressed data
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos)
                .bufferedWriter(Charsets.UTF_8)
                .use { it.write(DataFactory.unsealedHeaderDetailsString) }
            val compressedBytes = bos.toByteArray()
            val encodedBytes = Base64.encode(compressedBytes)

            // Mock decompression then decryption
            mockKeyManager.stub {
                on {
                    decryptWithSymmetricKey(
                        any<ByteArray>(),
                        any<ByteArray>(),
                    )
                } doReturn encodedBytes
            }

            mockS3EmailClient.stub {
                onBlocking { download(any(), any()) } doReturn sealedRfc822Data.toByteArray()
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = "${StringConstants.COMPRESSION_CONTENT_ENCODING},${StringConstants.CRYPTO_CONTENT_ENCODING}"
                    }
            }

            val result = useCase.execute(emailMessageEntity)

            result shouldBe rfc822Data

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe
                        "${StringConstants.COMPRESSION_CONTENT_ENCODING},${StringConstants.CRYPTO_CONTENT_ENCODING}"
                },
            )
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should handle binary encoding as no-op`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { download(any(), any()) } doReturn rfc822Data
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = StringConstants.BINARY_DATA_CONTENT_ENCODING
                    }
            }

            val result = useCase.execute(emailMessageEntity)

            result shouldBe rfc822Data

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe rfc822Data
                    input.contentEncoding shouldBe StringConstants.BINARY_DATA_CONTENT_ENCODING
                },
            )
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
        }

    @Test
    fun `execute() should handle null content encoding with default values`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = null
                    }
            }

            val result = useCase.execute(emailMessageEntity)

            result shouldBe rfc822Data

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe null
                },
            )
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should throw UnsealingException for invalid content encoding`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = "invalid-encoding"
                    }
            }

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                    useCase.execute(emailMessageEntity)
                }

            exception.message shouldBe "Invalid Content-Encoding value invalid-encoding"

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe "invalid-encoding"
                },
            )
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
        }

    @Test
    fun `execute() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { download(any(), any()) } doThrow NotAuthorizedException("Not authorized")
            }

            shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
                useCase.execute(emailMessageEntity)
            }

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
        }

    @Test
    fun `execute() should throw when S3 download fails`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { download(any(), any()) } doThrow
                    S3Exception.DownloadException(
                        StringConstants.S3_NOT_FOUND_ERROR_CODE,
                        AmazonS3Exception(StringConstants.S3_NOT_FOUND_ERROR_CODE),
                    )
            }

            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                useCase.execute(emailMessageEntity)
            }

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
        }

    @Test
    fun `execute() should throw when getObjectMetadata fails`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { getObjectMetadata(any(), any()) } doThrow RuntimeException("Metadata retrieval failed")
            }

            shouldThrow<RuntimeException> {
                useCase.execute(emailMessageEntity)
            }

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
        }

    @Test
    fun `execute() should throw when unsealing fails`() =
        runTest {
            mockKeyManager.stub {
                on { decryptWithPrivateKey(anyString(), any(), any()) } doThrow RuntimeException("Decryption failed")
            }

            shouldThrow<RuntimeException> {
                useCase.execute(emailMessageEntity)
            }

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe
                        "${StringConstants.BINARY_DATA_CONTENT_ENCODING},${StringConstants.CRYPTO_CONTENT_ENCODING}"
                },
            )
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
        }

    @Test
    fun `execute() should construct correct S3 key from email message entity`() =
        runTest {
            val customEmailAddressId = "customEmailAddressId"
            val customEmailMessageId = "customEmailMessageId"
            val customKeyId = "customKeyId"

            val customEmailMessage =
                EntityDataFactory.getSealedEmailMessageEntity(
                    id = customEmailMessageId,
                    emailAddressId = customEmailAddressId,
                    rfc822Header =
                        SealedAttributeEntity(
                            keyId = customKeyId,
                            algorithm = mockAlgorithm,
                            base64EncodedSealedData = sealedRfc822Data,
                            plainTextType = "string",
                        ),
                    encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
                )

            val expectedS3Key = customEmailMessage.rfc822DataAttributes.key

            useCase.execute(customEmailMessage)

            verify(mockEmailMessageBodyCache).get(customEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe customEmailMessageId
                    input.emailAddressId shouldBe customEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe
                        "${StringConstants.BINARY_DATA_CONTENT_ENCODING},${StringConstants.CRYPTO_CONTENT_ENCODING}"
                },
            )
            verify(mockS3EmailClient).download(expectedS3Key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(expectedS3Key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should handle compression with whitespace in content encoding`() =
        runTest {
            // Create compressed data
            val compressedData =
                ByteArrayOutputStream().use { byteStream ->
                    GZIPOutputStream(byteStream).use { gzipStream ->
                        gzipStream.write(rfc822Data)
                    }
                    Base64.encode(byteStream.toByteArray())
                }

            mockS3EmailClient.stub {
                onBlocking { download(any(), any()) } doReturn compressedData
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = " ${StringConstants.COMPRESSION_CONTENT_ENCODING} "
                    }
            }

            val result = useCase.execute(emailMessageEntity)

            result shouldBe rfc822Data

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe compressedData
                    input.contentEncoding shouldBe " ${StringConstants.COMPRESSION_CONTENT_ENCODING} "
                },
            )
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
        }

    @Test
    fun `execute() should handle uppercase content encoding values`() =
        runTest {
            mockS3EmailClient.stub {
                onBlocking { getObjectMetadata(any(), any()) } doReturn
                    mockRfc822Metadata.apply {
                        contentEncoding = "SUDOPLATFORM-CRYPTO,SUDOPLATFORM-BINARY-DATA"
                    }
            }

            val result = useCase.execute(emailMessageEntity)

            result shouldBe rfc822Data

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                    input.contentEncoding shouldBe "SUDOPLATFORM-CRYPTO,SUDOPLATFORM-BINARY-DATA"
                },
            )
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    // --- Cache integration tests ---

    @Test
    fun `execute() should return cached blob without calling S3 on cache hit`() =
        runTest {
            val cachedBlob = sealedRfc822Data.toByteArray()
            val contentEncoding = "${StringConstants.BINARY_DATA_CONTENT_ENCODING},${StringConstants.CRYPTO_CONTENT_ENCODING}"

            mockEmailMessageBodyCache.stub {
                onBlocking { get(any()) } doReturn
                    CacheGetResult(
                        messageId = mockEmailMessageId,
                        sudoId = "mockSudoId",
                        emailAddressId = mockEmailAddressId,
                        sealedBlob = cachedBlob,
                        contentEncoding = contentEncoding,
                    )
            }

            val result = useCase.execute(emailMessageEntity)

            result shouldBe rfc822Data

            // Cache was consulted
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)

            // S3 was NOT called
            verify(mockS3EmailClient, never()).download(any(), any())
            verify(mockS3EmailClient, never()).getObjectMetadata(any(), any())

            // Decryption still happened (unsealing occurs regardless of source)
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should call S3 and populate cache on cache miss`() =
        runTest {
            // Cache returns null (miss) — this is the default stub behaviour
            val result = useCase.execute(emailMessageEntity)

            result shouldBe rfc822Data

            // Cache was consulted
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)

            // S3 was called
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))

            // Cache was populated
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.messageId shouldBe mockEmailMessageId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.sealedBlob shouldBe sealedRfc822Data.toByteArray()
                },
            )

            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should fall back to S3 when cache get throws and re-populate cache`() =
        runTest {
            mockEmailMessageBodyCache.stub {
                onBlocking { get(any()) } doThrow RuntimeException("Cache corrupted")
            }

            val result = useCase.execute(emailMessageEntity)

            result shouldBe rfc822Data

            // Cache get was attempted
            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)

            // Fell back to S3
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))

            // Cache was re-populated
            verify(mockEmailMessageBodyCache).put(any())

            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should fall back to S3 when cache put throws without propagating error`() =
        runTest {
            mockEmailMessageBodyCache.stub {
                onBlocking { get(any()) } doReturn null
                onBlocking { put(any()) } doThrow RuntimeException("Cache write failed")
            }

            val result = useCase.execute(emailMessageEntity)

            // Should still succeed — cache put error is swallowed
            result shouldBe rfc822Data

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockS3EmailClient).download(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockS3EmailClient).getObjectMetadata(emailMessageEntity.rfc822DataAttributes.key, S3Client.KeyOptions(true))
            verify(mockEmailMessageBodyCache).put(any())
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should extract sudoId from owners with sudoplatform sudoservice issuer`() =
        runTest {
            val sudoId = "testSudoId"
            val emailMessageWithSudoOwner =
                EntityDataFactory.getSealedEmailMessageEntity(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                    owners =
                        listOf(
                            OwnerEntity(id = "accountOwner", issuer = "sudoplatform"),
                            OwnerEntity(id = sudoId, issuer = "sudoplatform.sudoservice"),
                        ),
                    rfc822Header =
                        SealedAttributeEntity(
                            keyId = mockKeyId,
                            algorithm = mockAlgorithm,
                            base64EncodedSealedData = sealedRfc822Data,
                            plainTextType = "string",
                        ),
                    encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
                )

            useCase.execute(emailMessageWithSudoOwner)

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.sudoId shouldBe sudoId
                    input.emailAddressId shouldBe mockEmailAddressId
                },
            )
            verify(mockS3EmailClient).download(any(), any())
            verify(mockS3EmailClient).getObjectMetadata(any(), any())
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should set sudoId to null when no sudoservice owner exists`() =
        runTest {
            val emailMessageWithoutSudoOwner =
                EntityDataFactory.getSealedEmailMessageEntity(
                    id = mockEmailMessageId,
                    emailAddressId = mockEmailAddressId,
                    owners =
                        listOf(
                            OwnerEntity(id = "accountOwner", issuer = "sudoplatform"),
                        ),
                    rfc822Header =
                        SealedAttributeEntity(
                            keyId = mockKeyId,
                            algorithm = mockAlgorithm,
                            base64EncodedSealedData = sealedRfc822Data,
                            plainTextType = "string",
                        ),
                    encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
                )

            useCase.execute(emailMessageWithoutSudoOwner)

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockEmailMessageBodyCache).put(
                check { input ->
                    input.sudoId shouldBe null
                },
            )
            verify(mockS3EmailClient).download(any(), any())
            verify(mockS3EmailClient).getObjectMetadata(any(), any())
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should preserve contentEncoding from cache hit`() =
        runTest {
            val cachedBlob = sealedRfc822Data.toByteArray()
            val contentEncoding = "${StringConstants.CRYPTO_CONTENT_ENCODING},${StringConstants.BINARY_DATA_CONTENT_ENCODING}"

            mockEmailMessageBodyCache.stub {
                onBlocking { get(any()) } doReturn
                    CacheGetResult(
                        messageId = mockEmailMessageId,
                        sudoId = null,
                        emailAddressId = mockEmailAddressId,
                        sealedBlob = cachedBlob,
                        contentEncoding = contentEncoding,
                    )
            }

            val result = useCase.execute(emailMessageEntity)

            result shouldBe rfc822Data

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockS3EmailClient, never()).download(any(), any())
            verify(mockS3EmailClient, never()).getObjectMetadata(any(), any())
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }

    @Test
    fun `execute() should use default content encoding when cache hit has null contentEncoding`() =
        runTest {
            val cachedBlob = sealedRfc822Data.toByteArray()

            mockEmailMessageBodyCache.stub {
                onBlocking { get(any()) } doReturn
                    CacheGetResult(
                        messageId = mockEmailMessageId,
                        sudoId = null,
                        emailAddressId = mockEmailAddressId,
                        sealedBlob = cachedBlob,
                        contentEncoding = null,
                    )
            }

            val result = useCase.execute(emailMessageEntity)

            // Default encoding is crypto + binary-data, which triggers unsealing
            result shouldBe rfc822Data

            verify(mockEmailMessageBodyCache).get(mockEmailMessageId)
            verify(mockS3EmailClient, never()).download(any(), any())
            verify(mockS3EmailClient, never()).getObjectMetadata(any(), any())
            verify(mockKeyManager).decryptWithPrivateKey(anyString(), any(), any())
            verify(mockKeyManager).decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>())
        }
}
