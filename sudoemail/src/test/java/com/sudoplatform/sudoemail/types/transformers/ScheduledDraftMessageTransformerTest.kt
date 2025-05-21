/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.types.Owner
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date
import com.sudoplatform.sudoemail.graphql.fragment.ScheduledDraftMessage as ScheduledDraftMessageFragment
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageState as ScheduleDraftMessageStateGraphQl
import com.sudoplatform.sudoemail.types.ScheduledDraftMessage as ScheduledDraftMessageEntity
import com.sudoplatform.sudoemail.types.ScheduledDraftMessageState as ScheduledDraftMessageStateEntity

/**
 * Test that the [ScheduledDraftMessageTransformer] converts graphql to entity type correctly.
 */
@RunWith(RobolectricTestRunner::class)
class ScheduledDraftMessageTransformerTest : BaseTests() {
    private val timestamp = Date()
    private val fragment by before {
        ScheduledDraftMessageFragment(
            "dummyPrefix/dummyId",
            "dummyEmailAddress",
            timestamp.time.toDouble(),
            ScheduleDraftMessageStateGraphQl.SCHEDULED,
            timestamp.time.toDouble(),
            timestamp.time.toDouble(),
            "dummyOwner",
            listOf(ScheduledDraftMessageFragment.Owner("dummyId", "dummyIssuer")),
        )
    }

    private val entity by before {
        ScheduledDraftMessageEntity(
            "dummyId",
            "dummyEmailAddress",
            "dummyOwner",
            listOf(Owner("dummyId", "dummyIssuer")),
            timestamp,
            ScheduledDraftMessageStateEntity.SCHEDULED,
            timestamp,
            timestamp,
        )
    }

    @Test
    fun testScheduledDraftMessageParsing() {
        ScheduledDraftMessageTransformer.toEntity(fragment) shouldBe entity
    }
}
