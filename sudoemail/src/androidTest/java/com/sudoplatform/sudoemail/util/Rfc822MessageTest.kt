/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the simple RFC822 email message formatter and parser can at least encode
 * and decode its own messages.
 */
@RunWith(AndroidJUnit4::class)
class Rfc822MessageTest {

    @Test
    fun parserShouldBeAbleToParseMessageFromFactory() {

        val rfc822Data = Rfc822MessageFactory.makeRfc822Data(
            from = "Foo Bar <foo@bar.com>",
            to = "Ted Bear <ted.bear@toys.org>",
            cc = "Andy Pandy <andy.pandy@toys.org>",
            bcc = "bar@foo.com",
            subject = "Greetings from the toys",
            body = "Hello there from all the toys."
        )

        val message = Rfc822MessageParser.parseRfc822Data(rfc822Data)
        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Foo Bar <foo@bar.com>")
            to shouldContainExactlyInAnyOrder listOf("Ted Bear <ted.bear@toys.org>")
            cc shouldContainExactlyInAnyOrder listOf("Andy Pandy <andy.pandy@toys.org>")
            bcc shouldContainExactlyInAnyOrder listOf("bar@foo.com")
            subject shouldBe "Greetings from the toys"
            body shouldBe "Hello there from all the toys."
        }
    }
}
