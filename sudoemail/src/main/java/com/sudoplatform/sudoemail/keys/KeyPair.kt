/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PublicKeyFormatEntity
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec

/**
 * Key Pair created and/or retrieved from a [DeviceKeyManager].
 *
 * @property keyId [String] Unique identifier of the key pair.
 * @property keyRingId [String] Identifier of the key ring that contains the key pair.
 * @property publicKey [ByteArray] Bytes of the public key (PEM format).
 * @property privateKey [ByteArray] Bytes of the private key (PEM format).
 */
internal data class KeyPair(
    val keyId: String,
    val keyRingId: String,
    val publicKey: ByteArray,
    val privateKey: ByteArray,
) {
    override fun toString(): String {
        val clz = this@KeyPair.javaClass.simpleName
        return "$clz[keyId=$keyId keyRingId=$keyRingId publicKey.size=${publicKey.size} privateKey.size=${privateKey.size}]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyPair

        if (keyId != other.keyId) return false
        if (keyRingId != other.keyRingId) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (!privateKey.contentEquals(other.privateKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyId.hashCode()
        result = 31 * result + keyRingId.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }

    fun getKeyFormat(): PublicKeyFormatEntity =
        PublicKeyFormatDetector.detectKeyFormat(publicKey)
            ?: throw IllegalStateException("Unable to determine public key format for key ID: $keyId")
}

/**
 * Utility object for determining public key formats
 */
internal object PublicKeyFormatDetector {
    /**
     * Determines the format of a public key by examining its structure.
     *
     * @param keyBytes The public key bytes
     * @return The detected key format or null if unable to determine
     */
    fun detectKeyFormat(keyBytes: ByteArray): PublicKeyFormatEntity? {
        return try {
            // Try to parse as SPKI (X.509 SubjectPublicKeyInfo) format first
            // SPKI keys have ASN.1 structure with algorithm identifier
            if (isSpkiFormat(keyBytes)) {
                return PublicKeyFormatEntity.SPKI
            }

            // Try to parse as RSA_PUBLIC_KEY format
            // RSA_PUBLIC_KEY format contains just the RSA key material without algorithm info
            if (isRsaPublicKeyFormat(keyBytes)) {
                return PublicKeyFormatEntity.RSA_PUBLIC_KEY
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if the key bytes are in SPKI (SubjectPublicKeyInfo) format
     */
    private fun isSpkiFormat(keyBytes: ByteArray): Boolean =
        try {
            // SPKI format can be parsed by X509EncodedKeySpec
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePublic(keySpec)
            true
        } catch (e: Exception) {
            false
        }

    /**
     * Checks if the key bytes are in RSA_PUBLIC_KEY format
     */
    private fun isRsaPublicKeyFormat(keyBytes: ByteArray): Boolean =
        try {
            // RSA_PUBLIC_KEY format starts with ASN.1 SEQUENCE tag (0x30)
            // but doesn't contain algorithm identifier like SPKI
            keyBytes.isNotEmpty() &&
                keyBytes[0] == 0x30.toByte() &&
                !isSpkiFormat(keyBytes) // Make sure it's not SPKI
        } catch (e: Exception) {
            false
        }

    /**
     * Detects format from a base64-encoded key string
     */
    fun detectKeyFormat(base64Key: String): PublicKeyFormatEntity? =
        try {
            val keyBytes = Base64.decode(base64Key.toByteArray())
            detectKeyFormat(keyBytes)
        } catch (e: Exception) {
            null
        }

    /**
     * Detects format by examining PEM headers (if present)
     */
    fun detectFromPemFormat(pemKey: String): PublicKeyFormatEntity? =
        when {
            pemKey.contains("-----BEGIN PUBLIC KEY-----") -> PublicKeyFormatEntity.SPKI
            pemKey.contains("-----BEGIN RSA PUBLIC KEY-----") -> PublicKeyFormatEntity.RSA_PUBLIC_KEY
            else -> null
        }
}
