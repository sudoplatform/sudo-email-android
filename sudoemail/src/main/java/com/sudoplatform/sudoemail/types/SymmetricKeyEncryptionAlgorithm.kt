/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

/**
 * Representation of an enumeration depicting a list of supported key encryption
 * algorithms.
 *
 * @enum SymmetricKeyEncryptionAlgorithm
 */
enum class SymmetricKeyEncryptionAlgorithm(private val algorithmName: String) {
    AES_CBC_PKCS7PADDING("AES/CBC/PKCS7Padding"),
    ;

    companion object {
        fun isAlgorithmSupported(algorithm: String): Boolean {
            return values().any { it.algorithmName == algorithm }
        }

        fun fromString(stringValue: String): SymmetricKeyEncryptionAlgorithm? {
            var value: SymmetricKeyEncryptionAlgorithm? = null
            if (stringValue == SymmetricKeyEncryptionAlgorithm.toString()) {
                value =
                    AES_CBC_PKCS7PADDING
            }

            return value
        }
    }

    override fun toString(): String {
        when (this) {
            AES_CBC_PKCS7PADDING -> return this.algorithmName
        }
    }
}
