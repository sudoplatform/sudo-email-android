/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.types.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.types.inputs.EqualStateFilter
import com.sudoplatform.sudoemail.types.inputs.NotEqualStateFilter
import com.sudoplatform.sudoemail.types.inputs.NotOneOfStateFilter
import com.sudoplatform.sudoemail.types.inputs.OneOfStateFilter
import com.sudoplatform.sudoemail.types.inputs.ScheduledDraftMessageFilterInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageState as ScheduledDraftMessageStateGql
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageStateFilterInput as ScheduledDraftMessageFilterGql

/**
 * Test that the [ScheduledDraftMessageFilterTransformer] converts filters properly
 */
@RunWith(RobolectricTestRunner::class)
class ScheduledDraftMessageFilterTransformerTest : BaseTests() {
    @Test
    fun scheduledDraftMessageFilterTransformerParsesEmptyFilterProperly() {
        val inputFilter = ScheduledDraftMessageFilterInput()

        val transformed = ScheduledDraftMessageFilterTransformer.toGraphQl(inputFilter)
        transformed shouldBe null
    }

    @Test
    fun scheduledDraftMessageFilterTransformerParsesEqualFilterProperly() {
        val inputFilter =
            ScheduledDraftMessageFilterInput(
                state =
                    EqualStateFilter(
                        equal = ScheduledDraftMessageState.SCHEDULED,
                    ),
            )

        val transformed = ScheduledDraftMessageFilterTransformer.toGraphQl(inputFilter)
        transformed shouldNotBe null
        transformed?.state shouldBe
            Optional.present(
                ScheduledDraftMessageFilterGql(
                    eq = Optional.present(ScheduledDraftMessageStateGql.SCHEDULED),
                ),
            )
    }

    @Test
    fun scheduledDraftMessageFilterTransformerParsesOneOfFilterProperly() {
        val inputFilter =
            ScheduledDraftMessageFilterInput(
                state =
                    OneOfStateFilter(
                        oneOf = listOf(ScheduledDraftMessageState.SCHEDULED, ScheduledDraftMessageState.SENT),
                    ),
            )

        val transformed = ScheduledDraftMessageFilterTransformer.toGraphQl(inputFilter)
        transformed shouldNotBe null
        transformed?.state shouldBe
            Optional.present(
                ScheduledDraftMessageFilterGql(
                    `in` = Optional.present(listOf(ScheduledDraftMessageStateGql.SCHEDULED, ScheduledDraftMessageStateGql.SENT)),
                ),
            )
    }

    @Test
    fun scheduledDraftMessageFilterTransformerParsesNotEqualFilterProperly() {
        val inputFilter =
            ScheduledDraftMessageFilterInput(
                state =
                    NotEqualStateFilter(
                        notEqual = ScheduledDraftMessageState.CANCELLED,
                    ),
            )

        val transformed = ScheduledDraftMessageFilterTransformer.toGraphQl(inputFilter)
        transformed shouldNotBe null
        transformed?.state shouldBe
            Optional.present(
                ScheduledDraftMessageFilterGql(
                    ne = Optional.present(ScheduledDraftMessageStateGql.CANCELLED),
                ),
            )
    }

    @Test
    fun scheduledDraftMessageFilterTransformerParsesNotOneOfEqualFilterProperly() {
        val inputFilter =
            ScheduledDraftMessageFilterInput(
                state =
                    NotOneOfStateFilter(
                        notOneOf = listOf(ScheduledDraftMessageState.CANCELLED, ScheduledDraftMessageState.FAILED),
                    ),
            )

        val transformed = ScheduledDraftMessageFilterTransformer.toGraphQl(inputFilter)
        transformed shouldNotBe null
        transformed?.state shouldBe
            Optional.present(
                ScheduledDraftMessageFilterGql(
                    notIn = Optional.present(listOf(ScheduledDraftMessageStateGql.CANCELLED, ScheduledDraftMessageStateGql.FAILED)),
                ),
            )
    }
}
