/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressPublicInfo.PublicKeyDetails
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressPublicKey
import com.sudoplatform.sudoemail.graphql.type.KeyFormat
import com.sudoplatform.sudoemail.types.EmailAddressPublicInfo
import com.sudoplatform.sudoemail.types.PublicKeyFormat
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressPublicInfo as EmailAddressPublicInfoFragment
import com.sudoplatform.sudoemail.types.EmailAddressPublicKey as EmailAddressPublicKeyEntity

/**
 * Test that the [EmailAddressPublicInfoTransformer] converts graphql to entity type correctly.
 */
@RunWith(RobolectricTestRunner::class)
class EmailAddressPublicInfoTransformerTest : BaseTests() {
    private val fragment by before {
        EmailAddressPublicInfoFragment(
            "emailAddress",
            "keyId",
            "publicKey",
            PublicKeyDetails(
                "PublicKeyDetails",
                EmailAddressPublicKey(
                    "publicKey",
                    KeyFormat.RSA_PUBLIC_KEY,
                    "algorithm",
                ),
            ),
        )
    }

    private val entity by before {
        EmailAddressPublicInfo(
            "emailAddress",
            "keyId",
            EmailAddressPublicKeyEntity(
                "publicKey",
                PublicKeyFormat.RSA_PUBLIC_KEY,
                "algorithm",
            ),
        )
    }

    @Test
    fun testEmailAddressPublicInfoParsing() {
        EmailAddressPublicInfoTransformer.toEntity(fragment) shouldBe entity
    }
}
