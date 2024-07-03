/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EncryptionStatus
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the correct operation of the [Rfc822MessageDataProcessor] encoding and parsing.
 */
@RunWith(AndroidJUnit4::class)
class Rfc822MessageDataProcessorTest {
    companion object {
        private const val RAW_BASIC_EMAIL = "basic_email.eml"
        private const val RAW_EMAIL_WITH_ATTACHMENTS = "sample_email_with_attachments.eml"
        private const val RAW_EMAIL_REPLY_TO_WITH_INLINE_ATTACHMENT = "reply_from_gmail.eml"
        private const val RAW_EMAIL_WITH_ATTACHED_EMAIL = "attached_email.eml"
        private const val RAW_EMAIL_WITH_INLINE_AND_NON_INLINE_ATTACHMENT = "email_with_inline_and_non_inline_attachment.eml"
        private const val DELIVERY_FAILED_RFC_822_HEADER_EMAIL = "delivery_failed_rfc_822_headers.eml"
        private const val DELIVERY_FAILED_INLINE_IMAGE_EMAIL = "delivery_failed_inline_image.eml"
        private const val DELIVERY_FAILED_PLAIN_SERVER_PLAIN_ATTACHED_EMAIL = "delivery_failed_plain_server_plain_attached.eml"
        private const val DELIVERY_FAILED_PLAIN_SERVER_HTML_ATTACHED_EMAIL = "delivery_failed_plain_server_html_attached.eml"
        private const val MEETING_INVITE_EMAIL = "meeting_invite.eml"
    }

