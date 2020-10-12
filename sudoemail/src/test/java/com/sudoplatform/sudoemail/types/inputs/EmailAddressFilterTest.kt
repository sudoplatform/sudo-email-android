/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import com.sudoplatform.sudoemail.types.inputs.filters.EmailAddressFilter
import com.sudoplatform.sudoemail.types.inputs.filters.filterEmailAddressesBy
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import org.junit.Test

/**
 * Check that the definition of the [EmailAddressFilter] matches what is specified.
 *
 * @since 2020-08-05
 */
class EmailAddressFilterTest {

    @Test
    fun `filter builder should combine all the elements specified`() {
        val filter = filterEmailAddressesBy {
            oneOf(
                allOf(
                    emailAddress equalTo "example@sudoplatform.com"
                )
            )
        }
        filter.propertyFilters.size shouldBe 1

        val addressFilter = EmailAddressFilter.PropertyFilter.StringFilter(
            EmailAddressFilter.Property.EMAIL_ADDRESS,
            EmailAddressFilter.ComparisonOperator.EQUAL,
            "example@sudoplatform.com"
        )

        filter.propertyFilters.shouldContainExactlyInAnyOrder(
            EmailAddressFilter.PropertyFilter.LogicalOr(
                setOf(
                    EmailAddressFilter.PropertyFilter.LogicalAnd(
                        setOf(addressFilter)
                    )
                )
            )
        )
    }
}
