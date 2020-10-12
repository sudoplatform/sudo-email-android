/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.graphql.CreatePublicKeyForEmailMutation
import com.sudoplatform.sudoemail.graphql.GetKeyRingForEmailQuery
import com.sudoplatform.sudoemail.keys.KeyRing
import com.sudoplatform.sudouser.PublicKey

/**
 * Transformer responsible for transforming the keys GraphQL data types to the
 * entity type that is exposed to users.
 *
 * @since 2020-08-05
 */
internal object KeyTransformer {

    /**
     * Transform the results of the [GetKeyRingForEmailQuery].
     *
     * @param result The GraphQL query results.
     * @return The [KeyRing] entity type.
     */
    fun toKeyRing(result: GetKeyRingForEmailQuery.GetKeyRingForEmail): KeyRing {
        val keys = result.items().map {
            PublicKey(
                keyId = it.keyId(),
                publicKey = Base64.decode(it.publicKey()),
                algorithm = it.algorithm()
            )
        }
        return KeyRing(
            id = result.items().firstOrNull()?.keyRingId() ?: "",
            keys = keys
        )
    }

    /**
     * Transform the results of the [CreatePublicKeyForEmailMutation].
     *
     * @param result The GraphQL mutation results.
     * @return The [PublicKey] entity type.
     */
    fun toPublicKey(result: CreatePublicKeyForEmailMutation.CreatePublicKeyForEmail): PublicKey {
        return PublicKey(
            keyId = result.keyId(),
            publicKey = Base64.decode(result.publicKey()),
            algorithm = result.algorithm()
        )
    }
}
