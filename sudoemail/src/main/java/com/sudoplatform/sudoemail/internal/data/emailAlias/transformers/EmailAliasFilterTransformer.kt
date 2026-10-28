/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailAlias.transformers

import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.graphql.type.EmailMaskRealAddressTypeFilterInput
import com.sudoplatform.sudoemail.graphql.type.EmailMaskStatusFilterInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasRealAddressTypeFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasStatusFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EqualRealAddressTypeFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EqualStatusFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.NotEqualRealAddressTypeFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.NotEqualStatusFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.NotOneOfRealAddressTypeFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.NotOneOfStatusFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.OneOfRealAddressTypeFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.OneOfStatusFilterEntity
import com.sudoplatform.sudoemail.types.inputs.EmailAliasFilterInput
import com.sudoplatform.sudoemail.types.inputs.EqualRealAddressTypeFilter
import com.sudoplatform.sudoemail.types.inputs.EqualStatusFilter
import com.sudoplatform.sudoemail.types.inputs.NotEqualRealAddressTypeFilter
import com.sudoplatform.sudoemail.types.inputs.NotEqualStatusFilter
import com.sudoplatform.sudoemail.types.inputs.NotOneOfRealAddressTypeFilter
import com.sudoplatform.sudoemail.types.inputs.NotOneOfStatusFilter
import com.sudoplatform.sudoemail.types.inputs.OneOfRealAddressTypeFilter
import com.sudoplatform.sudoemail.types.inputs.OneOfStatusFilter
import com.sudoplatform.sudoemail.graphql.type.EmailMaskFilterInput as EmailMaskFilterInputGraphQl

/**
 * Transformer for converting email alias filter inputs between entity, GraphQL, and API representations.
 */
internal object EmailAliasFilterTransformer {
    /**
     * Transforms an API [EmailAliasFilterInput] to entity type.
     *
     * @param input [EmailAliasFilterInput] The API filter input.
     * @return [EmailAliasFilterInputEntity] The entity filter input.
     * @throws SudoEmailClient.EmailMessageException.InvalidArgumentException if an invalid filter type is provided.
     */
    fun apiToEntity(input: EmailAliasFilterInput): EmailAliasFilterInputEntity {
        var statusFilter: EmailAliasStatusFilterInputEntity? = null
        var realAddressTypeFilter: EmailAliasRealAddressTypeFilterInputEntity? = null

        // Handle status filter
        if (input.status != null) {
            statusFilter =
                when (val inputStatus = input.status) {
                    is EqualStatusFilter -> {
                        EqualStatusFilterEntity(
                            equal = EmailAliasStatusTransformer.apiToEntity(inputStatus.equal),
                        )
                    }
                    is OneOfStatusFilter -> {
                        OneOfStatusFilterEntity(
                            oneOf = inputStatus.oneOf.map { EmailAliasStatusTransformer.apiToEntity(it) },
                        )
                    }
                    is NotEqualStatusFilter -> {
                        NotEqualStatusFilterEntity(
                            notEqual = EmailAliasStatusTransformer.apiToEntity(inputStatus.notEqual),
                        )
                    }
                    is NotOneOfStatusFilter -> {
                        NotOneOfStatusFilterEntity(
                            notOneOf = inputStatus.notOneOf.map { EmailAliasStatusTransformer.apiToEntity(it) },
                        )
                    }
                    else -> {
                        throw SudoEmailClient.EmailMessageException.InvalidArgumentException("Invalid status filter type $inputStatus")
                    }
                }
        }

        // Handle realAddressType filter
        if (input.realAddressType != null) {
            realAddressTypeFilter =
                when (val inputRealAddressType = input.realAddressType) {
                    is EqualRealAddressTypeFilter -> {
                        EqualRealAddressTypeFilterEntity(
                            equal = EmailAliasRealAddressTypeTransformer.apiToEntity(inputRealAddressType.equal),
                        )
                    }
                    is OneOfRealAddressTypeFilter -> {
                        OneOfRealAddressTypeFilterEntity(
                            oneOf = inputRealAddressType.oneOf.map { EmailAliasRealAddressTypeTransformer.apiToEntity(it) },
                        )
                    }
                    is NotEqualRealAddressTypeFilter -> {
                        NotEqualRealAddressTypeFilterEntity(
                            notEqual = EmailAliasRealAddressTypeTransformer.apiToEntity(inputRealAddressType.notEqual),
                        )
                    }
                    is NotOneOfRealAddressTypeFilter -> {
                        NotOneOfRealAddressTypeFilterEntity(
                            notOneOf = inputRealAddressType.notOneOf.map { EmailAliasRealAddressTypeTransformer.apiToEntity(it) },
                        )
                    }
                    else -> {
                        throw SudoEmailClient.EmailMessageException.InvalidArgumentException(
                            "Invalid realAddressType filter type $inputRealAddressType",
                        )
                    }
                }
        }

        return EmailAliasFilterInputEntity(
            status = statusFilter,
            addressType = realAddressTypeFilter,
        )
    }

