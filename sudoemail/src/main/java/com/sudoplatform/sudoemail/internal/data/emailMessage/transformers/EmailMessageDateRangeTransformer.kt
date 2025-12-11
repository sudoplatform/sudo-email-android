/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage.transformers

import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.graphql.type.EmailMessageDateRangeInput
import com.sudoplatform.sudoemail.internal.data.common.transformers.DateRangeTransformer.toDateRangeInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageDateRangeEntity

/**
 * Transformer responsible for transforming the EmailMessageDateRangeInput GraphQL data types to
 * the entity type that is exposed to users.
 */
internal object EmailMessageDateRangeTransformer {
    /**
     * Transforms an [EmailMessageDateRangeEntity] to GraphQL EmailMessageDateRangeInput.
     *
     * @return [EmailMessageDateRangeInput] The GraphQL input type, or null if this is null.
     */
    fun EmailMessageDateRangeEntity?.toEmailMessageDateRangeInput(): EmailMessageDateRangeInput? {
        if (this == null) {
            return null
        }
        return EmailMessageDateRangeInput(
            sortDateEpochMs = Optional.presentIfNotNull(sortDate.toDateRangeInput()),
            updatedAtEpochMs = Optional.presentIfNotNull(updatedAt.toDateRangeInput()),
        )
    }
}
