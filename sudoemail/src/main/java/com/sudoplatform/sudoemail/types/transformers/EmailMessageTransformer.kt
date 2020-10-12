/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import android.text.util.Rfc822Tokenizer
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.graphql.GetEmailMessageQuery
import com.sudoplatform.sudoemail.graphql.ListEmailMessagesQuery
import com.sudoplatform.sudoemail.graphql.OnEmailMessageCreatedSubscription
import com.sudoplatform.sudoemail.graphql.OnEmailMessageDeletedSubscription
import com.sudoplatform.sudoemail.graphql.type.BooleanFilterInput
import com.sudoplatform.sudoemail.graphql.type.EmailMessageDirection
import com.sudoplatform.sudoemail.graphql.type.EmailMessageDirectionFilterInput
import com.sudoplatform.sudoemail.graphql.type.EmailMessageFilterInput
import com.sudoplatform.sudoemail.graphql.type.EmailMessageState
import com.sudoplatform.sudoemail.graphql.type.EmailMessageStateFilterInput
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.types.EmailMessage.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.inputs.filters.EmailMessageFilter
import java.util.Locale

/**
 * Transformer responsible for transforming the [EmailMessage] GraphQL data
 * types to the entity type that is exposed to users.
 *
 * @since 2020-08-11
 */
internal object EmailMessageTransformer {

    /** Locale to use when comparing enum names */
    private val locale = Locale.ENGLISH

    /**
     * Transform the results of the [GetEmailMessageQuery].
     *
     * @param deviceKeyManager Manages the local storage and lifecycle of key pairs.
     * @param result The GraphQL query results.
     * @return The [EmailMessage] entity type.
     */
    fun toEntityFromGetEmailMessageQueryResult(
        deviceKeyManager: DeviceKeyManager,
        result: GetEmailMessageQuery.GetEmailMessage
    ): EmailMessage {
        val unsealer = Unsealer(deviceKeyManager, result.keyId(), result.algorithm())
        return EmailMessage(
            messageId = result.messageId(),
            clientRefId = result.clientRefId(),
            userId = result.userId(),
            sudoId = result.sudoId(),
            emailAddressId = result.emailAddressId(),
            seen = result.seen(),
            direction = result.direction().toEmailMessageDirection(),
            state = result.state().toEmailMessageState(),
            from = unsealer.unsealEmailAddresses(result.from()),
            to = unsealer.unsealEmailAddresses(result.to()),
            cc = unsealer.unsealEmailAddresses(result.cc()),
            bcc = unsealer.unsealEmailAddresses(result.bcc()),
            replyTo = unsealer.unsealEmailAddresses(result.replyTo()),
            subject = result.subject()?.let { unsealer.unseal(it) },
            createdAt = result.createdAtEpochMs().toDate(),
            updatedAt = result.updatedAtEpochMs().toDate(),
            id = result.id(),
            keyId = result.keyId(),
            algorithm = result.algorithm()
        )
    }

    /**
     * Transform the results of the [ListEmailMessagesQuery].
     *
     * @param deviceKeyManager Manages the local storage and lifecycle of key pairs.
     * @param result The GraphQL query results.
     * @return The list of [EmailMessage]es entity type.
     */
    fun toEntityFromListEmailMessagesQueryResult(
        deviceKeyManager: DeviceKeyManager,
        result: List<ListEmailMessagesQuery.Item>
    ): List<EmailMessage> {
        return result.map { emailMessage ->
            val unsealer = Unsealer(deviceKeyManager, emailMessage.keyId(), emailMessage.algorithm())
            EmailMessage(
                messageId = emailMessage.messageId(),
                clientRefId = emailMessage.clientRefId(),
                userId = emailMessage.userId(),
                sudoId = emailMessage.sudoId(),
                emailAddressId = emailMessage.emailAddressId(),
                seen = emailMessage.seen(),
                direction = emailMessage.direction().toEmailMessageDirection(),
                state = emailMessage.state().toEmailMessageState(),
                from = unsealer.unsealEmailAddresses(emailMessage.from()),
                to = unsealer.unsealEmailAddresses(emailMessage.to()),
                cc = unsealer.unsealEmailAddresses(emailMessage.cc()),
                bcc = unsealer.unsealEmailAddresses(emailMessage.bcc()),
                replyTo = unsealer.unsealEmailAddresses(emailMessage.replyTo()),
                subject = emailMessage.subject()?.let { unsealer.unseal(it) },
                createdAt = emailMessage.createdAtEpochMs().toDate(),
                updatedAt = emailMessage.updatedAtEpochMs().toDate(),
                id = emailMessage.id(),
                keyId = emailMessage.keyId(),
                algorithm = emailMessage.algorithm()
            )
        }.toList()
    }

