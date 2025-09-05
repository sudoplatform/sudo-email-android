/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.secure.types

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import okio.ByteString.Companion.toByteString
import org.junit.Test
import java.util.Base64

/**
 * Test the correct operation of the methods for the [SecureData] type.
 */
class SecureDataTest {
    private val encryptedDataString = "encryptedData"
    private val initVectorKeyIDString = "initVectorKeyID"

    @Test
    fun `toJson() should encode SecureData correctly`() {
        val encryptedData = Base64.getEncoder().encodeToString(encryptedDataString.toByteArray())
        val initVectorKeyID =
            Base64.getEncoder().encodeToString(initVectorKeyIDString.toByteArray())

        val secureData =
            SecureData(
                encryptedDataString.toByteArray().toByteString(),
                initVectorKeyIDString.toByteArray().toByteString(),
            )

        val encodedSecureData = secureData.toJson()
        encodedSecureData shouldBe
            """{"${SecureData.ENCRYPTED_DATA_JSON}":"$encryptedData","${SecureData.INIT_VECTOR_KEY_ID}":"$initVectorKeyID"}"""
                .trimIndent()
    }

    @Test
    fun `fromJson() should decode to SecureData correctly`() {
        val encryptedData = Base64.getEncoder().encodeToString(encryptedDataString.toByteArray())
        val initVectorKeyID =
            Base64.getEncoder().encodeToString(initVectorKeyIDString.toByteArray())

        val jsonString = """{
            "encryptedData": "$encryptedData",
            "initVectorKeyID": "$initVectorKeyID"
        }"""
        val jsonData = jsonString.toByteArray().toByteString()

        val secureData = SecureData.fromJson(jsonData)
        with(secureData) {
            shouldNotBe(null)
            secureData.encryptedData.utf8() shouldBe "encryptedData"
            secureData.initVectorKeyID.utf8() shouldBe "initVectorKeyID"
        }
    }
}
