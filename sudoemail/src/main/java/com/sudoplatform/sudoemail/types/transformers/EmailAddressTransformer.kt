/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.graphql.DeprovisionEmailAddressMutation
import com.sudoplatform.sudoemail.graphql.ProvisionEmailAddressMutation
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.Owner
import com.sudoplatform.sudoemail.graphql.GetEmailAddressQuery
import com.sudoplatform.sudoemail.graphql.ListEmailAddressesQuery
import com.sudoplatform.sudoemail.graphql.type.EmailAddressFilterInput
import com.sudoplatform.sudoemail.graphql.type.StringFilterInput
import com.sudoplatform.sudoemail.types.inputs.filters.EmailAddressFilter

/**
 * Transformer responsible for transforming the [EmailAddress] GraphQL data
 * types to the entity type that is exposed to users.
 *
 * @since 2020-08-05
 */
internal object EmailAddressTransformer {

    /**
     * Transform the results of the [ProvisionEmailAddressMutation].
     *
     * @param result The GraphQL mutation results.
     * @return The [EmailAddress] entity type.
     */
    fun toEntityFromProvisionEmailAddressMutationResult(
        result: ProvisionEmailAddressMutation.ProvisionEmailAddress
    ): EmailAddress {
        return EmailAddress(
            id = result.id(),
            emailAddress = result.emailAddress(),
            userId = result.userId(),
            sudoId = result.sudoId(),
            owners = result.owners().toProvisionEmailAddressOwners(),
            createdAt = result.createdAtEpochMs().toDate(),
            updatedAt = result.updatedAtEpochMs().toDate()
        )
    }

    /**
     * Transform the results of the [DeprovisionEmailAddressMutation].
     *
     * @param result The GraphQL mutation results.
     * @return The [EmailAddress] entity type.
     */
    fun toEntityFromDeprovisionEmailAddressMutationResult(
        result: DeprovisionEmailAddressMutation.DeprovisionEmailAddress
    ): EmailAddress {
        return EmailAddress(
            id = result.id(),
            emailAddress = result.emailAddress(),
            userId = result.userId(),
            sudoId = result.sudoId(),
            owners = result.owners().toDeprovisionEmailAddressOwners(),
            createdAt = result.createdAtEpochMs().toDate(),
            updatedAt = result.updatedAtEpochMs().toDate()
        )
    }

    /**
     * Transform the results of the [GetEmailAddressQuery].
     *
     * @param result The GraphQL query results.
     * @return The [EmailAddress] entity type.
     */
    fun toEntityFromGetEmailAddressQueryResult(result: GetEmailAddressQuery.GetEmailAddress): EmailAddress {
        return EmailAddress(
            id = result.id(),
            emailAddress = result.emailAddress(),
            userId = result.userId(),
            sudoId = result.sudoId(),
            owners = result.owners().toGetEmailAddressOwners(),
            createdAt = result.createdAtEpochMs().toDate(),
            updatedAt = result.updatedAtEpochMs().toDate()
        )
    }

    /**
     * Transform the results of the [ListEmailAddressesQuery].
     *
     * @param result The GraphQL query results.
     * @return The list of [EmailAddress]es entity type.
     */
    fun toEntityFromListEmailAddressesQueryResult(result: List<ListEmailAddressesQuery.Item>): List<EmailAddress> {
        return result.map { emailAddress ->
            EmailAddress(
                id = emailAddress.id(),
                emailAddress = emailAddress.emailAddress(),
                userId = emailAddress.userId(),
                sudoId = emailAddress.sudoId(),
                owners = emailAddress.owners().toListEmailAddressesOwners(),
                createdAt = emailAddress.createdAtEpochMs().toDate(),
                updatedAt = emailAddress.updatedAtEpochMs().toDate()
            )
        }.toList()
    }

    private fun List<ProvisionEmailAddressMutation.Owner>.toProvisionEmailAddressOwners(): List<Owner> {
        return this.map {
            it.toOwner()
        }
    }

    private fun ProvisionEmailAddressMutation.Owner.toOwner(): Owner {
        return Owner(id = id(), issuer = issuer())
    }

    private fun List<DeprovisionEmailAddressMutation.Owner>.toDeprovisionEmailAddressOwners(): List<Owner> {
        return this.map {
            it.toOwner()
        }
    }

    private fun DeprovisionEmailAddressMutation.Owner.toOwner(): Owner {
        return Owner(id = id(), issuer = issuer())
    }

