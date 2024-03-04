/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.graphql.type.EmailMessageDateRangeInput
import com.sudoplatform.sudoemail.types.EmailMessageDateRange
import com.sudoplatform.sudoemail.types.transformers.DateRangeTransformer.toDateRangeInput

/**
 * Transformer responsible for transforming the [EmailMessageDateRange] GraphQL data types to
 * the entity type that is exposed to users.
 */
internal object EmailMessageDateRangeTransformer {

    /**
     * Transform the input type [EmailMessageDateRange] into the corresponding GraphQL type [EmailMessageDateRangeInput].
     */
    fun EmailMessageDateRange?.toEmailMessageDateRangeInput(): EmailMessageDateRangeInput? {
        if (this == null) {
            return null
        }
        return EmailMessageDateRangeInput
            .builder()
            .sortDateEpochMs(sortDate.toDateRangeInput())
            .updatedAtEpochMs(updatedAt.toDateRangeInput())
            .build()
    }
}