    private fun EmailMessageDirection.toEmailMessageDirection(): EmailMessage.Direction {
        for (value in EmailMessage.Direction.values()) {
            if (value.name == this.name) {
                return value
            }
        }
        return EmailMessage.Direction.UNKNOWN
    }

    private fun EmailMessageState.toEmailMessageState(): EmailMessage.State {
        for (value in EmailMessage.State.values()) {
            if (value.name == this.name) {
                return value
            }
        }
        return EmailMessage.State.UNKNOWN
    }

    /**
     * Convert from the API definition of the email message filter to the GraphQL definition.
     *
     * @param filter The API definition of the email message filter, can be null.
     * @return The GraphQL definition of an email message filter, can be null.
     */
    fun toGraphQLFilter(filter: EmailMessageFilter?): EmailMessageFilterInput? {
        if (filter == null || filter.propertyFilters.isEmpty()) {
            return null
        }
        val builder = EmailMessageFilterInput.builder()
        for (field in filter.propertyFilters) {
            when (field) {
                is EmailMessageFilter.PropertyFilter.LogicalAnd -> {
                    builder.and(field.filters.map { transform(it) })
                }
                is EmailMessageFilter.PropertyFilter.LogicalOr -> {
                    builder.or(field.filters.map { transform(it) })
                }
                is EmailMessageFilter.PropertyFilter.StringFilter -> {
                    when (field.property) {
                        EmailMessageFilter.Property.DIRECTION -> builder.direction(field.toDirectionFilterInput())
                        EmailMessageFilter.Property.STATE -> builder.state(field.toStateFilterInput())
                        else -> throw SudoEmailClient.EmailMessageException.InvalidMessageFilterException(
                            "string value cannot be used as a filter for ${field.property}"
                        )
                    }
                }
                is EmailMessageFilter.PropertyFilter.BooleanFilter -> {
                    when (field.property) {
                        EmailMessageFilter.Property.SEEN -> builder.seen(field.toBooleanFilterInput())
                        else -> throw SudoEmailClient.EmailMessageException.InvalidMessageFilterException(
                            "boolean value cannot be used as a filter for ${field.property}"
                        )
                    }
                }
            }
        }
        return builder.build()
    }

    private fun transform(filter: EmailMessageFilter.PropertyFilter): EmailMessageFilterInput {
        return when (filter) {
            is EmailMessageFilter.PropertyFilter.LogicalAnd -> {
                EmailMessageFilterInput.builder()
                    .and(filter.filters.map {
                            it.toEmailMessageFilterInput()
                        }
                    )
                    .build()
            }
            is EmailMessageFilter.PropertyFilter.LogicalOr -> {
                EmailMessageFilterInput.builder()
                    .or(filter.filters.map {
                            it.toEmailMessageFilterInput()
                        }
                    )
                    .build()
            }
            is EmailMessageFilter.PropertyFilter.StringFilter -> {
                when (filter.property) {
                    EmailMessageFilter.Property.DIRECTION -> {
                        EmailMessageFilterInput.builder()
                            .direction(filter.toDirectionFilterInput())
                            .build()
                    }
                    EmailMessageFilter.Property.STATE -> {
                        EmailMessageFilterInput.builder()
                            .state(filter.toStateFilterInput())
                            .build()
                    }
                    else -> throw SudoEmailClient.EmailMessageException.InvalidMessageFilterException(
                        "string value cannot be used as a filter for ${filter.property}"
                    )
                }
            }
            is EmailMessageFilter.PropertyFilter.BooleanFilter -> {
                when (filter.property) {
                    EmailMessageFilter.Property.SEEN -> {
                        EmailMessageFilterInput.builder()
                            .seen(filter.toBooleanFilterInput())
                            .build()
                    }
                    else -> throw SudoEmailClient.EmailMessageException.InvalidMessageFilterException(
                        "boolean value cannot be used as a filter for ${filter.property}"
                    )
                }
            }
            is EmailMessageFilter.PropertyFilter.Empty -> throw SudoEmailClient.EmailMessageException.InvalidMessageFilterException(
                "Do not use the internal sentinel Empty"
            )
        }
    }

