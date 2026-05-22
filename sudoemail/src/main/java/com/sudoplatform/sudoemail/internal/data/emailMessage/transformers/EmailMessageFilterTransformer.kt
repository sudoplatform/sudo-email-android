/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage.transformers

import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.graphql.type.BooleanFilterInput
import com.sudoplatform.sudoemail.graphql.type.EmailMessageDirection
import com.sudoplatform.sudoemail.graphql.type.EmailMessageState
import com.sudoplatform.sudoemail.graphql.type.IDFilterInput
import com.sudoplatform.sudoemail.graphql.type.MailboxType
import com.sudoplatform.sudoemail.graphql.type.StringFilterInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.BooleanFieldFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageDirectionFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageDirectionFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageMailboxTypeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageStateFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageStateFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.IdFieldFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.MailboxIdsFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.StringFieldFilterInputEntity
import com.sudoplatform.sudoemail.types.inputs.BooleanFieldFilterInput
import com.sudoplatform.sudoemail.types.inputs.EmailMessageDirectionFilter
import com.sudoplatform.sudoemail.types.inputs.EmailMessageDirectionFilterInput
import com.sudoplatform.sudoemail.types.inputs.EmailMessageFilterInput
import com.sudoplatform.sudoemail.types.inputs.EmailMessageMailboxType
import com.sudoplatform.sudoemail.types.inputs.EmailMessageStateFilter
import com.sudoplatform.sudoemail.types.inputs.EmailMessageStateFilterInput
import com.sudoplatform.sudoemail.types.inputs.IdFieldFilterInput
import com.sudoplatform.sudoemail.types.inputs.MailboxIdsFilterInput
import com.sudoplatform.sudoemail.types.inputs.StringFieldFilterInput
import com.sudoplatform.sudoemail.graphql.type.EmailMessageDirectionFilterInput as EmailMessageDirectionGraphQlFilterInput
import com.sudoplatform.sudoemail.graphql.type.EmailMessageFilterInput as EmailMessageFilterGraphQlInput
import com.sudoplatform.sudoemail.graphql.type.EmailMessageStateFilterInput as EmailMessageStateGraphQlFilterInput
import com.sudoplatform.sudoemail.graphql.type.MailboxIdsFilterInput as MailboxIdsGraphQlFilterInput

internal object EmailMessageFilterTransformer {
    fun apiToEntity(input: EmailMessageFilterInput): EmailMessageFilterInputEntity =
        EmailMessageFilterInputEntity(
            id = input.id?.toEntity(),
            messageId = input.messageId?.toEntity(),
            algorithm = input.algorithm?.toEntity(),
            keyId = input.keyId?.toEntity(),
            folderId = input.folderId?.toEntity(),
            direction = input.direction?.toEntity(),
            seen = input.seen?.toEntity(),
            repliedTo = input.repliedTo?.toEntity(),
            forwarded = input.forwarded?.toEntity(),
            clientRefId = input.clientRefId?.toEntity(),
            state = input.state?.toEntity(),
            mailboxIds = input.mailboxIds?.map { it.toEntity() },
            and = input.and?.map { apiToEntity(it) },
            or = input.or?.map { apiToEntity(it) },
            not = input.not?.let { apiToEntity(it) },
        )

    fun entityToGraphQl(input: EmailMessageFilterInputEntity): EmailMessageFilterGraphQlInput? {
        val graphQlInput =
            EmailMessageFilterGraphQlInput(
                id = input.id.toGraphQl(),
                messageId = input.messageId.toGraphQl(),
                algorithm = input.algorithm.toGraphQl(),
                keyId = input.keyId.toGraphQl(),
                folderId = input.folderId.toGraphQl(),
                direction = input.direction.toGraphQl(),
                seen = input.seen.toGraphQl(),
                repliedTo = input.repliedTo.toGraphQl(),
                forwarded = input.forwarded.toGraphQl(),
                clientRefId = input.clientRefId.toGraphQl(),
                state = input.state.toGraphQl(),
                mailboxIds =
                    if (input.mailboxIds.isNullOrEmpty()) {
                        Optional.Absent
                    } else {
                        Optional.present(input.mailboxIds.map { it.toGraphQl() })
                    },
                and =
                    if (input.and.isNullOrEmpty()) {
                        Optional.Absent
                    } else {
                        Optional.present(input.and.mapNotNull { entityToGraphQl(it) })
                    },
                or =
                    if (input.or.isNullOrEmpty()) {
                        Optional.Absent
                    } else {
                        Optional.present(input.or.mapNotNull { entityToGraphQl(it) })
                    },
                not = Optional.presentIfNotNull(input.not?.let { entityToGraphQl(it) }),
            )

        return if (graphQlInput.isEmpty()) {
            null
        } else {
            graphQlInput
        }
    }

