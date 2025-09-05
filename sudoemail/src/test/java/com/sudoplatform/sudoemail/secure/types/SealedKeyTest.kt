/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.secure.types

import com.sudoplatform.sudokeymanager.KeyManagerInterface
import io.kotlintest.shouldBe
import okio.ByteString
import org.junit.Test

/**
 * Test the correct operation of the methods for the [SealedKey] type.
 */
class SealedKeyTest {
    private val stubSymmetricKey = "symmetricKey".toByteArray()
    private val sealedKey = SealedKey("publicKeyId", stubSymmetricKey)

    @Test
    fun `toJson() should encode SealedKey correctly`() {
        with(sealedKey) {
            publicKeyId shouldBe "publicKeyId"
            symmetricKey shouldBe stubSymmetricKey
            algorithm shouldBe KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1
        }

        val encodedSealedKey = sealedKey.toJson()
        encodedSealedKey shouldBe
            """{"$PUBLIC_KEY_ID_JSON":"publicKeyId","$ENCRYPTED_KEY_JSON":"","$ALGORITHM_JSON":"RSA_ECB_OAEPSHA1"}""".trimIndent()
    }

    @Test
    fun `fromJson() should decode SealedKey to SealedKeyComponents correctly`() {
        with(sealedKey) {
            publicKeyId shouldBe "publicKeyId"
            symmetricKey shouldBe stubSymmetricKey
            algorithm shouldBe KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1
        }
        val encodedSealedKey = sealedKey.toJson()

        val decodedSealedKeyComponents = SealedKeyComponents.fromJson(encodedSealedKey.toByteArray())
        decodedSealedKeyComponents shouldBe
            SealedKeyComponents(
                "publicKeyId",
                ByteString.EMPTY,
                KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
            )
    }
}