    /**
     * Transforms domain EmailAliasFilterInputEntity to GraphQL EmailMaskFilterInput.
     *
     * @param filter [EmailAliasFilterInputEntity] The domain filter to transform.
     * @return [EmailMaskFilterInputGraphQl] The GraphQL filter input, or null if no filters are set.
     * @throws SudoEmailClient.EmailAliasException.FailedException if filter transformation fails.
     */
    fun entityToGraphQl(filter: EmailAliasFilterInputEntity): EmailMaskFilterInputGraphQl {
        val status =
            if (filter.status != null) {
                when (val inputStatus = filter.status) {
                    is EqualStatusFilterEntity -> {
                        Optional.present(
                            EmailMaskStatusFilterInput(
                                eq = Optional.present(EmailAliasStatusTransformer.entityToGraphQL(inputStatus.equal)),
                            ),
                        )
                    }

                    is NotEqualStatusFilterEntity -> {
                        Optional.present(
                            EmailMaskStatusFilterInput(
                                ne = Optional.present(EmailAliasStatusTransformer.entityToGraphQL(inputStatus.notEqual)),
                            ),
                        )
                    }
                    is NotOneOfStatusFilterEntity -> {
                        Optional.present(
                            EmailMaskStatusFilterInput(
                                notIn =
                                    Optional.present(
                                        inputStatus.notOneOf.map { EmailAliasStatusTransformer.entityToGraphQL(it) },
                                    ),
                            ),
                        )
                    }
                    is OneOfStatusFilterEntity -> {
                        Optional.present(
                            EmailMaskStatusFilterInput(
                                `in` =
                                    Optional.present(
                                        inputStatus.oneOf.map { EmailAliasStatusTransformer.entityToGraphQL(it) },
                                    ),
                            ),
                        )
                    }

                    else -> {
                        Optional.absent()
                    }
                }
            } else {
                Optional.absent()
            }

        val realAddressType =
            if (filter.addressType != null) {
                when (val inputAddressType = filter.addressType) {
                    is EqualRealAddressTypeFilterEntity -> {
                        Optional.present(
                            EmailMaskRealAddressTypeFilterInput(
                                eq = Optional.present(EmailAliasRealAddressTypeTransformer.entityToGraphQL(inputAddressType.equal)),
                            ),
                        )
                    }
                    is NotEqualRealAddressTypeFilterEntity -> {
                        Optional.present(
                            EmailMaskRealAddressTypeFilterInput(
                                ne = Optional.present(EmailAliasRealAddressTypeTransformer.entityToGraphQL(inputAddressType.notEqual)),
                            ),
                        )
                    }
                    is OneOfRealAddressTypeFilterEntity -> {
                        Optional.present(
                            EmailMaskRealAddressTypeFilterInput(
                                `in` =
                                    Optional.present(
                                        inputAddressType.oneOf.map { EmailAliasRealAddressTypeTransformer.entityToGraphQL(it) },
                                    ),
                            ),
                        )
                    }
                    is NotOneOfRealAddressTypeFilterEntity -> {
                        Optional.present(
                            EmailMaskRealAddressTypeFilterInput(
                                notIn =
                                    Optional.present(
                                        inputAddressType.notOneOf.map { EmailAliasRealAddressTypeTransformer.entityToGraphQL(it) },
                                    ),
                            ),
                        )
                    }
                    // Similar handling for real address type filters can be added here
                    else -> {
                        Optional.absent()
                    }
                }
            } else {
                Optional.absent()
            }

        return try {
            EmailMaskFilterInputGraphQl(
                status = status,
                realAddressType = realAddressType,
            )
        } catch (e: Exception) {
            throw SudoEmailClient.EmailAliasException.FailedException("Failed to transform email alias filter: ${e.message}", e)
        }
    }
}
