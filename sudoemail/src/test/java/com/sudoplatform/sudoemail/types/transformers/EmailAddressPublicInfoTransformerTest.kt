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
import com.sudoplatform.sudoemail.internal.data.emailAddress.transformers.EmailAddressPublicInfoTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressPublicInfoEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressPublicKeyEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PublicKeyFormatEntity
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressPublicInfo as EmailAddressPublicInfoFragment

/**
 * Test that the [com.sudoplatform.sudoemail.internal.data.emailAddress.transformers.EmailAddressPublicInfoTransformer] converts graphql to entity type correctly.
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
        EmailAddressPublicInfoEntity(
            "emailAddress",
            "keyId",
            EmailAddressPublicKeyEntity(
                "publicKey",
                PublicKeyFormatEntity.RSA_PUBLIC_KEY,
                "algorithm",
            ),
        )
    }

    @Test
    fun testEmailAddressPublicInfoParsing() {
        EmailAddressPublicInfoTransformer.graphQLToEntity(fragment) shouldBe entity
    }
}
