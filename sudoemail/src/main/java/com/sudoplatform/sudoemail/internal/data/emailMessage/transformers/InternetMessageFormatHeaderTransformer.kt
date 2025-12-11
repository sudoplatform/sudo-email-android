/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage.transformers

import com.sudoplatform.sudoemail.internal.data.emailMessage.transformers.EmailMessageTransformer.toEmailMessageAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.InternetMessageFormatHeaderEntity
import com.sudoplatform.sudoemail.types.InternetMessageFormatHeader

/**
 * Transformer for converting Internet Message Format header data between API and entity representations.
 */
internal object InternetMessageFormatHeaderTransformer {
    /**
     * Transforms an API Internet Message Format header to entity type.
     *
     * @param headers [InternetMessageFormatHeader] The API headers.
     * @return [InternetMessageFormatHeaderEntity] The entity.
     */
    fun apiToEntity(headers: InternetMessageFormatHeader): InternetMessageFormatHeaderEntity =
        InternetMessageFormatHeaderEntity(
            subject = headers.subject,
            from = headers.from.toEmailMessageAddressEntity(),
            to = headers.to.map { it.toEmailMessageAddressEntity() },
            cc = headers.cc.map { it.toEmailMessageAddressEntity() },
            bcc = headers.bcc.map { it.toEmailMessageAddressEntity() },
            replyTo = headers.replyTo.map { it.toEmailMessageAddressEntity() },
        )
}
