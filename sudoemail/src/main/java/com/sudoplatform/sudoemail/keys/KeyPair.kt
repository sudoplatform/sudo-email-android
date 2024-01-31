/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

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
        return "$clz[keyId=$keyId keyRingId=$keyRingId publicKey.size=${publicKey?.size} privateKey.size=${privateKey?.size}]"
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
}
