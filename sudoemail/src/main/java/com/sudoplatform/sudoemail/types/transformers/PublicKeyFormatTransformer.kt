package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.graphql.type.KeyFormat
import com.sudoplatform.sudoemail.types.PublicKeyFormat
import com.sudoplatform.sudokeymanager.KeyManagerInterface

/**
 * Utility responsible for transforming between different public key format types.
 */
internal object PublicKeyFormatTransformer {

    /**
     * Transform the [KeyFormat] GraphQL type to its entity type.
     *
     * @param format [KeyFormat] The GraphQL type.
     * @return The [PublicKeyFormat] entity type.
     */
    fun toEntity(format: KeyFormat): PublicKeyFormat {
        return when (format) {
            KeyFormat.RSA_PUBLIC_KEY -> PublicKeyFormat.RSA_PUBLIC_KEY
            KeyFormat.SPKI -> PublicKeyFormat.SPKI
            else -> PublicKeyFormat.RSA_PUBLIC_KEY
        }
    }

    /**
     * Transform the public [PublicKeyFormat] entity type to the equivalent entity
     * defined in [KeyManagerInterface].
     *
     * @param format [PublicKeyFormat] The public key format type.
     * @return The [KeyManagerInterface.PublicKeyFormat] type.
     */
    fun toKeyManagerEntity(format: PublicKeyFormat): KeyManagerInterface.PublicKeyFormat {
        return when (format) {
            PublicKeyFormat.RSA_PUBLIC_KEY -> KeyManagerInterface.PublicKeyFormat.RSA_PUBLIC_KEY
            PublicKeyFormat.SPKI -> KeyManagerInterface.PublicKeyFormat.SPKI
            else -> KeyManagerInterface.PublicKeyFormat.RSA_PUBLIC_KEY
        }
    }
}
