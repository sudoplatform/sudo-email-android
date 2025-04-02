/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.types.EmailMessage
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test that the [EmailMessageTransformer] converts to GraphQL types correctly.
 */
@RunWith(RobolectricTestRunner::class)
class EmailMessageTransformerTest : BaseTests() {

    @Test
    fun testEmailAddressParsing() {
        val bareAddress = "foo@bar.com"
        EmailMessageTransformer.toEmailAddress(bareAddress) shouldBe EmailMessage.EmailAddress(bareAddress)

        val paddedBareAddress = " foo@bar.com "
        EmailMessageTransformer.toEmailAddress(paddedBareAddress) shouldBe EmailMessage.EmailAddress(bareAddress)

        val addressWithDisplayName = "Foo Bar <$bareAddress>"
        EmailMessageTransformer.toEmailAddress(addressWithDisplayName) shouldBe EmailMessage.EmailAddress(bareAddress, "Foo Bar")
    }
}