    val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun shouldParseBasicMessage() {
        val rfc822Data = Rfc822MessageDataProcessor(context).encodeToInternetMessageData(
            from = "Foo Bar <foo@bar.com>",
            to = listOf("Ted Bear <ted.bear@toys.org>"),
            cc = listOf("Andy Pandy <andy.pandy@toys.org>"),
            bcc = listOf("bar@foo.com"),
            subject = "Greetings from the toys",
            body = "Hello there from all the toys.",
            encryptionStatus = EncryptionStatus.UNENCRYPTED,
        )

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rfc822Data)
        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Foo Bar <foo@bar.com>")
            to shouldContainExactlyInAnyOrder listOf("Ted Bear <ted.bear@toys.org>")
            cc shouldContainExactlyInAnyOrder listOf("Andy Pandy <andy.pandy@toys.org>")
            bcc shouldContainExactlyInAnyOrder listOf("bar@foo.com")
            subject shouldBe "Greetings from the toys"
            body shouldBe "Hello there from all the toys."
            isHtml shouldBe false
            attachments.isEmpty() shouldBe true
            inlineAttachments.isEmpty() shouldBe true
        }
    }

    @Test
    fun shouldParseMessageWithNullFields() {
        val rfc822Data = Rfc822MessageDataProcessor(context).encodeToInternetMessageData(
            from = "Foo Bar <foo@bar.com>",
            to = listOf("Ted Bear <ted.bear@toys.org>"),
            cc = null,
            bcc = null,
            subject = null,
            body = "Hello there from all the toys.",
            encryptionStatus = EncryptionStatus.UNENCRYPTED,
        )

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rfc822Data)
        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Foo Bar <foo@bar.com>")
            to shouldContainExactlyInAnyOrder listOf("Ted Bear <ted.bear@toys.org>")
            cc shouldContainExactlyInAnyOrder emptyList()
            bcc shouldContainExactlyInAnyOrder emptyList()
            subject shouldBe null
            body shouldBe "Hello there from all the toys."
            isHtml shouldBe false
            attachments.isEmpty() shouldBe true
            inlineAttachments.isEmpty() shouldBe true
        }
    }

    @Test
    fun shouldParseMessageWithAttachments() {
        val attachment = EmailAttachment(
            fileName = "goodExtension.pdf",
            contentId = UUID.randomUUID().toString(),
            mimeType = "application/pdf",
            inlineAttachment = false,
            data = "This file has a valid file extension".toByteArray(),
        )
        val inlineAttachment = EmailAttachment(
            fileName = "goodImage.png",
            contentId = UUID.randomUUID().toString(),
            mimeType = "image/png",
            inlineAttachment = true,
            data = ByteArray(42),
        )
        val rfc822Data = Rfc822MessageDataProcessor(context).encodeToInternetMessageData(
            from = "Foo Bar <foo@bar.com>",
            to = listOf("Ted Bear <ted.bear@toys.org>"),
            cc = listOf("Andy Pandy <andy.pandy@toys.org>"),
            bcc = listOf("bar@foo.com"),
            subject = "Greetings from the toys",
            body = "Hello there from all the toys.",
            attachments = listOf(attachment),
            inlineAttachments = listOf(inlineAttachment),
            encryptionStatus = EncryptionStatus.UNENCRYPTED,
        )

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rfc822Data)
        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Foo Bar <foo@bar.com>")
            to shouldContainExactlyInAnyOrder listOf("Ted Bear <ted.bear@toys.org>")
            cc shouldContainExactlyInAnyOrder listOf("Andy Pandy <andy.pandy@toys.org>")
            bcc shouldContainExactlyInAnyOrder listOf("bar@foo.com")
            subject shouldBe "Greetings from the toys"
            body shouldBe "Hello there from all the toys."
            isHtml shouldBe false
            with(attachments.first()) {
                fileName shouldBe attachment.fileName
                contentId shouldBe attachment.contentId
                mimeType shouldBe attachment.mimeType
                attachments.first().inlineAttachment shouldBe false
                data shouldBe attachment.data
            }
            with(inlineAttachments.first()) {
                fileName shouldBe inlineAttachment.fileName
                contentId shouldBe inlineAttachment.contentId
                mimeType shouldBe inlineAttachment.mimeType
                inlineAttachments.first().inlineAttachment shouldBe true
                data shouldBe inlineAttachment.data
            }
        }
    }

    @Test
    fun shouldParseBasicTextEmail() {
        val rawEmail = getRawEmail(RAW_BASIC_EMAIL)

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rawEmail)

        with(message) {
            from shouldContainExactlyInAnyOrder listOf("toys@toys.org")
            to shouldContainExactlyInAnyOrder listOf("ted.bear@toys.org")
            cc.isEmpty() shouldBe true
            bcc.isEmpty() shouldBe true
            subject shouldBe "User Information for TedBear4135"
            body?.shouldContainOrderedStrings(
                listOf(
                    "Your new password is XXXXXX",
                    "have to send yourself another one.\n",
                ),
            )
            isHtml shouldBe false
            attachments.isEmpty() shouldBe true
            inlineAttachments.isEmpty() shouldBe true
        }
    }

    @Test
    fun shouldParseEmailWithRegularAttachmentsAndInlineAttachmentsWithIncorrectDisposition() {
        val rawEmail = getRawEmail(RAW_EMAIL_WITH_ATTACHMENTS)

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rawEmail)

        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Ted Bear <ted.bear@toys.com>")
            to shouldContainExactlyInAnyOrder listOf("ted.bear@toys.pl")
            cc shouldContainExactlyInAnyOrder listOf("fred.bear@toys.pl")
            bcc shouldContainExactlyInAnyOrder listOf("ken.bear@toys.pl")
            subject shouldBe "Test subject"
            body?.shouldContainOrderedStrings(
                listOf(
                    "Test email message. Inline attachment below.",
                ),
            )
            isHtml shouldBe true

            attachments.size shouldBe 1
            with(attachments.first()) {
                fileName shouldBe "sample_file.jpg"
                contentId shouldBe "15ffdeead5ee90dd7642"
                mimeType shouldBe "image/jpeg"
                inlineAttachment shouldBe false
                data.size shouldBe 1253432
            }

            inlineAttachments.size shouldBe 1
            with(inlineAttachments.first()) {
                fileName shouldBe "oczami-golebia.jpg"
                contentId shouldBe "15ffdee8dd14254a3fe1"
                mimeType shouldBe "image/jpeg"
                inlineAttachment shouldBe true
                data.size shouldBe 61728
            }
        }
    }

    @Test
    fun shouldParseReplyToEmailWithInlineAttachment() {
        val rawEmail = getRawEmail(RAW_EMAIL_REPLY_TO_WITH_INLINE_ATTACHMENT)

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rawEmail)

        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Fred B <fred.bear@gmail.com>")
            to shouldContainExactlyInAnyOrder listOf("Ted Bear <ted.bear@toys.com>")
            cc.isEmpty() shouldBe true
            bcc.isEmpty() shouldBe true
            subject shouldBe "Re: test from gmail"
            body?.shouldContainOrderedStrings(
                listOf(
                    "reply 2 from gmail",
                    "Reply from outlook",
                    "original message with inline",
                ),
            )
            isHtml shouldBe true
            attachments.isEmpty() shouldBe true

            inlineAttachments.size shouldBe 1
            with(inlineAttachments.first()) {
                fileName shouldBe "image001.png"
                contentId shouldBe "16b0d9a87064cff311"
                mimeType shouldBe "image/png"
                inlineAttachment shouldBe true
                data.size shouldBe 205257
            }
        }
    }

    @Test
    fun shouldParseEmailWithAttachedEmail() {
        val rawEmail = getRawEmail(RAW_EMAIL_WITH_ATTACHED_EMAIL)

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rawEmail)

        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Ted Bear <ted.bear@toys.com>")
            to shouldContainExactlyInAnyOrder listOf("Fred B <fred.bear@gmail.com>")
            cc.isEmpty() shouldBe true
            bcc.isEmpty() shouldBe true
            subject shouldBe "Attached email"
            body?.shouldContainOrderedStrings(
                listOf(
                    "Attaching",
                    "another email to this one.",
                    "original message with inline",
                ),
            )
            isHtml shouldBe true
            attachments.isEmpty() shouldBe true

            inlineAttachments.size shouldBe 1
            with(inlineAttachments.first()) {
                fileName shouldBe "1525680193922.png"
                contentId shouldBe "ii_jwbzpxvc0"
                mimeType shouldBe "image/png"
                inlineAttachment shouldBe true
                data.size shouldBe 205256
            }
        }
    }

    @Test
    fun shouldParseEmailWithInlineAndNonInLineAttachment() {
        val rawEmail = getRawEmail(RAW_EMAIL_WITH_INLINE_AND_NON_INLINE_ATTACHMENT)

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rawEmail)

        with(message) {
            from shouldContainExactlyInAnyOrder listOf("John Smith <jsmith@foobar.com>")
            to shouldContainExactlyInAnyOrder listOf("\"ted42dp@toys.com\" <ted42dp@toys.com>")
            cc.isEmpty() shouldBe true
            bcc.isEmpty() shouldBe true
            subject shouldBe "Hi from Outlook"
            body?.shouldContainOrderedStrings(
                listOf(
                    "Hello",
                    "from Outlook",
                ),
            )
            isHtml shouldBe true

            attachments.size shouldBe 1
            with(attachments.first()) {
                fileName shouldBe "whiskey_the_dog.jpg"
                contentId shouldBe ""
                mimeType shouldBe "image/jpeg"
                inlineAttachment shouldBe false
                data.size shouldBe 2850
            }

            inlineAttachments.size shouldBe 1
            with(inlineAttachments.first()) {
                fileName shouldBe "image001.png"
                contentId shouldBe "image001.png@01DAC3C5.D4996FD0"
                mimeType shouldBe "image/png"
                inlineAttachment shouldBe true
                data.size shouldBe 16826
            }
        }
    }

    @Test
    fun shouldParseDeliveryFailedPlainServerMessagePlainAttachedMessageEmail() {
        val rawEmail = getRawEmail(DELIVERY_FAILED_PLAIN_SERVER_PLAIN_ATTACHED_EMAIL)

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rawEmail)

        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Mail Delivery System <Mailer-Daemon@sydd1mt01>")
            to shouldContainExactlyInAnyOrder listOf("4747test1234@toys.com")
            cc.isEmpty() shouldBe true
            bcc.isEmpty() shouldBe true
            subject shouldBe "Mail delivery failed: returning message to sender"
            body?.shouldContainOrderedStrings(
                listOf(
                    "550 no mailbox by that name is currently available",
                    "test@test.com",
                    "Test",
                ),
            )
            isHtml shouldBe true
            attachments.isEmpty() shouldBe true
            inlineAttachments.isEmpty() shouldBe true
        }
    }

    @Test
    fun shouldParseDeliveryFailedPlainServerMessageHtmlAttachedMessageEmail() {
        val rawEmail = getRawEmail(DELIVERY_FAILED_PLAIN_SERVER_HTML_ATTACHED_EMAIL)

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rawEmail)

        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Mail Delivery System <Mailer-Daemon@sydd1mt02>")
            to shouldContainExactlyInAnyOrder listOf("4747test1234@toys.com")
            cc.isEmpty() shouldBe true
            bcc.isEmpty() shouldBe true
            subject shouldBe "Mail delivery failed: returning message to sender"
            body?.shouldContainOrderedStrings(
                listOf(
                    "This message was created automatically by mail delivery software.",
                ),
            )
            isHtml shouldBe true
            attachments.isEmpty() shouldBe true
            inlineAttachments.isEmpty() shouldBe true
        }
    }

    @Test
    fun shouldParseDeliveryFailedEmailWithMessageRfc822Header() {
        val rawEmail = getRawEmail(DELIVERY_FAILED_RFC_822_HEADER_EMAIL)

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rawEmail)

        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Mail Delivery System <Mailer-Daemon@sydd1mt02>")
            to shouldContainExactlyInAnyOrder listOf("ted.bear@toys.com")
            cc.isEmpty() shouldBe true
            bcc.isEmpty() shouldBe true
            subject shouldBe "Mail delivery failed: returning message to sender"
            body?.shouldContainOrderedStrings(
                listOf(
                    "This message was created automatically by mail delivery software.",
                ),
            )
            isHtml shouldBe false
            attachments.isEmpty() shouldBe true
            inlineAttachments.isEmpty() shouldBe true
        }
    }

    @Test
    fun shouldParseDeliveryFailedEmailWithInlineImageRfc822Header() {
        val rawEmail = getRawEmail(DELIVERY_FAILED_INLINE_IMAGE_EMAIL)

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rawEmail)

        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Mail Delivery System <Mailer-Daemon@sydd1mt02>")
            to shouldContainExactlyInAnyOrder listOf("ted.bear@toys.com")
            cc.isEmpty() shouldBe true
            bcc.isEmpty() shouldBe true
            subject shouldBe "Mail delivery failed: returning message to sender"
            body?.shouldContainOrderedStrings(
                listOf(
                    "This message was created automatically by mail delivery software.",
                ),
            )
            isHtml shouldBe true
            attachments.isEmpty() shouldBe true

            inlineAttachments.size shouldBe 1
            with(inlineAttachments.first()) {
                fileName shouldBe "512wF3yP3FL._AC_SY400_.jpg"
                contentId shouldBe "72e4fe5b-d9e1-48b4-8601-834931156db8"
                mimeType shouldBe "image/jpeg"
                inlineAttachment shouldBe true
                data.size shouldBe 20802
            }
        }
    }

    @Test
    fun shouldParseMeetingInviteEmail() {
        val rawEmail = getRawEmail(MEETING_INVITE_EMAIL)

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rawEmail)

        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Ted Bear <ted.bear@toys.com>")
            to shouldContainExactlyInAnyOrder listOf(""""fred.bear@gmail.com" <fred.bear@gmail.com>""")
            cc.isEmpty() shouldBe true
            bcc.isEmpty() shouldBe true
            subject shouldBe "FW: Holiday event"
            body shouldNotBe null
            isHtml shouldBe true
            inlineAttachments.isEmpty() shouldBe true

            attachments.size shouldBe 1
            with(attachments.first()) {
                fileName shouldBe "invite.ics"
                contentId.isEmpty() shouldBe true
                mimeType shouldBe "text/calendar"
                inlineAttachment shouldBe false
                data.size shouldBe 3238
            }
        }
    }

    @Test
    fun shouldParseBodyCorrectlyForEncryptedMessage() {
        val rfc822Data = Rfc822MessageDataProcessor(context).encodeToInternetMessageData(
            from = "Foo Bar <foo@bar.com>",
            to = listOf("Ted Bear <ted.bear@toys.org>"),
            cc = listOf("Andy Pandy <andy.pandy@toys.org>"),
            bcc = listOf("bar@foo.com"),
            subject = "Greetings from the toys",
            body = "Hello there from all the toys.",
            encryptionStatus = EncryptionStatus.ENCRYPTED,
        )

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rfc822Data)
        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Foo Bar <foo@bar.com>")
            to shouldContainExactlyInAnyOrder listOf("Ted Bear <ted.bear@toys.org>")
            cc shouldContainExactlyInAnyOrder listOf("Andy Pandy <andy.pandy@toys.org>")
            bcc shouldContainExactlyInAnyOrder listOf("bar@foo.com")
            subject shouldBe "Greetings from the toys"
            body shouldBe "Encrypted message attached"
            isHtml shouldBe false
        }
    }

    @Test
    fun shouldParseEncryptedMessageWithAttachments() {
        val attachment = EmailAttachment(
            fileName = "goodExtension.pdf",
            contentId = UUID.randomUUID().toString(),
            mimeType = "application/pdf",
            inlineAttachment = false,
            data = "This file has a valid file extension".toByteArray(),
        )
        val inlineAttachment = EmailAttachment(
            fileName = "goodImage.png",
            contentId = UUID.randomUUID().toString(),
            mimeType = "image/png",
            inlineAttachment = true,
            data = ByteArray(42),
        )
        val rfc822Data = Rfc822MessageDataProcessor(context).encodeToInternetMessageData(
            from = "Foo Bar <foo@bar.com>",
            to = listOf("Ted Bear <ted.bear@toys.org>"),
            cc = listOf("Andy Pandy <andy.pandy@toys.org>"),
            bcc = listOf("bar@foo.com"),
            subject = "Greetings from the toys",
            body = "Hello there from all the toys.",
            attachments = listOf(attachment),
            inlineAttachments = listOf(inlineAttachment),
            encryptionStatus = EncryptionStatus.ENCRYPTED,
        )

        val message = Rfc822MessageDataProcessor(context).parseInternetMessageData(rfc822Data)
        with(message) {
            from shouldContainExactlyInAnyOrder listOf("Foo Bar <foo@bar.com>")
            to shouldContainExactlyInAnyOrder listOf("Ted Bear <ted.bear@toys.org>")
            cc shouldContainExactlyInAnyOrder listOf("Andy Pandy <andy.pandy@toys.org>")
            bcc shouldContainExactlyInAnyOrder listOf("bar@foo.com")
            subject shouldBe "Greetings from the toys"
            body shouldBe "Encrypted message attached"
            isHtml shouldBe false
            with(attachments.first()) {
                fileName shouldBe attachment.fileName
                contentId shouldBe attachment.contentId
                mimeType shouldBe attachment.mimeType
                attachments.first().inlineAttachment shouldBe false
                data shouldBe attachment.data
            }
            inlineAttachments.isEmpty() shouldBe true
        }
    }

    private fun getRawEmail(rawFileName: String): ByteArray {
        val file = context.assets.open(rawFileName)
        return file.readBytes()
    }

    /**
     * Asserts that a string contains all of the supplied list of strings in the order defined in
     * the given list
     */
    private infix fun String.shouldContainOrderedStrings(strings: List<String>) {
        var lastIndex = 0
        strings.forEachIndexed { index, string ->
            val nextIndex = this.indexOf(string, lastIndex)
            assertTrue("Expected string '$string' not found", nextIndex != -1)
            if (index > 0) {
                assertTrue("Expected string '$string' not found after string '${strings[index - 1]}'", nextIndex > lastIndex)
            }
            lastIndex = nextIndex + string.length
        }
    }
}
