/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.draftMessage.transformers

import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageStateFilterInput
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.EqualStateFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.NotEqualStateFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.NotOneOfStateFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.OneOfStateFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageFilterInputEntity
import com.sudoplatform.sudoemail.types.inputs.EqualStateFilter
import com.sudoplatform.sudoemail.types.inputs.NotEqualStateFilter
import com.sudoplatform.sudoemail.types.inputs.NotOneOfStateFilter
import com.sudoplatform.sudoemail.types.inputs.OneOfStateFilter
import com.sudoplatform.sudoemail.types.inputs.ScheduledDraftMessageFilterInput
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageFilterInput as ScheduledDraftMessageFilterGraphQl

/**
 * Transformer for converting scheduled draft message filter inputs between entity, GraphQL, and API representations.
 */
internal object ScheduledDraftMessageFilterTransformer {
    /**
     * Transforms a [ScheduledDraftMessageFilterInputEntity] to GraphQL type.
     *
     * @param input [ScheduledDraftMessageFilterInputEntity] The entity filter input.
     * @return [ScheduledDraftMessageFilterGraphQl] The GraphQL filter input, or null if no filters are set.
     * @throws SudoEmailClient.EmailMessageException.InvalidArgumentException if an invalid filter type is provided.
     */
    fun entityToGraphQl(input: ScheduledDraftMessageFilterInputEntity): ScheduledDraftMessageFilterGraphQl? {
        if (input.state != null) {
            when (val inputState = input.state) {
                is EqualStateFilterEntity -> {
                    return ScheduledDraftMessageFilterGraphQl(
                        state =
                            Optional.present(
                                ScheduledDraftMessageStateFilterInput(
                                    eq = Optional.present(ScheduledDraftMessageStateTransformer.entityToGraphQL(inputState.equal)),
                                ),
                            ),
                    )
                }
                is OneOfStateFilterEntity -> {
                    return ScheduledDraftMessageFilterGraphQl(
                        state =
                            Optional.present(
                                ScheduledDraftMessageStateFilterInput(
                                    `in` =
                                        Optional.present(
                                            inputState.oneOf.map { ScheduledDraftMessageStateTransformer.entityToGraphQL(it) },
                                        ),
                                ),
                            ),
                    )
                }
                is NotEqualStateFilterEntity -> {
                    return ScheduledDraftMessageFilterGraphQl(
                        state =
                            Optional.present(
                                ScheduledDraftMessageStateFilterInput(
                                    ne = Optional.present(ScheduledDraftMessageStateTransformer.entityToGraphQL(inputState.notEqual)),
                                ),
                            ),
                    )
                }
                is NotOneOfStateFilterEntity -> {
                    return ScheduledDraftMessageFilterGraphQl(
                        state =
                            Optional.present(
                                ScheduledDraftMessageStateFilterInput(
                                    notIn =
                                        Optional.present(
                                            inputState.notOneOf.map { ScheduledDraftMessageStateTransformer.entityToGraphQL(it) },
                                        ),
                                ),
                            ),
                    )
                }
                else -> {
                    throw SudoEmailClient.EmailMessageException.InvalidArgumentException("Invalid filter type $inputState")
                }
            }
        }
        return null
    }

    /**
     * Transforms an API [ScheduledDraftMessageFilterInput] to entity type.
     *
     * @param input [ScheduledDraftMessageFilterInput] The API filter input.
     * @return [ScheduledDraftMessageFilterInputEntity] The entity filter input.
     * @throws SudoEmailClient.EmailMessageException.InvalidArgumentException if an invalid filter type is provided.
     */
    fun apiToEntity(input: ScheduledDraftMessageFilterInput): ScheduledDraftMessageFilterInputEntity {
        if (input.state != null) {
            when (val inputState = input.state) {
                is EqualStateFilter -> {
                    return ScheduledDraftMessageFilterInputEntity(
                        state =
                            EqualStateFilterEntity(
                                equal = ScheduledDraftMessageStateTransformer.apiToEntity(inputState.equal),
                            ),
                    )
                }
                is OneOfStateFilter -> {
                    return ScheduledDraftMessageFilterInputEntity(
                        state =
                            OneOfStateFilterEntity(
                                oneOf = inputState.oneOf.map { ScheduledDraftMessageStateTransformer.apiToEntity(it) },
                            ),
                    )
                }
                is NotEqualStateFilter -> {
                    return ScheduledDraftMessageFilterInputEntity(
                        state =
                            NotEqualStateFilterEntity(
                                notEqual = ScheduledDraftMessageStateTransformer.apiToEntity(inputState.notEqual),
                            ),
                    )
                }
                is NotOneOfStateFilter -> {
                    return ScheduledDraftMessageFilterInputEntity(
                        state =
                            NotOneOfStateFilterEntity(
                                notOneOf = inputState.notOneOf.map { ScheduledDraftMessageStateTransformer.apiToEntity(it) },
                            ),
                    )
                }
                else -> {
                    throw SudoEmailClient.EmailMessageException.InvalidArgumentException("Invalid filter type $inputState")
                }
            }
        }
        return ScheduledDraftMessageFilterInputEntity(state = null)
    }
}
