/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import com.sudoplatform.sudoemail.types.inputs.filters.EmailMessageFilter
import com.sudoplatform.sudoemail.types.inputs.filters.filterEmailMessagesBy
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import org.junit.Test

/**
 * Check that the definition of the [EmailMessageFilter] matches what is specified.
 *
 * @since 2020-08-11
 */
class EmailMessageFilterTest {

    @Test
    fun `filter builder should combine all the elements specified`() {
        val filter = filterEmailMessagesBy {
            oneOf(
                allOf(
                    direction equalTo outbound,
                    not(seen)
                ),
                direction equalTo inbound
            )
        }
        filter.propertyFilters.size shouldBe 1

        val outboundFilter = EmailMessageFilter.PropertyFilter.StringFilter(
            EmailMessageFilter.Property.DIRECTION,
            EmailMessageFilter.ComparisonOperator.EQUAL,
            "OUTBOUND"
        )
        val notSeenFilter = EmailMessageFilter.PropertyFilter.BooleanFilter(
            EmailMessageFilter.Property.SEEN,
            EmailMessageFilter.ComparisonOperator.NOT_EQUAL,
            true
        )
        val inboundFilter = EmailMessageFilter.PropertyFilter.StringFilter(
            EmailMessageFilter.Property.DIRECTION,
            EmailMessageFilter.ComparisonOperator.EQUAL,
            "INBOUND"
        )

        filter.propertyFilters.shouldContainExactlyInAnyOrder(
            EmailMessageFilter.PropertyFilter.LogicalOr(
                setOf(
                    EmailMessageFilter.PropertyFilter.LogicalAnd(
                        setOf(outboundFilter, notSeenFilter)
                    ),
                    inboundFilter
                )
            )
        )
    }
}