    private fun List<GetEmailAddressQuery.Owner>.toGetEmailAddressOwners(): List<Owner> {
        return this.map {
            it.toOwner()
        }
    }

    private fun GetEmailAddressQuery.Owner.toOwner(): Owner {
        return Owner(id = id(), issuer = issuer())
    }

    private fun List<ListEmailAddressesQuery.Owner>.toListEmailAddressesOwners(): List<Owner> {
        return this.map {
            it.toOwner()
        }
    }

    private fun ListEmailAddressesQuery.Owner.toOwner(): Owner {
        return Owner(id = id(), issuer = issuer())
    }

    /**
     * Convert from the API definition of the email address filter to the GraphQL definition.
     *
     * @param filter The API definition of the email address filter, can be null.
     * @return The GraphQL definition of an email address filter, can be null.
     */
    fun toGraphQLFilter(filter: EmailAddressFilter?): EmailAddressFilterInput? {
        if (filter == null || filter.propertyFilters.isEmpty()) {
            return null
        }
        val builder = EmailAddressFilterInput.builder()
        for (field in filter.propertyFilters) {
            when (field) {
                is EmailAddressFilter.PropertyFilter.LogicalAnd -> {
                    builder.and(field.filters.map { transform(it) })
                }
                is EmailAddressFilter.PropertyFilter.LogicalOr -> {
                    builder.or(field.filters.map { transform(it) })
                }
                is EmailAddressFilter.PropertyFilter.StringFilter -> {
                    when (field.property) {
                        EmailAddressFilter.Property.EMAIL_ADDRESS -> builder.emailAddress(field.toAddressFilterInput())
                    }
                }
            }
        }
        return builder.build()
    }

    private fun transform(filter: EmailAddressFilter.PropertyFilter): EmailAddressFilterInput {
        return when (filter) {
            is EmailAddressFilter.PropertyFilter.LogicalAnd -> {
                EmailAddressFilterInput.builder()
                    .and(filter.filters.map {
                        it.toEmailAddressFilterInput()
                    })
                    .build()
            }
            is EmailAddressFilter.PropertyFilter.LogicalOr -> {
                EmailAddressFilterInput.builder()
                    .or(filter.filters.map {
                        it.toEmailAddressFilterInput()
                    })
                    .build()
            }
            is EmailAddressFilter.PropertyFilter.StringFilter -> {
                when (filter.property) {
                    EmailAddressFilter.Property.EMAIL_ADDRESS -> {
                        EmailAddressFilterInput.builder()
                            .emailAddress(filter.toAddressFilterInput())
                            .build()
                    }
                }
            }
            is EmailAddressFilter.PropertyFilter.Empty -> throw SudoEmailClient.EmailAddressException.InvalidAddressFilterException(
                "Do not use the internal sentinel Empty"
            )
        }
    }

    private fun EmailAddressFilter.PropertyFilter.toEmailAddressFilterInput(): EmailAddressFilterInput {
        return when (this) {
            is EmailAddressFilter.PropertyFilter.LogicalAnd -> {
                EmailAddressFilterInput.builder()
                    .and(this.filters.map {
                        it.toEmailAddressFilterInput()
                    })
                    .build()
            }
            is EmailAddressFilter.PropertyFilter.LogicalOr -> {
                EmailAddressFilterInput.builder()
                    .or(this.filters.map {
                        it.toEmailAddressFilterInput()
                    })
                    .build()
            }
            is EmailAddressFilter.PropertyFilter.StringFilter -> this.toEmailAddressFilterInput()
            is EmailAddressFilter.PropertyFilter.Empty -> throw SudoEmailClient.EmailAddressException.InvalidAddressFilterException(
                "Do not use the internal sentinel Empty"
            )
        }
    }

    private fun EmailAddressFilter.PropertyFilter.StringFilter.toEmailAddressFilterInput(): EmailAddressFilterInput {
        return when (property) {
            EmailAddressFilter.Property.EMAIL_ADDRESS -> EmailAddressFilterInput.builder()
                .emailAddress(toAddressFilterInput())
                .build()
        }
    }

    private fun EmailAddressFilter.PropertyFilter.StringFilter.toAddressFilterInput(): StringFilterInput {
        val builder = StringFilterInput.builder()
        when (comparison) {
            EmailAddressFilter.ComparisonOperator.EQUAL -> builder.eq(value)
            EmailAddressFilter.ComparisonOperator.NOT_EQUAL -> builder.ne(value)
            EmailAddressFilter.ComparisonOperator.BEGINS_WITH -> builder.beginsWith(value)
        }
        return builder.build()
    }
}
