/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.common.transformers

import com.sudoplatform.sudoemail.graphql.type.DateRangeInput
import com.sudoplatform.sudoemail.internal.domain.entities.common.DateRangeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageDateRangeEntity
import com.sudoplatform.sudoemail.types.DateRange
import com.sudoplatform.sudoemail.types.EmailMessageDateRange

/**
 * Transformer responsible for transforming the date range data types.
 */
internal object DateRangeTransformer {
    /**
     * Transforms a [DateRangeEntity] to GraphQL DateRangeInput.
     *
     * @return The GraphQL [DateRangeInput], or null if this is null.
     */
    fun DateRangeEntity?.toDateRangeInput(): DateRangeInput? {
        if (this == null) {
            return null
        }
        return DateRangeInput(
            endDateEpochMs = endDate.time.toDouble(),
            startDateEpochMs = startDate.time.toDouble(),
        )
    }

    /**
     * Transforms an API [DateRange] to DateRangeEntity.
     *
     * @return The [DateRangeEntity].
     */
    fun DateRange.toDateRangeEntity(): DateRangeEntity =
        DateRangeEntity(
            endDate = endDate,
            startDate = startDate,
        )

    /**
     * Transforms an API EmailMessageDateRange to EmailMessageDateRangeEntity.
     *
     * @param entity [EmailMessageDateRange] The API EmailMessageDateRange.
     * @return The [EmailMessageDateRangeEntity].
     */
    fun apiToEntity(entity: EmailMessageDateRange): EmailMessageDateRangeEntity =
        EmailMessageDateRangeEntity(
            sortDate = entity.sortDate?.toDateRangeEntity(),
            updatedAt = entity.updatedAt?.toDateRangeEntity(),
        )
}
