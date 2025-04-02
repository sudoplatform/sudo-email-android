/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.secure.types

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sudoplatform.sudoemail.secure.EmailCryptoService.EmailCryptoServiceException
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

/**
 * Representation of secure data items that can be encoded and decoded to and from
 * JSON.
 *
 * @property encryptedData [ByteString] The secure encrypted data.
 * @property initVectorKeyID [ByteString] The initialization vector.
 */
internal data class SecureData(
    val encryptedData: ByteString,
    val initVectorKeyID: ByteString,
) {
    companion object {
        // JSON element names
        const val ENCRYPTED_DATA_JSON = "encryptedData"
        const val INIT_VECTOR_KEY_ID = "initVectorKeyID"

        /**
         * Decode the [data] from its JSON form to an instance of [SecureData].
         *
         * @param data [ByteString] A json object
         * @return An instance of this json object in the [SecureData] form.
         * @throws EmailCryptoServiceException.SecureDataParsingException
         */
        fun fromJson(data: ByteString): SecureData {
            JsonParser.parseString(data.utf8())?.let { jsonElement ->
                with(jsonElement.asJsonObject) {
                    val encryptedData = this.get(ENCRYPTED_DATA_JSON).asString.decodeBase64()
                        ?: throw EmailCryptoServiceException.SecureDataParsingException(
                            "Base64 decoding of encrypted key failed",
                        )
                    val initVectorData = this.get(INIT_VECTOR_KEY_ID).asString.decodeBase64()
                        ?: throw EmailCryptoServiceException.SecureDataParsingException(
                            "Base64 decoding of IV failed",
                        )
                    return SecureData(encryptedData, initVectorData)
                }
            }
            throw EmailCryptoServiceException.SecureDataParsingException(
                "Unable to parse the JSON data",
            )
        }
    }

    /**
     * Encode the [SecureData] object as a JSON object.
     *
     * @return The JSON encoded version of this object.
     */
    fun toJson(): String {
        val jsonObject = JsonObject().apply {
            addProperty(ENCRYPTED_DATA_JSON, encryptedData.base64())
            addProperty(INIT_VECTOR_KEY_ID, initVectorKeyID.base64())
        }
        return jsonObject.toString()
    }
}
