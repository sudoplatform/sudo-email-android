/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.common.transformers

import com.sudoplatform.sudoemail.graphql.type.KeyFormat
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PublicKeyFormatEntity
import com.sudoplatform.sudokeymanager.KeyManagerInterface

/**
 * Utility responsible for transforming between different public key format types.
 */
internal object PublicKeyFormatTransformer {
    /**
     * Transform the [com.sudoplatform.sudoemail.graphql.type.KeyFormat] GraphQL type to its entity type.
     *
     * @param format [com.sudoplatform.sudoemail.graphql.type.KeyFormat] The GraphQL type.
     * @return The [com.sudoplatform.sudoemail.types.PublicKeyFormat] entity type.
     */
    fun toEntity(format: KeyFormat): PublicKeyFormatEntity =
        when (format) {
            KeyFormat.RSA_PUBLIC_KEY -> PublicKeyFormatEntity.RSA_PUBLIC_KEY
            KeyFormat.SPKI -> PublicKeyFormatEntity.SPKI
            else -> PublicKeyFormatEntity.RSA_PUBLIC_KEY
        }

    /**
     * Transform the public [PublicKeyFormatEntity] entity type to the equivalent entity
     * defined in [com.sudoplatform.sudokeymanager.KeyManagerInterface].
     *
     * @param format [PublicKeyFormatEntity] The public key format type.
     * @return The [com.sudoplatform.sudokeymanager.KeyManagerInterface.PublicKeyFormat] type.
     */
    fun toKeyManagerEntity(format: PublicKeyFormatEntity): KeyManagerInterface.PublicKeyFormat =
        when (format) {
            PublicKeyFormatEntity.RSA_PUBLIC_KEY -> KeyManagerInterface.PublicKeyFormat.RSA_PUBLIC_KEY
            PublicKeyFormatEntity.SPKI -> KeyManagerInterface.PublicKeyFormat.SPKI
        }
}
