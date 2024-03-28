/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.secure

import com.sudoplatform.sudoemail.secure.types.SecurePackage

/**
 * Encrypts and decrypts email messages within the email service.
 */
internal interface EmailCryptoService {

    companion object {
        const val IV_SIZE = 16
    }

    /**
     * Defines the exceptions for the [EmailCryptoService] methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class EmailCryptoServiceException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
        class InvalidArgumentException(message: String? = null, cause: Throwable? = null) :
            EmailCryptoServiceException(message = message, cause = cause)

        class KeyNotFoundException(message: String? = null, cause: Throwable? = null) :
            EmailCryptoServiceException(message = message, cause = cause)

        class SecureDataParsingException(message: String? = null, cause: Throwable? = null) :
            EmailCryptoServiceException(message = message, cause = cause)

        class SecureDataEncryptionException(message: String? = null, cause: Throwable? = null) :
            EmailCryptoServiceException(message = message, cause = cause)

        class SecureDataDecryptionException(message: String? = null, cause: Throwable? = null) :
            EmailCryptoServiceException(message = message, cause = cause)
    }

    /**
     * Encrypt email data that can be decrypted by all the recipients.
     *
     * @param data [ByteArray] The body of the email that should be encrypted.
     * @param keyIds [Set<String>] The list of [keyIds] for each recipient that must be able to decrypt the message.
     * @return The encrypted body and a sealed key for each recipient.
     * @throws [EmailCryptoServiceException] when the encryption operation fails.
     */
    @Throws(EmailCryptoServiceException::class)
    suspend fun encrypt(data: ByteArray, keyIds: Set<String>): SecurePackage

    /**
     * Decrypt email data using the key belonging to the current recipient.
     *
     * @param securePackage [SecurePackage] Contains the [SecurePackage.bodyAttachment] and [SecurePackage.keyAttachments]:
     *  - [SecurePackage.bodyAttachment]: an attachment that contains the encrypted body of the email message.
     *  - [SecurePackage.keyAttachments]: an attachment that contains the decryption keys for the email message.
     * @return The decrypted body of the email message.
     * @throws [EmailCryptoServiceException] when the decryption operation fails.
     */
    @Throws(EmailCryptoServiceException::class)
    suspend fun decrypt(securePackage: SecurePackage): ByteArray
}