    private fun IdFieldFilterInput.toEntity(): IdFieldFilterInputEntity =
        IdFieldFilterInputEntity(
            equal = this.equal,
            notEqual = this.notEqual,
            beginsWith = this.beginsWith,
        )

    private fun StringFieldFilterInput.toEntity(): StringFieldFilterInputEntity =
        StringFieldFilterInputEntity(
            equal = this.equal,
            notEqual = this.notEqual,
            beginsWith = this.beginsWith,
        )

    private fun BooleanFieldFilterInput.toEntity(): BooleanFieldFilterInputEntity =
        BooleanFieldFilterInputEntity(
            equal = this.equal,
            notEqual = this.notEqual,
        )

    private fun EmailMessageDirectionFilterInput.toEntity(): EmailMessageDirectionFilterInputEntity =
        EmailMessageDirectionFilterInputEntity(
            equal = this.equal?.toEntity(),
            notEqual = this.notEqual?.toEntity(),
        )

    private fun EmailMessageStateFilterInput.toEntity(): EmailMessageStateFilterInputEntity =
        EmailMessageStateFilterInputEntity(
            equal = this.equal?.toEntity(),
            notEqual = this.notEqual?.toEntity(),
            oneOf = this.oneOf?.map { it.toEntity() },
            notOneOf = this.notOneOf?.map { it.toEntity() },
        )

    private fun MailboxIdsFilterInput.toEntity(): MailboxIdsFilterInputEntity =
        MailboxIdsFilterInputEntity(
            type = this.type.toEntity(),
            id = this.id.toEntity(),
        )

    private fun IdFieldFilterInputEntity?.toGraphQl(): Optional<IDFilterInput> =
        if (this == null) {
            Optional.Absent
        } else {
            Optional.present(
                IDFilterInput(
                    eq = Optional.presentIfNotNull(this.equal),
                    ne = Optional.presentIfNotNull(this.notEqual),
                    beginsWith = Optional.presentIfNotNull(this.beginsWith),
                ),
            )
        }

    private fun StringFieldFilterInputEntity?.toGraphQl(): Optional<StringFilterInput> =
        if (this == null) {
            Optional.Absent
        } else {
            Optional.present(
                StringFilterInput(
                    eq = Optional.presentIfNotNull(this.equal),
                    ne = Optional.presentIfNotNull(this.notEqual),
                    beginsWith = Optional.presentIfNotNull(this.beginsWith),
                ),
            )
        }

    private fun BooleanFieldFilterInputEntity?.toGraphQl(): Optional<BooleanFilterInput> =
        if (this == null) {
            Optional.Absent
        } else {
            Optional.present(
                BooleanFilterInput(
                    eq = Optional.presentIfNotNull(this.equal),
                    ne = Optional.presentIfNotNull(this.notEqual),
                ),
            )
        }

    private fun EmailMessageDirectionFilterInputEntity?.toGraphQl(): Optional<EmailMessageDirectionGraphQlFilterInput> =
        if (this == null) {
            Optional.Absent
        } else {
            Optional.present(
                EmailMessageDirectionGraphQlFilterInput(
                    eq = Optional.presentIfNotNull(this.equal?.toGraphQl()),
                    ne = Optional.presentIfNotNull(this.notEqual?.toGraphQl()),
                ),
            )
        }

    private fun EmailMessageStateFilterInputEntity?.toGraphQl(): Optional<EmailMessageStateGraphQlFilterInput> =
        if (this == null) {
            Optional.Absent
        } else {
            Optional.present(
                EmailMessageStateGraphQlFilterInput(
                    eq = Optional.presentIfNotNull(this.equal?.toGraphQl()),
                    ne = Optional.presentIfNotNull(this.notEqual?.toGraphQl()),
                    `in` = Optional.presentIfNotNull(this.oneOf?.map { it.toGraphQl() }),
                    notIn = Optional.presentIfNotNull(this.notOneOf?.map { it.toGraphQl() }),
                ),
            )
        }

