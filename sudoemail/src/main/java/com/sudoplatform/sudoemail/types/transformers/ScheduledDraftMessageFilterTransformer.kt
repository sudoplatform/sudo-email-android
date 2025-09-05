package com.sudoplatform.sudoemail.types.transformers

import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageStateFilterInput
import com.sudoplatform.sudoemail.types.inputs.EqualStateFilter
import com.sudoplatform.sudoemail.types.inputs.NotEqualStateFilter
import com.sudoplatform.sudoemail.types.inputs.NotOneOfStateFilter
import com.sudoplatform.sudoemail.types.inputs.OneOfStateFilter
import com.sudoplatform.sudoemail.types.inputs.ScheduledDraftMessageFilterInput
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageFilterInput as ScheduledDraftMessageFilterGraphQl

object ScheduledDraftMessageFilterTransformer {
    fun toGraphQl(input: ScheduledDraftMessageFilterInput): ScheduledDraftMessageFilterGraphQl? {
        if (input.state != null) {
            when (val inputState = input.state) {
                is EqualStateFilter -> {
                    return ScheduledDraftMessageFilterGraphQl(
                        state =
                            Optional.present(
                                ScheduledDraftMessageStateFilterInput(
                                    eq = Optional.present(ScheduledDraftMessageStateTransformer.toGraphQl(inputState.equal)),
                                ),
                            ),
                    )
                }
                is OneOfStateFilter -> {
                    return ScheduledDraftMessageFilterGraphQl(
                        state =
                            Optional.present(
                                ScheduledDraftMessageStateFilterInput(
                                    `in` =
                                        Optional.present(
                                            inputState.oneOf.map { ScheduledDraftMessageStateTransformer.toGraphQl(it) },
                                        ),
                                ),
                            ),
                    )
                }
                is NotEqualStateFilter -> {
                    return ScheduledDraftMessageFilterGraphQl(
                        state =
                            Optional.present(
                                ScheduledDraftMessageStateFilterInput(
                                    ne = Optional.present(ScheduledDraftMessageStateTransformer.toGraphQl(inputState.notEqual)),
                                ),
                            ),
                    )
                }
                is NotOneOfStateFilter -> {
                    return ScheduledDraftMessageFilterGraphQl(
                        state =
                            Optional.present(
                                ScheduledDraftMessageStateFilterInput(
                                    notIn =
                                        Optional.present(
                                            inputState.notOneOf.map { ScheduledDraftMessageStateTransformer.toGraphQl(it) },
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
}
