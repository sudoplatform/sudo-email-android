/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.types.EncryptionStatus
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the correct operation of the [Rfc822MessageDataProcessor] encoding and parsing.
 */
@RunWith(AndroidJUnit4::class)
class Rfc822MessageDataProcessorTest {

    @Test
    fun shouldBeAbleToParseMessage() {
        val rfc822Data = Rfc822MessageDataProcessor().encodeToInternetMessageData(
            from = "Foo Bar <foo@bar.com>",
            to = listOf("Ted Bear <ted.bear@toys.org>"),
            cc = listOf("Andy Pandy <andy.pandy@toys.org>"),
            bcc = listOf("bar@foo.com"),
            subject = "Greetings from the toys",
            body = "Hello there from all the toys.",
            encryptionStatus = EncryptionStatus.UNENCRYPTED,
        )

        val message = Rfc822MessageDataProcessor().parseInternetMessageData(rfc822Data)
        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Foo Bar <foo@bar.com>")
            to shouldContainExactlyInAnyOrder listOf("Ted Bear <ted.bear@toys.org>")
            cc shouldContainExactlyInAnyOrder listOf("Andy Pandy <andy.pandy@toys.org>")
            bcc shouldContainExactlyInAnyOrder listOf("bar@foo.com")
            subject shouldBe "Greetings from the toys"
            body shouldBe "Hello there from all the toys."
        }
    }

    @Test
    fun shouldBeAbleToParseMessageWithNullFields() {
        val rfc822Data = Rfc822MessageDataProcessor().encodeToInternetMessageData(
            from = "Foo Bar <foo@bar.com>",
            to = listOf("Ted Bear <ted.bear@toys.org>"),
            cc = null,
            bcc = null,
            subject = null,
            body = "Hello there from all the toys.",
            encryptionStatus = EncryptionStatus.UNENCRYPTED,
        )

        val message = Rfc822MessageDataProcessor().parseInternetMessageData(rfc822Data)
        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Foo Bar <foo@bar.com>")
            to shouldContainExactlyInAnyOrder listOf("Ted Bear <ted.bear@toys.org>")
            cc shouldContainExactlyInAnyOrder emptyList()
            bcc shouldContainExactlyInAnyOrder emptyList()
            subject shouldBe null
            body shouldBe "Hello there from all the toys."
        }
    }

    @Test
    fun shouldBeAbleToParseBodyCorrectlyForEncryptedMessage() {
        val rfc822Data = Rfc822MessageDataProcessor().encodeToInternetMessageData(
            from = "Foo Bar <foo@bar.com>",
            to = listOf("Ted Bear <ted.bear@toys.org>"),
            cc = listOf("Andy Pandy <andy.pandy@toys.org>"),
            bcc = listOf("bar@foo.com"),
            subject = "Greetings from the toys",
            body = "Hello there from all the toys.",
            encryptionStatus = EncryptionStatus.ENCRYPTED,
        )

        val message = Rfc822MessageDataProcessor().parseInternetMessageData(rfc822Data)
        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Foo Bar <foo@bar.com>")
            to shouldContainExactlyInAnyOrder listOf("Ted Bear <ted.bear@toys.org>")
            cc shouldContainExactlyInAnyOrder listOf("Andy Pandy <andy.pandy@toys.org>")
            bcc shouldContainExactlyInAnyOrder listOf("bar@foo.com")
            subject shouldBe "Greetings from the toys"
            body shouldBe "Encrypted message attached"
        }
    }
}
