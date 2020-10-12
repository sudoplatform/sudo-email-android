/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs.filters

import com.sudoplatform.sudoemail.types.EmailMessage

/**
 * A filter that can be applied when listing email messages so that only the subset
 * of email messages that match the items in the filter are returned.
 *
 * @sample com.sudoplatform.sudoemail.samples.Samples.emailMessageFilter
 * @since 2020-08-11
 */
class EmailMessageFilter private constructor(val propertyFilters: Set<PropertyFilter>) {

    companion object {
        /** Return a [EmailMessageFilter.Builder] that is used to create an [EmailMessageFilter] */
        @JvmStatic
        fun builder() = Builder()
    }

    /** These are the properties of a [EmailMessage] that can be used in a [EmailMessageFilter] */
    enum class Property {
        DIRECTION,
        SEEN,
        STATE
    }

    /** Properties used in filters can be compared to the values in an [EmailMessage] with these operators. */
    enum class ComparisonOperator {
        EQUAL,
        NOT_EQUAL
    }

    sealed class PropertyFilter {
        /**
         * The filter [property] and its [value] that must match when compared according to the [comparison]
         * for a [EmailMessage] to be included in the list of results.
         */
        data class StringFilter(
            val property: Property,
            val comparison: ComparisonOperator,
            val value: String
        ) : PropertyFilter()

        data class BooleanFilter(
            val property: Property,
            val comparison: ComparisonOperator,
            val value: Boolean
        ) : PropertyFilter()

        data class LogicalAnd(
            val filters: Set<PropertyFilter>
        ) : PropertyFilter()

        data class LogicalOr(
            val filters: Set<PropertyFilter>
        ) : PropertyFilter()

        internal object Empty : PropertyFilter()
    }

    /** A Builder that is used to create a [EmailMessageFilter] */
    class Builder internal constructor() {

        private val propertyFilters = mutableSetOf<PropertyFilter>()

        /**
         * These provide a nicer syntax than specifying the enums in the lambda,
         * e.g direction equalTo outbound rather than [Property.DIRECTION] equalTo EmailMessage.Direction.OUTBOUND
         */
        val direction = Property.DIRECTION
        val seen = Property.SEEN
        val state = Property.STATE
        val inbound = EmailMessage.Direction.INBOUND.name
        val outbound = EmailMessage.Direction.OUTBOUND.name
        val sent = EmailMessage.State.SENT.name
        val received = EmailMessage.State.RECEIVED.name
        val queued = EmailMessage.State.QUEUED.name
        val delivered = EmailMessage.State.DELIVERED.name
        val undelivered = EmailMessage.State.UNDELIVERED.name
        val failed = EmailMessage.State.FAILED.name

        infix fun Property.equalTo(value: String): PropertyFilter {
            return PropertyFilter.StringFilter(this, ComparisonOperator.EQUAL, value)
        }

        infix fun Property.equalTo(value: Boolean): PropertyFilter {
            return PropertyFilter.BooleanFilter(this, ComparisonOperator.EQUAL, value)
        }

        infix fun Property.notEqualTo(value: String): PropertyFilter {
            return PropertyFilter.StringFilter(this, ComparisonOperator.NOT_EQUAL, value)
        }

        infix fun Property.notEqualTo(value: Boolean): PropertyFilter {
            return PropertyFilter.BooleanFilter(this, ComparisonOperator.NOT_EQUAL, value)
        }

        fun not(property: Property): PropertyFilter {
            return PropertyFilter.BooleanFilter(property, ComparisonOperator.NOT_EQUAL, true)
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

        fun build(): EmailMessageFilter {
            return EmailMessageFilter(propertyFilters)
        }
    }
}

/**
 * A helper function to make it easy to specify the filter of email addresses.
 * @sample com.sudoplatform.sudoemail.samples.Samples.emailMessageFilter
 */
fun filterEmailMessagesBy(
    init: EmailMessageFilter.Builder.() -> EmailMessageFilter.PropertyFilter = { EmailMessageFilter.PropertyFilter.Empty }
) = EmailMessageFilter.builder()
    .run(init)
    .build()
