/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMask.transformers

import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.graphql.type.EmailMaskRealAddressTypeFilterInput
import com.sudoplatform.sudoemail.graphql.type.EmailMaskStatusFilterInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskRealAddressTypeFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskStatusFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EqualRealAddressTypeFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EqualStatusFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.NotEqualRealAddressTypeFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.NotEqualStatusFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.NotOneOfRealAddressTypeFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.NotOneOfStatusFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.OneOfRealAddressTypeFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.OneOfStatusFilterEntity
import com.sudoplatform.sudoemail.types.inputs.EmailMaskFilterInput
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
 * Transformer for converting email mask filter inputs between entity, GraphQL, and API representations.
 */
internal object EmailMaskFilterTransformer {
    /**
     * Transforms an API [EmailMaskFilterInput] to entity type.
     *
     * @param input [EmailMaskFilterInput] The API filter input.
     * @return [EmailMaskFilterInputEntity] The entity filter input.
     * @throws SudoEmailClient.EmailMessageException.InvalidArgumentException if an invalid filter type is provided.
     */
    fun apiToEntity(input: EmailMaskFilterInput): EmailMaskFilterInputEntity {
        var statusFilter: EmailMaskStatusFilterInputEntity? = null
        var realAddressTypeFilter: EmailMaskRealAddressTypeFilterInputEntity? = null

        // Handle status filter
        if (input.status != null) {
            statusFilter =
                when (val inputStatus = input.status) {
                    is EqualStatusFilter -> {
                        EqualStatusFilterEntity(
                            equal = EmailMaskStatusTransformer.apiToEntity(inputStatus.equal),
                        )
                    }
                    is OneOfStatusFilter -> {
                        OneOfStatusFilterEntity(
                            oneOf = inputStatus.oneOf.map { EmailMaskStatusTransformer.apiToEntity(it) },
                        )
                    }
                    is NotEqualStatusFilter -> {
                        NotEqualStatusFilterEntity(
                            notEqual = EmailMaskStatusTransformer.apiToEntity(inputStatus.notEqual),
                        )
                    }
                    is NotOneOfStatusFilter -> {
                        NotOneOfStatusFilterEntity(
                            notOneOf = inputStatus.notOneOf.map { EmailMaskStatusTransformer.apiToEntity(it) },
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
                            equal = EmailMaskRealAddressTypeTransformer.apiToEntity(inputRealAddressType.equal),
                        )
                    }
                    is OneOfRealAddressTypeFilter -> {
                        OneOfRealAddressTypeFilterEntity(
                            oneOf = inputRealAddressType.oneOf.map { EmailMaskRealAddressTypeTransformer.apiToEntity(it) },
                        )
                    }
                    is NotEqualRealAddressTypeFilter -> {
                        NotEqualRealAddressTypeFilterEntity(
                            notEqual = EmailMaskRealAddressTypeTransformer.apiToEntity(inputRealAddressType.notEqual),
                        )
                    }
                    is NotOneOfRealAddressTypeFilter -> {
                        NotOneOfRealAddressTypeFilterEntity(
                            notOneOf = inputRealAddressType.notOneOf.map { EmailMaskRealAddressTypeTransformer.apiToEntity(it) },
                        )
                    }
                    else -> {
                        throw SudoEmailClient.EmailMessageException.InvalidArgumentException(
                            "Invalid realAddressType filter type $inputRealAddressType",
                        )
                    }
                }
        }

        return EmailMaskFilterInputEntity(
            status = statusFilter,
            addressType = realAddressTypeFilter,
        )
    }

    /**
     * Transforms domain EmailMaskFilterInputEntity to GraphQL EmailMaskFilterInput.
     *
     * @param filter [EmailMaskFilterInputEntity] The domain filter to transform.
     * @return [EmailMaskFilterInput] The GraphQL filter input, or null if no filters are set.
     * @throws SudoEmailClient.EmailMaskException.FailedException if filter transformation fails.
     */
    fun entityToGraphQl(filter: EmailMaskFilterInputEntity): EmailMaskFilterInputGraphQl {
        val status =
            if (filter.status != null) {
                when (val inputStatus = filter.status) {
                    is EqualStatusFilterEntity -> {
                        Optional.present(
                            EmailMaskStatusFilterInput(
                                eq = Optional.present(EmailMaskStatusTransformer.entityToGraphQL(inputStatus.equal)),
                            ),
                        )
                    }

                    is NotEqualStatusFilterEntity -> {
                        Optional.present(
                            EmailMaskStatusFilterInput(
                                ne = Optional.present(EmailMaskStatusTransformer.entityToGraphQL(inputStatus.notEqual)),
                            ),
                        )
                    }
                    is NotOneOfStatusFilterEntity -> {
                        Optional.present(
                            EmailMaskStatusFilterInput(
                                notIn =
                                    Optional.present(
                                        inputStatus.notOneOf.map { EmailMaskStatusTransformer.entityToGraphQL(it) },
                                    ),
                            ),
                        )
                    }
                    is OneOfStatusFilterEntity -> {
                        Optional.present(
                            EmailMaskStatusFilterInput(
                                `in` =
                                    Optional.present(
                                        inputStatus.oneOf.map { EmailMaskStatusTransformer.entityToGraphQL(it) },
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
                                eq = Optional.present(EmailMaskRealAddressTypeTransformer.entityToGraphQL(inputAddressType.equal)),
                            ),
                        )
                    }
                    is NotEqualRealAddressTypeFilterEntity -> {
                        Optional.present(
                            EmailMaskRealAddressTypeFilterInput(
                                ne = Optional.present(EmailMaskRealAddressTypeTransformer.entityToGraphQL(inputAddressType.notEqual)),
                            ),
                        )
                    }
                    is OneOfRealAddressTypeFilterEntity -> {
                        Optional.present(
                            EmailMaskRealAddressTypeFilterInput(
                                `in` =
                                    Optional.present(
                                        inputAddressType.oneOf.map { EmailMaskRealAddressTypeTransformer.entityToGraphQL(it) },
                                    ),
                            ),
                        )
                    }
                    is NotOneOfRealAddressTypeFilterEntity -> {
                        Optional.present(
                            EmailMaskRealAddressTypeFilterInput(
                                notIn =
                                    Optional.present(
                                        inputAddressType.notOneOf.map { EmailMaskRealAddressTypeTransformer.entityToGraphQL(it) },
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
            throw SudoEmailClient.EmailMaskException.FailedException("Failed to transform email mask filter: ${e.message}", e)
        }
    }
}
