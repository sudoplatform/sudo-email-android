/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.secure.types

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sudoplatform.sudoemail.secure.EmailCryptoService.EmailCryptoServiceException
import com.sudoplatform.sudokeymanager.KeyManagerInterface.PublicKeyEncryptionAlgorithm
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

// JSON element names
const val PUBLIC_KEY_ID_JSON = "publicKeyId"
const val ENCRYPTED_KEY_JSON = "encryptedKey"
const val ALGORITHM_JSON = "algorithm"

/**
 * A sealed symmetric key which is sealed by encrypting it with a public key.
 * It can be unsealed by decrypting it with the corresponding private key.
 *
 * @property publicKeyId [String] Identifier associated with the public key.
 * @property symmetricKey [ByteArray] The symmetric key data.
 * @property algorithm [PublicKeyEncryptionAlgorithm] The algorithm used to encrypt the symmetric key.
 */
internal data class SealedKey(
    val publicKeyId: String,
    val symmetricKey: ByteArray,
    val algorithm: PublicKeyEncryptionAlgorithm = PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
) {
    var encryptedKey: ByteString = ByteString.EMPTY

    /**
     * Encode the [SealedKey] object as a JSON object.
     *
     * @return The JSON encoded version of this object.
     */
    fun toJson(): String {
        val jsonObject = JsonObject().apply {
            addProperty(PUBLIC_KEY_ID_JSON, publicKeyId)
            addProperty(ENCRYPTED_KEY_JSON, encryptedKey.base64())
            addProperty(ALGORITHM_JSON, algorithm.name)
        }
        return jsonObject.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SealedKey

        if (publicKeyId != other.publicKeyId) return false
        if (!symmetricKey.contentEquals(other.symmetricKey)) return false
        if (algorithm != other.algorithm) return false
        return encryptedKey == other.encryptedKey
    }

    override fun hashCode(): Int {
        var result = publicKeyId.hashCode()
        result = 31 * result + symmetricKey.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + encryptedKey.hashCode()
        return result
    }
}

internal data class SealedKeyComponents(
    val publicKeyId: String,
    val encryptedKey: ByteString,
    val algorithm: PublicKeyEncryptionAlgorithm,
) {
    companion object {
        fun fromJson(data: ByteArray): SealedKeyComponents {
            JsonParser.parseString(data.toString(Charsets.UTF_8))?.let { jsonElement ->
                with(jsonElement.asJsonObject) {
                    val encryptedKey = get(ENCRYPTED_KEY_JSON).asString.decodeBase64()
                        ?: throw EmailCryptoServiceException.SecureDataParsingException(
                            "Base64 decoding of encrypted key failed",
                        )
                    val privateKeyId = get(PUBLIC_KEY_ID_JSON).asString
                    val algorithm = PublicKeyEncryptionAlgorithm.valueOf(get(ALGORITHM_JSON).asString)
                    return SealedKeyComponents(privateKeyId, encryptedKey, algorithm)
                }
            }
            throw EmailCryptoServiceException.SecureDataParsingException("Unable to parse the JSON data")
        }
    }
}