    private fun MailboxIdsFilterInputEntity.toGraphQl(): MailboxIdsGraphQlFilterInput =
        MailboxIdsGraphQlFilterInput(
            type = this.type.toGraphQl(),
            id =
                StringFilterInput(
                    eq = Optional.presentIfNotNull(this.id.equal),
                    ne = Optional.presentIfNotNull(this.id.notEqual),
                    beginsWith = Optional.presentIfNotNull(this.id.beginsWith),
                ),
        )

    private fun EmailMessageDirectionFilter.toEntity(): EmailMessageDirectionFilterEntity =
        when (this) {
            EmailMessageDirectionFilter.INBOUND -> EmailMessageDirectionFilterEntity.INBOUND
            EmailMessageDirectionFilter.OUTBOUND -> EmailMessageDirectionFilterEntity.OUTBOUND
            EmailMessageDirectionFilter.UNKNOWN -> EmailMessageDirectionFilterEntity.UNKNOWN
        }

    private fun EmailMessageStateFilter.toEntity(): EmailMessageStateFilterEntity =
        when (this) {
            EmailMessageStateFilter.QUEUED -> EmailMessageStateFilterEntity.QUEUED
            EmailMessageStateFilter.SENT -> EmailMessageStateFilterEntity.SENT
            EmailMessageStateFilter.DELIVERED -> EmailMessageStateFilterEntity.DELIVERED
            EmailMessageStateFilter.UNDELIVERED -> EmailMessageStateFilterEntity.UNDELIVERED
            EmailMessageStateFilter.FAILED -> EmailMessageStateFilterEntity.FAILED
            EmailMessageStateFilter.RECEIVED -> EmailMessageStateFilterEntity.RECEIVED
            EmailMessageStateFilter.DELETED -> EmailMessageStateFilterEntity.DELETED
            EmailMessageStateFilter.UNKNOWN -> EmailMessageStateFilterEntity.UNKNOWN
        }

    private fun EmailMessageMailboxType.toEntity(): EmailMessageMailboxTypeEntity =
        when (this) {
            EmailMessageMailboxType.ADDRESS -> EmailMessageMailboxTypeEntity.ADDRESS
            EmailMessageMailboxType.MASK -> EmailMessageMailboxTypeEntity.MASK
        }

    private fun EmailMessageDirectionFilterEntity.toGraphQl(): EmailMessageDirection =
        when (this) {
            EmailMessageDirectionFilterEntity.INBOUND -> EmailMessageDirection.INBOUND
            EmailMessageDirectionFilterEntity.OUTBOUND -> EmailMessageDirection.OUTBOUND
            EmailMessageDirectionFilterEntity.UNKNOWN -> EmailMessageDirection.UNKNOWN__
        }

    private fun EmailMessageStateFilterEntity.toGraphQl(): EmailMessageState =
        when (this) {
            EmailMessageStateFilterEntity.QUEUED -> EmailMessageState.QUEUED
            EmailMessageStateFilterEntity.SENT -> EmailMessageState.SENT
            EmailMessageStateFilterEntity.DELIVERED -> EmailMessageState.DELIVERED
            EmailMessageStateFilterEntity.UNDELIVERED -> EmailMessageState.UNDELIVERED
            EmailMessageStateFilterEntity.FAILED -> EmailMessageState.FAILED
            EmailMessageStateFilterEntity.RECEIVED -> EmailMessageState.RECEIVED
            EmailMessageStateFilterEntity.DELETED -> EmailMessageState.DELETED
            EmailMessageStateFilterEntity.UNKNOWN -> EmailMessageState.UNKNOWN__
        }

    private fun EmailMessageMailboxTypeEntity.toGraphQl(): MailboxType =
        when (this) {
            EmailMessageMailboxTypeEntity.ADDRESS -> MailboxType.ADDRESS
            EmailMessageMailboxTypeEntity.MASK -> MailboxType.MASK
        }

    private fun EmailMessageFilterGraphQlInput.isEmpty(): Boolean =
        id is Optional.Absent &&
            messageId is Optional.Absent &&
            algorithm is Optional.Absent &&
            keyId is Optional.Absent &&
            folderId is Optional.Absent &&
            direction is Optional.Absent &&
            seen is Optional.Absent &&
            repliedTo is Optional.Absent &&
            forwarded is Optional.Absent &&
            clientRefId is Optional.Absent &&
            state is Optional.Absent &&
            mailboxIds is Optional.Absent &&
            and is Optional.Absent &&
            or is Optional.Absent &&
            not is Optional.Absent
}