    private fun EmailMessageFilter.PropertyFilter.toEmailMessageFilterInput(): EmailMessageFilterInput {
        return when (this) {
            is EmailMessageFilter.PropertyFilter.LogicalAnd -> {
                EmailMessageFilterInput.builder()
                    .and(this.filters.map {
                        it.toEmailMessageFilterInput()
                    })
                    .build()
            }
            is EmailMessageFilter.PropertyFilter.LogicalOr -> {
                EmailMessageFilterInput.builder()
                    .or(this.filters.map {
                        it.toEmailMessageFilterInput()
                    })
                    .build()
            }
            is EmailMessageFilter.PropertyFilter.StringFilter -> this.toEmailMessageFilterInput()
            is EmailMessageFilter.PropertyFilter.BooleanFilter -> this.toEmailMessageFilterInput()
            is EmailMessageFilter.PropertyFilter.Empty -> throw SudoEmailClient.EmailMessageException.InvalidMessageFilterException(
                "Do not use the internal sentinel Empty"
            )
        }
    }

    private fun EmailMessageFilter.PropertyFilter.StringFilter.toEmailMessageFilterInput(): EmailMessageFilterInput {
        return when (property) {
            EmailMessageFilter.Property.DIRECTION -> EmailMessageFilterInput.builder()
                .direction(toDirectionFilterInput())
                .build()
            EmailMessageFilter.Property.STATE -> EmailMessageFilterInput.builder()
                .state(toStateFilterInput())
                .build()
            else -> throw SudoEmailClient.EmailMessageException.InvalidMessageFilterException(
                "string value cannot be used as a filter for $property"
            )
        }
    }

    private fun EmailMessageFilter.PropertyFilter.BooleanFilter.toEmailMessageFilterInput(): EmailMessageFilterInput {
        return when (property) {
            EmailMessageFilter.Property.SEEN -> EmailMessageFilterInput.builder()
                .seen(toBooleanFilterInput())
                .build()
            else -> throw SudoEmailClient.EmailMessageException.InvalidMessageFilterException(
                "boolean value cannot be used as a filter for $property"
            )
        }
    }
    private fun EmailMessageFilter.PropertyFilter.StringFilter.toDirectionFilterInput(): EmailMessageDirectionFilterInput {
        val builder = EmailMessageDirectionFilterInput.builder()
        when (comparison) {
            EmailMessageFilter.ComparisonOperator.EQUAL -> builder.eq(value.toDirection())
            EmailMessageFilter.ComparisonOperator.NOT_EQUAL -> builder.ne(value.toDirection())
        }
        return builder.build()
    }

    private fun EmailMessageFilter.PropertyFilter.BooleanFilter.toBooleanFilterInput(): BooleanFilterInput {
        val builder = BooleanFilterInput.builder()
        when (comparison) {
            EmailMessageFilter.ComparisonOperator.EQUAL -> builder.eq(value)
            EmailMessageFilter.ComparisonOperator.NOT_EQUAL -> builder.ne(value)
        }
        return builder.build()
    }

    private fun EmailMessageFilter.PropertyFilter.StringFilter.toStateFilterInput(): EmailMessageStateFilterInput {
        val builder = EmailMessageStateFilterInput.builder()
        when (comparison) {
            EmailMessageFilter.ComparisonOperator.EQUAL -> builder.eq(value.toState())
            EmailMessageFilter.ComparisonOperator.NOT_EQUAL -> builder.ne(value.toState())
        }
        return builder.build()
    }

    private fun String.toDirection(): EmailMessageDirection {
        for (value in EmailMessageDirection.values()) {
            if (value.name.toLowerCase(locale) == this.toLowerCase(locale)) {
                return value
            }
        }
        throw SudoEmailClient.EmailMessageException.InvalidMessageFilterException(
            "Value specified for the direction filter should be inbound or outbound"
        )
    }

    private fun String.toState(): EmailMessageState {
        for (value in EmailMessageState.values()) {
            if (value.name.toLowerCase(locale) == this.toLowerCase(locale)) {
                return value
            }
        }
        throw SudoEmailClient.EmailMessageException.InvalidMessageFilterException(
            "Value specified for the state filter was not recognized"
        )
    }

