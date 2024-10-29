/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import androidx.annotation.VisibleForTesting
import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.graphql.fragment.BlockedAddress
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.fragment.EmailFolder
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudokeymanager.KeyManagerInterface

/**
 * Unpack and decrypt the sealed fields of an email address and message.
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

        /** Algorithm used when creating/registering public keys. */
        const val DEFAULT_ALGORITHM = "RSAEncryptionOAEPAESCBC"
    }

    private val algorithm: KeyManagerInterface.PublicKeyEncryptionAlgorithm = when (keyInfo.algorithm) {
        DEFAULT_ALGORITHM -> KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1
        else -> KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_PKCS1
    }

    sealed class UnsealerException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
        class SealedDataTooShortException(message: String? = null, cause: Throwable? = null) :
            UnsealerException(message, cause)
        class UnsupportedAlgorithmException(message: String? = null, cause: Throwable? = null) :
            UnsealerException(message, cause)
    }

    /**
     * The sealed value is a base64 encoded string that when base64 decoded contains:
     * bytes 0..255   : symmetric decryption key that is encrypted with the public key
     * bytes 256..end : the data that is encrypted with the symmetric key
     */
    @Throws(UnsealerException::class)
    fun unseal(valueBase64: String): String {
        val valueBytes = Base64.decode(valueBase64)
        return decrypt(keyInfo, valueBytes)
    }

    private fun decrypt(keyInfo: KeyInfo, data: ByteArray): String {
        return when (keyInfo.keyType) {
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
        val symmetricKey = deviceKeyManager.decryptWithKeyPairId(encryptedSymmetricKey, keyInfo.keyId, algorithm)
        return deviceKeyManager.decryptWithSymmetricKey(symmetricKey, encryptedData)
    }

    /**
     * Unseal the fields of the GraphQL [EmailAddressWithoutFolders.Alias] type.
     */
    fun unseal(value: EmailAddressWithoutFolders.Alias): String {
        val alias = value.sealedAttribute
        return unsealValue(alias.algorithm, alias.base64EncodedSealedData)
    }

    /**
     * Unseal the fields of the GraphQL [BlockedAddress.SealedValue] type
     */
    fun unseal(value: BlockedAddress.SealedValue): String {
        val sealedValue = value.sealedAttribute
        return unsealValue(sealedValue.algorithm, sealedValue.base64EncodedSealedData)
    }

    /**
     * Unseal the fields of the GraphQL [EmailFolder.CustomFolderName] type.
     */
    fun unseal(value: EmailFolder.CustomFolderName): String {
        val sealedValue = value.sealedAttribute
        return unsealValue(sealedValue.algorithm, sealedValue.base64EncodedSealedData)
    }

    private fun unsealValue(algorithm: String, base64EncodedSealedData: String): String {
        if (!SymmetricKeyEncryptionAlgorithm.isAlgorithmSupported(algorithm)) {
            throw UnsealerException.UnsupportedAlgorithmException(algorithm)
        }
        return unseal(base64EncodedSealedData)
    }
}
