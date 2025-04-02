/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import com.sudoplatform.sudoemail.BaseTests
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test the operation of [StringHasher] under Robolectric
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StringHasherTest : BaseTests() {
    private val address = "me@example.com"
    private val altAddress = "ME@example.com"
    private val addressHash = "jCpH07240wlqZHn1Pqw7ckKR218cMWERAPZ1vlU3Mp0="

    @Test
    fun `Properly normalizes and hashes email address`() {
        val result = StringHasher.hashString(EmailAddressParser.normalize(address))
        val altResult = StringHasher.hashString(EmailAddressParser.normalize(altAddress))

        result shouldBe altResult
        result shouldBe addressHash
    }
}
