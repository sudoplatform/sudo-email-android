/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.mechanisms.Unsealer
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.KeyInfo
import com.sudoplatform.sudoemail.internal.domain.entities.common.KeyType
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageWithBodyEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.GetEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.CachePutInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.EmailMessageBodyCache
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.types.LEGACY_BODY_CONTENT_ID
import com.sudoplatform.sudoemail.secure.types.LEGACY_KEY_EXCHANGE_CONTENT_ID
import com.sudoplatform.sudoemail.secure.types.SecureEmailAttachmentType
import com.sudoplatform.sudoemail.secure.types.SecurePackage
import com.sudoplatform.sudologging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Input for the get email message with body use case.
 *
 * @property id [String] The ID of the email message.
 * @property emailAddressId [String] The email address ID associated with the message.
 */
internal data class GetEmailMessageWithBodyUseCaseInput(
    val id: String,
    val emailAddressId: String,
)

/**
 * Use case for retrieving an email message with its body content.
 *
 * This use case retrieves an email message and includes its decoded body and attachments.
 *
 * @property emailMessageService [EmailMessageService] Service for email message operations.
 * @property s3EmailClient [S3Client] Client for S3 email bucket operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property emailMessageDataProcessor [EmailMessageDataProcessor] Processor for email message data.
 * @property emailCryptoService [EmailCryptoService] Service for email cryptographic operations.
 * @property logger [Logger] Logger for debugging.
 */
