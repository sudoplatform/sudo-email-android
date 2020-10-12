/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import androidx.annotation.VisibleForTesting
import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.keys.DefaultPublicKeyService
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudokeymanager.KeyManagerInterface

/**
 * Unpack and decrypt the sealed fields of an email message.
 *
 * @since 2020-08-11
 */
internal class Unsealer(
    private val deviceKeyManager: DeviceKeyManager,
    private val keyId: String,
    algorithmSpec: String
) {
    companion object {
        /** Size of the AES symmetric key. */
        @VisibleForTesting
        const val KEY_SIZE_AES = 256
    }

    private val algorithm: KeyManagerInterface.PublicKeyEncryptionAlgorithm = when (algorithmSpec) {
        DefaultPublicKeyService.DEFAULT_ALGORITHM -> KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1
        else -> KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_PKCS1
    }

    sealed class UnsealerException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
        class SealedDataTooShortException(message: String? = null, cause: Throwable? = null) :
            UnsealerException(message, cause)
    }

    /**
     * The sealed value is a base64 encoded string that when base64 decoded contains:
     * bytes 0..255   : symmetric decryption key that is encrypted with the public key
     * bytes 256..end : the data that is encrypted with the symmetric key
     */
    @Throws(UnsealerException::class)
    fun unseal(valueBase64: String): String {
        return unsealRawBytes(Base64.decode(valueBase64)).toString(Charsets.UTF_8)
    }

    /**
     * The sealed value is a base64 encoded string that when base64 decoded contains:
     * bytes 0..255   : symmetric decryption key that is encrypted with the public key
     * bytes 256..end : the data that is encrypted with the symmetric key
     */
    @Throws(UnsealerException::class)
    fun unsealBytes(valueBase64: ByteArray): ByteArray {
        return unsealRawBytes(Base64.decode(valueBase64))
    }

    private fun unsealRawBytes(valueBytes: ByteArray): ByteArray {
        if (valueBytes.size < KEY_SIZE_AES) {
            throw UnsealerException.SealedDataTooShortException("Sealed value too short")
        }
        val encryptedSymmetricKey = valueBytes.copyOfRange(0, KEY_SIZE_AES)
        val encryptedData = valueBytes.copyOfRange(KEY_SIZE_AES, valueBytes.size)
        val symmetricKey = deviceKeyManager.decryptWithPrivateKey(encryptedSymmetricKey, keyId, algorithm)
        return deviceKeyManager.decryptWithSymmetricKey(symmetricKey, encryptedData)
    }

    /**
     * Unseal a list of sealed email address strings.
     */
    @Throws(UnsealerException::class)
    fun unsealEmailAddresses(emailAddressesBase64: List<String>): List<EmailMessage.EmailAddress> {
        return emailAddressesBase64.mapNotNull {
            EmailMessageTransformer.toEmailAddress(unseal(it))
        }
    }
}
