/*
 * Copyright © 2020 - Anonyome Labs, Inc. - All rights reserved
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.graphql.type.EmailMessageDirection
import com.sudoplatform.sudoemail.graphql.type.EmailMessageState
import com.sudoplatform.sudoemail.types.EmailMessage.EmailAddress
import com.sudoplatform.sudoemail.types.inputs.filters.filterEmailMessagesBy
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test that the [EmailMessageTransformer.toGraphQLFilter] converts to GraphQL filters correctly.
 *
 * @since 2020-08-12
 */
@RunWith(RobolectricTestRunner::class)
class EmailMessageTransformerTest : BaseTests() {

    @Test
    fun testSeenFilterTransformation() {
        val seenFilter = EmailMessageTransformer.toGraphQLFilter(
            filterEmailMessagesBy {
                seen equalTo true
            }
        )
        seenFilter shouldNotBe null
        seenFilter?.seen()?.eq() shouldBe true

        val unseenFilter = EmailMessageTransformer.toGraphQLFilter(
            filterEmailMessagesBy {
                seen notEqualTo true
            }
        )
        unseenFilter shouldNotBe null
        unseenFilter?.seen()?.ne() shouldBe true
    }

    @Test
    fun testStateFilterTransformation() {
        val sentFilter = EmailMessageTransformer.toGraphQLFilter(
            filterEmailMessagesBy {
                state equalTo sent
            }
        )
        sentFilter shouldNotBe null
        sentFilter?.state()?.eq() shouldBe EmailMessageState.SENT

        val notDeliveredFilter = EmailMessageTransformer.toGraphQLFilter(
            filterEmailMessagesBy {
                state notEqualTo delivered
            }
        )
        notDeliveredFilter shouldNotBe null
        notDeliveredFilter?.state()?.ne() shouldBe EmailMessageState.DELIVERED
    }

    @Test
    fun testNotSeenFilterTransformation() {
        val notSeenFilter = EmailMessageTransformer.toGraphQLFilter(
            filterEmailMessagesBy {
                not(seen)
            }
        )
        notSeenFilter shouldNotBe null
        notSeenFilter?.seen()?.ne() shouldBe true
    }

    @Test
    fun testDirectionFilterTransformation() {
        val directionFilter = EmailMessageTransformer.toGraphQLFilter(
            filterEmailMessagesBy {
                direction equalTo outbound
            }
        )
        directionFilter shouldNotBe null
        directionFilter?.direction()?.eq() shouldBe EmailMessageDirection.OUTBOUND
    }

    @Test
    fun testAndFilterTransformation() {
        val andFilter = EmailMessageTransformer.toGraphQLFilter(
            filterEmailMessagesBy {
                allOf(
                    not(seen),
                    state equalTo sent
                )
            }
        )
        andFilter shouldNotBe null
        andFilter?.and()?.size shouldBe 2
        andFilter?.and()?.get(0)?.seen()?.ne() shouldBe true
        andFilter?.and()?.get(1)?.state()?.eq() shouldBe EmailMessageState.SENT
    }

    @Test
    fun testOrFilterTransformation() {
        val orFilter = EmailMessageTransformer.toGraphQLFilter(
            filterEmailMessagesBy {
                oneOf(
                    seen equalTo true,
                    state notEqualTo delivered,
                    direction notEqualTo outbound

                )
            }
        )
        orFilter shouldNotBe null
        orFilter?.or()?.size shouldBe 3
        orFilter?.or()?.get(0)?.seen()?.eq() shouldBe true
        orFilter?.or()?.get(1)?.state()?.ne() shouldBe EmailMessageState.DELIVERED
        orFilter?.or()?.get(2)?.direction()?.ne() shouldBe EmailMessageDirection.OUTBOUND
    }

    @Test
    fun testEmailAddressParsing() {
        val bareAddress = "foo@bar.com"
        EmailMessageTransformer.toEmailAddress(bareAddress) shouldBe EmailAddress(bareAddress)

        val paddedBareAddress = " foo@bar.com "
        EmailMessageTransformer.toEmailAddress(paddedBareAddress) shouldBe EmailAddress(bareAddress)

        val addressWithDisplayName = "Foo Bar <$bareAddress>"
        EmailMessageTransformer.toEmailAddress(addressWithDisplayName) shouldBe EmailAddress(bareAddress, "Foo Bar")
    }
}
