/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Mailbox type used when filtering by mailbox identifiers.
 */
enum class EmailMessageMailboxType {
    ADDRESS,
    MASK,
}

/**
 * Direction filter values for email messages.
 */
enum class EmailMessageDirectionFilter {
    INBOUND,
    OUTBOUND,
    UNKNOWN,
}

/**
 * State filter values for email messages.
 */
enum class EmailMessageStateFilter {
    QUEUED,
    SENT,
    DELIVERED,
    UNDELIVERED,
    FAILED,
    RECEIVED,
    DELETED,
    UNKNOWN,
}

/**
 * Filter for ID-based fields.
 */
data class IdFieldFilterInput(
    val equal: String? = null,
    val notEqual: String? = null,
    val beginsWith: String? = null,
)

/**
 * Filter for string-based fields.
 */
data class StringFieldFilterInput(
    val equal: String? = null,
    val notEqual: String? = null,
    val beginsWith: String? = null,
)

/**
 * Filter for boolean-based fields.
 */
data class BooleanFieldFilterInput(
    val equal: Boolean? = null,
    val notEqual: Boolean? = null,
)

/**
 * Filter for direction values.
 */
data class EmailMessageDirectionFilterInput(
    val equal: EmailMessageDirectionFilter? = null,
    val notEqual: EmailMessageDirectionFilter? = null,
)

/**
 * Filter for state values.
 */
data class EmailMessageStateFilterInput(
    val equal: EmailMessageStateFilter? = null,
    val notEqual: EmailMessageStateFilter? = null,
    val oneOf: List<EmailMessageStateFilter>? = null,
    val notOneOf: List<EmailMessageStateFilter>? = null,
)

/**
 * Filter for mailbox identifiers.
 */
data class MailboxIdsFilterInput(
    val type: EmailMessageMailboxType,
    val id: StringFieldFilterInput,
)

/**
 * Filter object used with list email message APIs that support filtering.
 */
data class EmailMessageFilterInput(
    val id: IdFieldFilterInput? = null,
    val messageId: IdFieldFilterInput? = null,
    val algorithm: StringFieldFilterInput? = null,
    val keyId: IdFieldFilterInput? = null,
    val folderId: IdFieldFilterInput? = null,
    val direction: EmailMessageDirectionFilterInput? = null,
    val seen: BooleanFieldFilterInput? = null,
    val repliedTo: BooleanFieldFilterInput? = null,
    val forwarded: BooleanFieldFilterInput? = null,
    val clientRefId: IdFieldFilterInput? = null,
    val state: EmailMessageStateFilterInput? = null,
    val mailboxIds: List<MailboxIdsFilterInput>? = null,
    val and: List<EmailMessageFilterInput>? = null,
    val or: List<EmailMessageFilterInput>? = null,
    val not: EmailMessageFilterInput? = null,
)
