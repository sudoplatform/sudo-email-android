/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.types.EmailAddressPublicInfo
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressPublicInfo as EmailAddressPublicInfoFragment

/**
 * Test that the [EmailAddressPublicInfoTransformer] converts graphql to entity type correctly.
 */
@RunWith(RobolectricTestRunner::class)
class EmailAddressPublicInfoTransformerTest : BaseTests() {

    private val fragment by before {
        EmailAddressPublicInfoFragment("typename", "emailAddress", "keyId", "publicKey")
    }

    private val entity by before {
        EmailAddressPublicInfo("emailAddress", "keyId", "publicKey")
    }

    @Test
    fun testEmailAddressPublicInfoParsing() {
        EmailAddressPublicInfoTransformer.toEntity(fragment) shouldBe entity
    }
}
