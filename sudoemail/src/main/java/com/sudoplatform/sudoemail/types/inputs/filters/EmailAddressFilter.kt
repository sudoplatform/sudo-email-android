/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs.filters

import com.sudoplatform.sudoemail.types.EmailAddress

/**
 * A filter that can be applied when listing email addresses so that only the subset
 * of email addresses that match the items in the filter are returned.
 *
 * @sample com.sudoplatform.sudoemail.samples.Samples.emailAddressFilter
 * @since 2020-08-05
 */
class EmailAddressFilter private constructor(val propertyFilters: Set<PropertyFilter>) {

    companion object {
        /** Return a [EmailAddressFilter.Builder] that is used to create an [EmailAddressFilter] */
        @JvmStatic
        fun builder() = Builder()
    }

    /** These are the properties of a [EmailAddress] that can be used in a [EmailAddressFilter] */
    enum class Property {
        EMAIL_ADDRESS
    }

    /** Properties used in filters can be compared to the values in an [EmailAddress] with these operators. */
    enum class ComparisonOperator {
        EQUAL,
        NOT_EQUAL,
        BEGINS_WITH,
    }

    sealed class PropertyFilter {
        /**
         * The filter [property] and its [value] that must match when compared according to the [comparison]
         * for a [EmailAddress] to be included in the list of results.
         */
        data class StringFilter(
            val property: Property,
            val comparison: ComparisonOperator,
            val value: String
        ) : PropertyFilter()

        data class LogicalAnd(
            val filters: Set<PropertyFilter>
        ) : PropertyFilter()

        data class LogicalOr(
            val filters: Set<PropertyFilter>
        ) : PropertyFilter()

        internal object Empty : PropertyFilter()
    }

    /** A Builder that is used to create a [EmailAddressFilter] */
    class Builder internal constructor() {

        private val propertyFilters = mutableSetOf<PropertyFilter>()

        /**
         * These provide a nicer syntax than specifying the enums in the lambda,
         * e.g emailAddress equal "example@sudplatform.com" rather than [Property.EMAIL_ADDRESS] equal "example@sudplatform.com"
         */
        val emailAddress = Property.EMAIL_ADDRESS

        infix fun Property.equalTo(value: String): PropertyFilter {
            return PropertyFilter.StringFilter(this, ComparisonOperator.EQUAL, value)
        }

        infix fun Property.notEqualTo(value: String): PropertyFilter {
            return PropertyFilter.StringFilter(this, ComparisonOperator.NOT_EQUAL, value)
        }

        infix fun Property.beginsWith(value: String): PropertyFilter {
            return PropertyFilter.StringFilter(this, ComparisonOperator.BEGINS_WITH, value)
        }

        fun allOf(vararg filters: PropertyFilter): PropertyFilter {
            return PropertyFilter.LogicalAnd(filters.toSet())
        }

        fun oneOf(vararg filters: PropertyFilter): PropertyFilter {
            return PropertyFilter.LogicalOr(filters.toSet())
        }

        internal fun run(block: Builder.() -> PropertyFilter): Builder {
            propertyFilters.add(block.invoke(this))
            return this
        }

        fun build(): EmailAddressFilter {
            return EmailAddressFilter(propertyFilters)
        }
    }
}

/**
 * A helper function to make it easy to specify the filter of email addresses.
 * @sample com.sudoplatform.sudoemail.samples.Samples.emailAddressFilter
 */
fun filterEmailAddressesBy(
    init: EmailAddressFilter.Builder.() -> EmailAddressFilter.PropertyFilter = { EmailAddressFilter.PropertyFilter.Empty }
) = EmailAddressFilter.builder()
    .run(init)
    .build()
