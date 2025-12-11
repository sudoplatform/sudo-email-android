/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.draftMessage.transformers

import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageMetadataEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageWithContentEntity
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.DraftEmailMessageMetadata
import com.sudoplatform.sudoemail.types.DraftEmailMessageWithContent

/**
 * Transformer responsible for transforming, decrypting/encrypting and encoding/decoding
 * [com.sudoplatform.sudoemail.types.DraftEmailMessage]s' RFC 822 data.
 */
internal object DraftEmailMessageTransformer {
    /**
     * Uses the [SealingService] to seal the RFC 822 data, then encodes it into Base64.
     *
     * @param sealingService [SealingService] The service used to seal the data.
     * @param rfc822Data [ByteArray] The data to be encrypted and encoded.
     * @param symmetricKeyId [String] The symmetric key used to seal the data.
     * @return The encrypted and encoded data as a [ByteArray].
     */
    fun toEncryptedAndEncodedRfc822Data(
        sealingService: SealingService,
        rfc822Data: ByteArray,
        symmetricKeyId: String,
    ): ByteArray {
        val sealedRfc822Data = sealingService.sealString(symmetricKeyId, rfc822Data)
        return Base64.encode(sealedRfc822Data)
    }

    /**
     * Decodes the RFC 822 data from Base64 then decrypts it using the [SealingService].
     *
     * @param sealingService [SealingService] The service used to unseal the data.
     * @param sealedRfc822Data [ByteArray] The data to be decrypted and decoded.
     * @param symmetricKeyId [String] The symmetric key used to unseal the data.
     * @return The decrypted and decoded data as a [ByteArray].
     */
    fun toDecodedAndDecryptedRfc822Data(
        sealingService: SealingService,
        sealedRfc822Data: ByteArray,
        symmetricKeyId: String,
    ): ByteArray {
        val decodedRfc822Data = Base64.decode(sealedRfc822Data)
        return sealingService.unsealString(symmetricKeyId, decodedRfc822Data)
    }

    /**
     * Transforms a [DraftEmailMessageWithContentEntity] to API type.
     *
     * @param entity [DraftEmailMessageWithContentEntity] The entity to transform.
     * @return [DraftEmailMessageWithContent] The API type.
     */
    fun entityWithContentToApi(entity: DraftEmailMessageWithContentEntity): DraftEmailMessageWithContent =
        DraftEmailMessageWithContent(
            id = entity.id,
            emailAddressId = entity.emailAddressId,
            updatedAt = entity.updatedAt,
            rfc822Data = entity.rfc822Data,
        )

    /**
     * Transforms a [DraftEmailMessageMetadataEntity] to API type.
     *
     * @param entity [DraftEmailMessageMetadataEntity] The entity to transform.
     * @return [DraftEmailMessageMetadata] The API type.
     */
    fun draftMetadataEntityToApi(entity: DraftEmailMessageMetadataEntity): DraftEmailMessageMetadata =
        DraftEmailMessageMetadata(
            id = entity.id,
            emailAddressId = entity.emailAddressId,
            updatedAt = entity.updatedAt,
        )
}