    /**
     * Unseal the sealed Base64 encoded RFC 822 bytes.
     */
    fun toUnsealedRfc822Data(
        deviceKeyManager: DeviceKeyManager,
        keyId: String,
        algorithm: String,
        sealedBase64Rfc822Data: ByteArray
    ): ByteArray? {
        val unsealer = Unsealer(deviceKeyManager, keyId, algorithm)
        return unsealer.unsealBytes(sealedBase64Rfc822Data)
    }

    /**
     * Convert from the GraphQL subscription version of a new email message to the one we export.
     */
    fun toEntityFromCreateSubscription(
        deviceKeyManager: DeviceKeyManager,
        newMessage: OnEmailMessageCreatedSubscription.OnEmailMessageCreated
    ): EmailMessage {
        val unsealer = Unsealer(deviceKeyManager, newMessage.keyId(), newMessage.algorithm())
        return EmailMessage(
            messageId = newMessage.messageId(),
            clientRefId = newMessage.clientRefId(),
            userId = newMessage.userId(),
            sudoId = newMessage.sudoId(),
            emailAddressId = newMessage.emailAddressId(),
            seen = newMessage.seen(),
            direction = newMessage.direction().toEmailMessageDirection(),
            state = newMessage.state().toEmailMessageState(),
            from = unsealer.unsealEmailAddresses(newMessage.from()),
            to = unsealer.unsealEmailAddresses(newMessage.to()),
            replyTo = unsealer.unsealEmailAddresses(newMessage.replyTo()),
            cc = unsealer.unsealEmailAddresses(newMessage.cc()),
            bcc = unsealer.unsealEmailAddresses(newMessage.bcc()),
            subject = newMessage.subject()?.let { unsealer.unseal(it) },
            createdAt = newMessage.createdAtEpochMs().toDate(),
            updatedAt = newMessage.updatedAtEpochMs().toDate(),
            id = newMessage.id(),
            keyId = newMessage.keyId(),
            algorithm = newMessage.algorithm()
        )
    }

    /**
     * Convert from the GraphQL subscription version of a deleted email message to the one we export.
     */
    fun toEntityFromDeleteSubscription(
        deviceKeyManager: DeviceKeyManager,
        deletedMessage: OnEmailMessageDeletedSubscription.OnEmailMessageDeleted
    ): EmailMessage {
        val unsealer = Unsealer(deviceKeyManager, deletedMessage.keyId(), deletedMessage.algorithm())
        return EmailMessage(
            messageId = deletedMessage.messageId(),
            clientRefId = deletedMessage.clientRefId(),
            userId = deletedMessage.userId(),
            sudoId = deletedMessage.sudoId(),
            emailAddressId = deletedMessage.emailAddressId(),
            seen = deletedMessage.seen(),
            direction = deletedMessage.direction().toEmailMessageDirection(),
            state = deletedMessage.state().toEmailMessageState(),
            from = unsealer.unsealEmailAddresses(deletedMessage.from()),
            to = unsealer.unsealEmailAddresses(deletedMessage.to()),
            replyTo = unsealer.unsealEmailAddresses(deletedMessage.replyTo()),
            cc = unsealer.unsealEmailAddresses(deletedMessage.cc()),
            bcc = unsealer.unsealEmailAddresses(deletedMessage.bcc()),
            subject = deletedMessage.subject()?.let { unsealer.unseal(it) },
            createdAt = deletedMessage.createdAtEpochMs().toDate(),
            updatedAt = deletedMessage.updatedAtEpochMs().toDate(),
            id = deletedMessage.id(),
            keyId = deletedMessage.keyId(),
            algorithm = deletedMessage.algorithm()
        )
    }

    /**
     * Transform a string that might contain an RFC822 email address and display name into an [EmailAddress]
     */
    fun toEmailAddress(value: String): EmailAddress? {
        Rfc822Tokenizer.tokenize(value).firstOrNull() {
            val address = it.address
            return if (address.isNullOrBlank()) {
                null
            } else if (it.name.isNullOrBlank()) {
                EmailAddress(address)
            } else {
                EmailAddress(address, it.name)
            }
        }
        return null
    }
}