internal class GetEmailMessageWithBodyUseCase(
    private val emailMessageService: EmailMessageService,
    private val s3EmailClient: S3Client,
    private val serviceKeyManager: ServiceKeyManager,
    private val emailMessageDataProcessor: EmailMessageDataProcessor,
    private val emailCryptoService: EmailCryptoService,
    private val logger: Logger,
    private val emailMessageBodyCache: EmailMessageBodyCache,
) {
    /**
     * Executes the get email message with body use case.
     *
     * @param input [GetEmailMessageWithBodyUseCaseInput] The input parameters.
     * @return [EmailMessageWithBodyEntity] The email message with body, or null if not found.
     * @throws SudoEmailClient.EmailMessageException.AuthenticationException if authentication fails.
     * @throws SudoEmailClient.EmailMessageException.UnsealingException if decryption fails.
     */
    suspend fun execute(input: GetEmailMessageWithBodyUseCaseInput): EmailMessageWithBodyEntity? {
        logger.debug("Getting email message with body for email message ID: ${input.id}")
        try {
            val sealedEmailMessage =
                emailMessageService
                    .get(
                        GetEmailMessageRequest(id = input.id),
                    )?.takeIf { it.emailAddressId == input.emailAddressId } ?: return null
            val s3Key = sealedEmailMessage.rfc822DataAttributes.key
            val sealedRfc822Data: ByteArray
            val contentEncodingValues: List<String>

            // Cache-first retrieval
            val cacheResult =
                try {
                    emailMessageBodyCache.get(sealedEmailMessage.id)
                } catch (e: Exception) {
                    logger.error("Cache get error, falling back to S3: ${e.message}")
                    null
                }

            if (cacheResult != null) {
                // Cache hit
                logger.debug("Cache hit for message: ${sealedEmailMessage.id}")
                sealedRfc822Data = cacheResult.sealedBlob
                contentEncodingValues =
                    (
                        if (cacheResult.contentEncoding != null) {
                            cacheResult.contentEncoding.split(',')
                        } else {
                            listOf(StringConstants.CRYPTO_CONTENT_ENCODING, StringConstants.BINARY_DATA_CONTENT_ENCODING)
                        }
                    ).reversed()
            } else {
                // Cache miss — download from S3
                logger.debug("Cache miss for message: ${sealedEmailMessage.id}")
                sealedRfc822Data = s3EmailClient.download(s3Key, S3Client.KeyOptions(isKeyCredentialled = true))
                val rfc822Metadata = s3EmailClient.getObjectMetadata(s3Key, S3Client.KeyOptions(isKeyCredentialled = true))
                contentEncodingValues =
                    (
                        if (rfc822Metadata.contentEncoding != null) {
                            rfc822Metadata.contentEncoding.split(',')
                        } else {
                            listOf(StringConstants.CRYPTO_CONTENT_ENCODING, StringConstants.BINARY_DATA_CONTENT_ENCODING)
                        }
                    ).reversed()

                // Populate cache (fire-and-forget style — errors are caught internally by the cache)
                val sudoOwner = sealedEmailMessage.owners.find { it.issuer == StringConstants.SUDO_SERVICE_ISSUER }
                try {
                    emailMessageBodyCache.put(
                        CachePutInput(
                            messageId = sealedEmailMessage.id,
                            sudoId = sudoOwner?.id,
                            emailAddressId = sealedEmailMessage.emailAddressId,
                            sealedBlob = sealedRfc822Data,
                            contentEncoding = rfc822Metadata.contentEncoding,
                        ),
                    )
                } catch (e: Exception) {
                    logger.error("Cache put error: ${e.message}")
                }
            }

            // Decode the sealed data
            var decodedBytes = sealedRfc822Data
            for (value in contentEncodingValues) {
                when (value.trim().lowercase()) {
                    StringConstants.COMPRESSION_CONTENT_ENCODING -> {
                        decodedBytes = Base64.decode(decodedBytes)
                        val unzippedInputStream =
                            GZIPInputStream(ByteArrayInputStream(decodedBytes))
                        unzippedInputStream.use {
                            decodedBytes =
                                withContext(Dispatchers.IO) {
                                    unzippedInputStream.readBytes()
                                }
                        }
                    }

                    StringConstants.CRYPTO_CONTENT_ENCODING -> {
                        val keyInfo =
                            KeyInfo(sealedEmailMessage.rfc822Header.keyId, KeyType.PRIVATE_KEY, sealedEmailMessage.rfc822Header.algorithm)
                        val unsealer = Unsealer(serviceKeyManager, keyInfo)
                        decodedBytes = unsealer.unsealBytes(sealedRfc822Data)
                    }

                    StringConstants.BINARY_DATA_CONTENT_ENCODING -> {} // no-op
                    else -> throw SudoEmailClient.EmailMessageException.UnsealingException("Invalid Content-Encoding value $value")
                }
            }

            var parsedMessage = emailMessageDataProcessor.parseInternetMessageData(decodedBytes)
            if (sealedEmailMessage.encryptionStatus == EncryptionStatusEntity.ENCRYPTED) {
                val keyAttachments =
                    parsedMessage.attachments.filter {
                        it.contentId.contains(SecureEmailAttachmentType.KEY_EXCHANGE.contentId) ||
                            it.contentId.contains(LEGACY_KEY_EXCHANGE_CONTENT_ID)
                    }
                if (keyAttachments.isEmpty()) {
                    throw SudoEmailClient.EmailMessageException.FailedException(
                        StringConstants.KEY_ATTACHMENTS_NOT_FOUND_ERROR_MSG,
                    )
                }
                val bodyAttachment =
                    parsedMessage.attachments.filter {
                        it.contentId.contains(SecureEmailAttachmentType.BODY.contentId) ||
                            it.contentId.contains(LEGACY_BODY_CONTENT_ID)
                    }
                if (bodyAttachment.isEmpty()) {
                    throw SudoEmailClient.EmailMessageException.FailedException(
                        StringConstants.BODY_ATTACHMENT_NOT_FOUND_ERROR_MSG,
                    )
                }
                val securePackage = SecurePackage(keyAttachments.toSet(), bodyAttachment.first())
                val unencryptedMessage = emailCryptoService.decrypt(securePackage)
                parsedMessage =
                    emailMessageDataProcessor.parseInternetMessageData(unencryptedMessage)
            }
            return EmailMessageWithBodyEntity(
                id = sealedEmailMessage.id,
                body = parsedMessage.body ?: "",
                isHtml = parsedMessage.isHtml,
                attachments = parsedMessage.attachments,
                inlineAttachments = parsedMessage.inlineAttachments,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMessageException.AuthenticationException(
                    cause = e,
                )
                else -> throw ErrorTransformer.interpretEmailMessageException(e)
            }
        }
    }
}
