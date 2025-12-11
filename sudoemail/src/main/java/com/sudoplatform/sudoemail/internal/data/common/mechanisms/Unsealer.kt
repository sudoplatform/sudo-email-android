/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.common.mechanisms

import androidx.annotation.VisibleForTesting
import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.graphql.fragment.BlockedAddress
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.fragment.EmailFolder
import com.sudoplatform.sudoemail.internal.domain.entities.common.KeyInfo
import com.sudoplatform.sudoemail.internal.domain.entities.common.KeyType
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudoemail.util.Constants
import com.sudoplatform.sudokeymanager.KeyManagerInterface

/**
 * Unsealer for decrypting and decoding sealed (encrypted) data.
 *
 * @property deviceKeyManager [DeviceKeyManager] The device key manager for decryption operations.
 * @property keyInfo [KeyInfo] Information about the encryption key to use.
 */
internal class Unsealer(
    private val deviceKeyManager: DeviceKeyManager,
    private val keyInfo: KeyInfo,
) {
    companion object {
        /** Size of the AES symmetric key in bits */
        @VisibleForTesting
        const val KEY_SIZE_AES = 256

        /** RSA block size in bytes */
        const val BLOCK_SIZE_RSA = 256
    }

    private val algorithm: KeyManagerInterface.PublicKeyEncryptionAlgorithm =
        when (keyInfo.algorithm) {
            Constants.DEFAULT_PUBLIC_KEY_ALGORITHM -> KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1
            else -> KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_PKCS1
        }

    /**
     * Base exception for unsealer errors.
     */
    sealed class UnsealerException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        /**
         * Exception thrown when sealed data is too short to be valid.
         */
        class SealedDataTooShortException(
            message: String? = null,
            cause: Throwable? = null,
        ) : UnsealerException(message, cause)

        /**
         * Exception thrown when an unsupported algorithm is encountered.
         */
        class UnsupportedAlgorithmException(
            message: String? = null,
            cause: Throwable? = null,
        ) : UnsealerException(message, cause)
    }

    /**
     * Unseals a Base64-encoded sealed string value.
     *
     * The sealed value is a base64 encoded string that when base64 decoded contains:
     * - bytes 0..255: symmetric decryption key that is encrypted with the public key
     * - bytes 256..end: the data that is encrypted with the symmetric key
     *
     * @param valueBase64 [String] The Base64-encoded sealed value.
     * @return [String] The unsealed string.
     * @throws UnsealerException if unsealing fails.
     */
    @Throws(UnsealerException::class)
    fun unseal(valueBase64: String): String {
        val valueBytes = Base64.decode(valueBase64)
        return decrypt(keyInfo, valueBytes)
    }

    private fun decrypt(
        keyInfo: KeyInfo,
        data: ByteArray,
    ): String =
        when (keyInfo.keyType) {
            KeyType.PRIVATE_KEY -> {
                if (data.size < KEY_SIZE_AES) {
                    throw UnsealerException.SealedDataTooShortException("Sealed value too short")
                }
                val aesEncrypted = data.copyOfRange(0, BLOCK_SIZE_RSA)
                val cipherData = data.copyOfRange(KEY_SIZE_AES, data.size)
                val aesDecrypted = deviceKeyManager.decryptWithKeyPairId(aesEncrypted, keyInfo.keyId, algorithm)
                String(deviceKeyManager.decryptWithSymmetricKey(aesDecrypted, cipherData), Charsets.UTF_8)
            }
            KeyType.SYMMETRIC_KEY -> {
                String(deviceKeyManager.decryptWithSymmetricKeyId(keyInfo.keyId, data))
            }
        }

    /**
     * Unseals Base64-encoded sealed byte data.
     *
     * The sealed value is a base64 encoded byte array that when base64 decoded contains:
     * - bytes 0..255: symmetric decryption key that is encrypted with the public key
     * - bytes 256..end: the data that is encrypted with the symmetric key
     *
     * @param valueBase64 [ByteArray] The Base64-encoded sealed bytes.
     * @return [ByteArray] The unsealed byte data.
     * @throws UnsealerException if unsealing fails.
     */
    @Throws(UnsealerException::class)
    fun unsealBytes(valueBase64: ByteArray): ByteArray = unsealRawBytes(Base64.decode(valueBase64))

    private fun unsealRawBytes(valueBytes: ByteArray): ByteArray {
        if (valueBytes.size < KEY_SIZE_AES) {
            throw UnsealerException.SealedDataTooShortException("Sealed value too short")
        }
        val encryptedSymmetricKey = valueBytes.copyOfRange(0, KEY_SIZE_AES)
        val encryptedData = valueBytes.copyOfRange(KEY_SIZE_AES, valueBytes.size)
        val symmetricKey = deviceKeyManager.decryptWithKeyPairId(encryptedSymmetricKey, keyInfo.keyId, algorithm)
        return deviceKeyManager.decryptWithSymmetricKey(symmetricKey, encryptedData)
    }

    /**
     * Unseals an email address alias from GraphQL format.
     *
     * @param value [EmailAddressWithoutFolders.Alias] The sealed alias from GraphQL.
     * @return [String] The unsealed alias string.
     */
    fun unseal(value: EmailAddressWithoutFolders.Alias): String {
        val alias = value.sealedAttribute
        return unsealValue(alias.algorithm, alias.base64EncodedSealedData)
    }

    /**
     * Unseals a blocked address value from GraphQL format.
     *
     * @param value [BlockedAddress.SealedValue] The sealed value from GraphQL.
     * @return [String] The unsealed address string.
     */
    fun unseal(value: BlockedAddress.SealedValue): String {
        val sealedValue = value.sealedAttribute
        return unsealValue(sealedValue.algorithm, sealedValue.base64EncodedSealedData)
    }

    /**
     * Unseals an email folder custom name from GraphQL format.
     *
     * @param value [EmailFolder.CustomFolderName] The sealed custom folder name from GraphQL.
     * @return [String] The unsealed folder name string.
     */
    fun unseal(value: EmailFolder.CustomFolderName): String {
        val sealedValue = value.sealedAttribute
        return unsealValue(sealedValue.algorithm, sealedValue.base64EncodedSealedData)
    }

    fun unseal(value: SealedAttributeEntity): String = unsealValue(value.algorithm, value.base64EncodedSealedData)

    private fun unsealValue(
        algorithm: String,
        base64EncodedSealedData: String,
    ): String {
        if (!SymmetricKeyEncryptionAlgorithm.isAlgorithmSupported(algorithm)) {
            throw UnsealerException.UnsupportedAlgorithmException(algorithm)
        }
        return unseal(base64EncodedSealedData)
    }
}
