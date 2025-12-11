/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.graphql.type.KeyFormat
import com.sudoplatform.sudoemail.internal.data.common.transformers.PublicKeyFormatTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PublicKeyFormatEntity
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test that the [com.sudoplatform.sudoemail.internal.data.common.transformers.PublicKeyFormatTransformer] converts between entity types correctly.
 */
@RunWith(RobolectricTestRunner::class)
class PublicKeyFormatTransformerTest : BaseTests() {
    @Test
    fun testTransformingToEntity() {
        PublicKeyFormatTransformer.toEntity(KeyFormat.RSA_PUBLIC_KEY) shouldBe PublicKeyFormatEntity.RSA_PUBLIC_KEY
        PublicKeyFormatTransformer.toEntity(KeyFormat.SPKI) shouldBe PublicKeyFormatEntity.SPKI
    }

    @Test
    fun testTransformingToKeyManagerEntity() {
        PublicKeyFormatTransformer.toKeyManagerEntity(
            PublicKeyFormatEntity.RSA_PUBLIC_KEY,
        ) shouldBe KeyManagerInterface.PublicKeyFormat.RSA_PUBLIC_KEY
        PublicKeyFormatTransformer.toKeyManagerEntity(
            PublicKeyFormatEntity.SPKI,
        ) shouldBe KeyManagerInterface.PublicKeyFormat.SPKI
    }
}
