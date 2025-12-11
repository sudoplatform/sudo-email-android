/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.common.transformers

import com.sudoplatform.sudoemail.internal.data.emailAddress.transformers.EmailAddressTransformer
import com.sudoplatform.sudoemail.internal.data.emailMessage.transformers.EmailMessageTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListAPIResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PartialEmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.UnsealedEmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.PartialEmailMessageEntity
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.PartialEmailAddress
import com.sudoplatform.sudoemail.types.PartialEmailMessage
import com.sudoplatform.sudoemail.types.PartialResult

/**
 * Transformer for converting list API result entities to API types.
 */
internal object ListApiResultTransformer {
    /**
     * Transforms an email address list API result entity to API type.
     *
     * @param entity [ListAPIResultEntity]The email address list API result entity.
     * @return The [ListAPIResult] containing email addresses and partial results.
     */
    fun transformEmailAddressListApiResultEntity(
        entity: ListAPIResultEntity<UnsealedEmailAddressEntity, PartialEmailAddressEntity>,
    ): ListAPIResult<EmailAddress, PartialEmailAddress> =
        when (entity) {
            is ListAPIResultEntity.Success -> {
                val success = entity.result.items.map { EmailAddressTransformer.unsealedEntityToApi(it) }
                ListAPIResult.Success(
                    ListAPIResult.ListSuccessResult(
                        success,
                        entity.result.nextToken,
                    ),
                )
            }
            is ListAPIResultEntity.Partial -> {
                val success = entity.result.items.map { EmailAddressTransformer.unsealedEntityToApi(it) }
                val partials =
                    entity.result.failed.map {
                        PartialResult(
                            EmailAddressTransformer.partialEntityToApi(it.partial),
                            it.cause,
                        )
                    }
                ListAPIResult.Partial(
                    ListAPIResult.ListPartialResult(
                        success,
                        partials,
                        entity.result.nextToken,
                    ),
                )
            }
        }

    /**
     * Transforms an email message list API result entity to API type.
     *
     * @param entity [ListAPIResultEntity] The email message list API result entity.
     * @return The [ListAPIResult] containing email messages and partial results.
     */
    fun transformEmailMessageListApiResultEntity(
        entity: ListAPIResultEntity<EmailMessageEntity, PartialEmailMessageEntity>,
    ): ListAPIResult<EmailMessage, PartialEmailMessage> =
        when (entity) {
            is ListAPIResultEntity.Success -> {
                val success = entity.result.items.map { EmailMessageTransformer.entityToApi(it) }
                ListAPIResult.Success(
                    ListAPIResult.ListSuccessResult(
                        success,
                        entity.result.nextToken,
                    ),
                )
            }
            is ListAPIResultEntity.Partial -> {
                val success = entity.result.items.map { EmailMessageTransformer.entityToApi(it) }
                val partials =
                    entity.result.failed.map {
                        PartialResult(
                            EmailMessageTransformer.entityToPartialApi(it.partial),
                            it.cause,
                        )
                    }
                ListAPIResult.Partial(
                    ListAPIResult.ListPartialResult(
                        success,
                        partials,
                        entity.result.nextToken,
                    ),
                )
            }
        }
}
