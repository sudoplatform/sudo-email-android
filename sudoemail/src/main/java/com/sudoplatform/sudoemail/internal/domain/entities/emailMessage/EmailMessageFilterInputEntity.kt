/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

internal enum class EmailMessageMailboxTypeEntity {
    ADDRESS,
    MASK,
}

internal enum class EmailMessageDirectionFilterEntity {
    INBOUND,
    OUTBOUND,
    UNKNOWN,
}

internal enum class EmailMessageStateFilterEntity {
    QUEUED,
    SENT,
    DELIVERED,
    UNDELIVERED,
    FAILED,
    RECEIVED,
    DELETED,
    UNKNOWN,
}

internal data class IdFieldFilterInputEntity(
    val equal: String? = null,
    val notEqual: String? = null,
    val beginsWith: String? = null,
)

internal data class StringFieldFilterInputEntity(
    val equal: String? = null,
    val notEqual: String? = null,
    val beginsWith: String? = null,
)

internal data class BooleanFieldFilterInputEntity(
    val equal: Boolean? = null,
    val notEqual: Boolean? = null,
)

internal data class EmailMessageDirectionFilterInputEntity(
    val equal: EmailMessageDirectionFilterEntity? = null,
    val notEqual: EmailMessageDirectionFilterEntity? = null,
)

internal data class EmailMessageStateFilterInputEntity(
    val equal: EmailMessageStateFilterEntity? = null,
    val notEqual: EmailMessageStateFilterEntity? = null,
    val oneOf: List<EmailMessageStateFilterEntity>? = null,
    val notOneOf: List<EmailMessageStateFilterEntity>? = null,
)

internal data class MailboxIdsFilterInputEntity(
    val type: EmailMessageMailboxTypeEntity,
    val id: StringFieldFilterInputEntity,
)

internal data class EmailMessageFilterInputEntity(
    val id: IdFieldFilterInputEntity? = null,
    val messageId: IdFieldFilterInputEntity? = null,
    val algorithm: StringFieldFilterInputEntity? = null,
    val keyId: IdFieldFilterInputEntity? = null,
    val folderId: IdFieldFilterInputEntity? = null,
    val direction: EmailMessageDirectionFilterInputEntity? = null,
    val seen: BooleanFieldFilterInputEntity? = null,
    val repliedTo: BooleanFieldFilterInputEntity? = null,
    val forwarded: BooleanFieldFilterInputEntity? = null,
    val clientRefId: IdFieldFilterInputEntity? = null,
    val state: EmailMessageStateFilterInputEntity? = null,
    val mailboxIds: List<MailboxIdsFilterInputEntity>? = null,
    val and: List<EmailMessageFilterInputEntity>? = null,
    val or: List<EmailMessageFilterInputEntity>? = null,
    val not: EmailMessageFilterInputEntity? = null,
)
