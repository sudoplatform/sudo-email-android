package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.graphql.type.KeyFormat
import com.sudoplatform.sudoemail.types.PublicKeyFormat
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test that the [PublicKeyFormatTransformer] converts between entity types correctly.
 */
@RunWith(RobolectricTestRunner::class)
class PublicKeyFormatTransformerTest : BaseTests() {

    @Test
    fun testTransformingToEntity() {
        PublicKeyFormatTransformer.toEntity(KeyFormat.RSA_PUBLIC_KEY) shouldBe PublicKeyFormat.RSA_PUBLIC_KEY
        PublicKeyFormatTransformer.toEntity(KeyFormat.SPKI) shouldBe PublicKeyFormat.SPKI
    }

    @Test
    fun testTransformingToKeyManagerEntity() {
        PublicKeyFormatTransformer.toKeyManagerEntity(
            PublicKeyFormat.RSA_PUBLIC_KEY,
        ) shouldBe KeyManagerInterface.PublicKeyFormat.RSA_PUBLIC_KEY
        PublicKeyFormatTransformer.toKeyManagerEntity(
            PublicKeyFormat.SPKI,
        ) shouldBe KeyManagerInterface.PublicKeyFormat.SPKI
    }
}
