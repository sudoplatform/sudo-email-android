/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.secure

import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudoemail.secure.EmailCryptoService.Companion.IV_SIZE
import com.sudoplatform.sudoemail.secure.EmailCryptoService.EmailCryptoServiceException
import com.sudoplatform.sudoemail.secure.types.SealedKey
import com.sudoplatform.sudoemail.secure.types.SealedKeyComponents
import com.sudoplatform.sudoemail.secure.types.SecureData
import com.sudoplatform.sudoemail.secure.types.SecureEmailAttachmentType
import com.sudoplatform.sudoemail.secure.types.SecurePackage
import com.sudoplatform.sudoemail.types.EmailAddressPublicInfo
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.transformers.PublicKeyFormatTransformer
import com.sudoplatform.sudokeymanager.KeyManagerInterface.PublicKeyEncryptionAlgorithm
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import okio.ByteString.Companion.toByteString

internal class DefaultEmailCryptoService(
    private val deviceKeyManager: DeviceKeyManager,
    private val logger: Logger = Logger(
        LogConstants.SUDOLOG_TAG,
        AndroidUtilsLogDriver(LogLevel.INFO),
    ),
) : EmailCryptoService {

    companion object {

        /** Exception messages */
        private const val ENCRYPTION_ERROR_MSG =
            "Exception while encrypting the email body and keys"
        private const val DECRYPTION_ERROR_MSG = "Exception while decrypting the email body"
        private const val DECRYPTION_KEY_NOT_FOUND_ERROR_MSG =
            "Could not find a key to decrypt the email message body"
    }

    @Throws(EmailCryptoServiceException::class)
    override suspend fun encrypt(data: ByteArray, emailAddressPublicInfo: List<EmailAddressPublicInfo>): SecurePackage {
        if (data.isEmpty() || emailAddressPublicInfo.isEmpty()) {
            throw EmailCryptoServiceException.InvalidArgumentException()
        }

        try {
            // Create a symmetric key that will be used to encrypt the input data
            val symmetricKey = deviceKeyManager.generateRandomSymmetricKey()

            // Encrypt the input data using the symmetric key with an AES/CBC/PKCS7Padding cipher
            val initVector = deviceKeyManager.createRandomData(IV_SIZE)
            val encryptedBodyData = deviceKeyManager.encryptWithSymmetricKey(
                symmetricKey,
                data,
                initVector,
            )
            val secureBodyData =
                SecureData(encryptedBodyData.toByteString(), initVector.toByteString())
            val serializedBodyData = secureBodyData.toJson().toByteArray()

            // Build an email attachment of the secure email body data
            val secureBodyAttachment =
                buildEmailAttachment(serializedBodyData, SecureEmailAttachmentType.BODY)

            // Iterate through each public key for each recipient and encrypt the symmetric key with the public key
            val distinctPublicInfo = emailAddressPublicInfo.distinctBy { it.keyId }
            val secureKeyAttachments = distinctPublicInfo.mapIndexed { index, publicInfo ->
                // Seal the symmetric key using the publicKey and RSA_ECB_OAEPSHA1 algorithm
                val sealedKey =
                    SealedKey(publicInfo.keyId, symmetricKey, PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1)
                val publicKeyFormat = PublicKeyFormatTransformer.toKeyManagerEntity(
                    publicInfo.publicKeyDetails.keyFormat,
                )
                val encryptedSymmetricKey = deviceKeyManager.encryptWithPublicKey(
                    Base64.decode(publicInfo.publicKeyDetails.publicKey),
                    sealedKey.symmetricKey,
                    publicKeyFormat,
                    sealedKey.algorithm,
                )
                sealedKey.encryptedKey = encryptedSymmetricKey.toByteString()
                val sealedKeyData = sealedKey.toJson().toByteArray()

                // Build an email attachment of the sealed keys
                buildEmailAttachment(
                    sealedKeyData,
                    SecureEmailAttachmentType.KEY_EXCHANGE,
                    index + 1,
                )
            }

            // Return a secure package with the secure key attachments and the secure body attachment
            return SecurePackage(secureKeyAttachments.toMutableSet(), secureBodyAttachment)
        } catch (e: DeviceKeyManager.DeviceKeyManagerException) {
            logger.error("error $e")
            throw EmailCryptoServiceException.SecureDataEncryptionException(ENCRYPTION_ERROR_MSG, e)
        }
    }

    @Throws(EmailCryptoServiceException::class)
    override suspend fun decrypt(securePackage: SecurePackage): ByteArray {
        if (securePackage.bodyAttachment.data.isEmpty() || securePackage.keyAttachments.isEmpty()) {
            throw EmailCryptoServiceException.InvalidArgumentException()
        }

        try {
            val secureBodyData =
                SecureData.fromJson(securePackage.bodyAttachment.data.toByteString())

            // Iterate through the set of keyAttachments and search for the key
            // belonging to the current recipient
            val keyComponents = securePackage.keyAttachments
                .mapNotNull { key ->
                    if (key.data.isNotEmpty()) {
                        // Parse the key and pluck the publicKeyId
                        val sealedKeyComponents = SealedKeyComponents.fromJson(key.data)
                        // Check if the private key pair exists based on the publicKeyId
                        if (deviceKeyManager.privateKeyExists(sealedKeyComponents.publicKeyId)) {
                            // Found the right key
                            return@mapNotNull sealedKeyComponents
                        }
                    }
                    null
                }
                .firstOrNull()
                ?: throw EmailCryptoServiceException.KeyNotFoundException(
                    DECRYPTION_KEY_NOT_FOUND_ERROR_MSG,
                )

            // Decrypt the symmetric key with the private key using the RSA/ECB/OAEPSHA1 cipher
            val symmetricKey = deviceKeyManager.decryptWithKeyPairId(
                keyComponents.encryptedKey.toByteArray(),
                keyComponents.publicKeyId,
                keyComponents.algorithm,
            )

            // Decrypt and return the email body data using the symmetric key with the
            // AES/CBC/PKCS7Padding cipher
            return deviceKeyManager.decryptWithSymmetricKey(
                symmetricKey,
                secureBodyData.encryptedData.toByteArray(),
                secureBodyData.initVectorKeyID.toByteArray(),
            )
        } catch (e: DeviceKeyManager.DeviceKeyManagerException) {
            logger.error("error $e")
            throw EmailCryptoServiceException.SecureDataDecryptionException(DECRYPTION_ERROR_MSG, e)
        }
    }

    private fun buildEmailAttachment(
        data: ByteArray,
        attachmentType: SecureEmailAttachmentType,
        attachmentNumber: Int = -1,
    ): EmailAttachment {
        val fileName =
            if (attachmentNumber >= 0) "${attachmentType.fileName} $attachmentNumber" else attachmentType.fileName
        return EmailAttachment(
            contentId = attachmentType.contentId,
            fileName = fileName,
            inlineAttachment = false,
            mimeType = attachmentType.mimeType,
            data = data,
        )
    }
}
