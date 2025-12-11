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
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SealedEmailMessageEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudologging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Use case for retrieving and decoding an email message.
 *
 * This use case retrieves an email message from S3 and decodes it by reversing
 * the applied content encodings such as compression and encryption.
 *
 * @property s3EmailClient [S3Client] Client for S3 email bucket operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property logger [Logger] Logger for debugging.
 */
internal class RetrieveAndDecodeEmailMessageUseCase(
    private val s3EmailClient: S3Client,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the retrieve and decode email message use case.
     *
     * @param emailMessage [SealedEmailMessageEntity] The sealed email message entity.
     * @return [ByteArray] The decoded email message data.
     * @throws SudoEmailClient.EmailMessageException.AuthenticationException if authentication fails.
     * @throws SudoEmailClient.EmailMessageException.UnsealingException if unsealing fails.
     */
    suspend fun execute(emailMessage: SealedEmailMessageEntity): ByteArray {
        logger.debug("Retrieving and decoding email message: $emailMessage")
        try {
            val s3Key =
                DefaultS3Client.constructS3KeyForEmailMessage(
                    emailAddressId = emailMessage.emailAddressId,
                    emailMessageId = emailMessage.id,
                    keyId = emailMessage.rfc822Header.keyId,
                )
            val sealedRfc822Data = s3EmailClient.download(s3Key)
            val rfc822Metadata = s3EmailClient.getObjectMetadata(s3Key)
            val contentEncodingValues =
                (
                    if (rfc822Metadata.contentEncoding != null) {
                        rfc822Metadata.contentEncoding.split(',')
                    } else {
                        listOf(StringConstants.CRYPTO_CONTENT_ENCODING, StringConstants.BINARY_DATA_CONTENT_ENCODING)
                    }
                ).reversed()
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
                        val keyInfo = KeyInfo(emailMessage.rfc822Header.keyId, KeyType.PRIVATE_KEY, emailMessage.rfc822Header.algorithm)
                        val unsealer = Unsealer(serviceKeyManager, keyInfo)
                        decodedBytes = unsealer.unsealBytes(sealedRfc822Data)
                    }

                    StringConstants.BINARY_DATA_CONTENT_ENCODING -> {} // no-op
                    else -> throw SudoEmailClient.EmailMessageException.UnsealingException("Invalid Content-Encoding value $value")
                }
            }
            return decodedBytes
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
