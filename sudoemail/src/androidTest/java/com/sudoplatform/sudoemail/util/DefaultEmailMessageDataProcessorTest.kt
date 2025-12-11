/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressPublicInfoEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressPublicKeyEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PublicKeyFormatEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailAttachmentEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.util.DefaultEmailMessageDataProcessor
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.types.SecureEmailAttachmentType
import com.sudoplatform.sudoemail.secure.types.SecurePackage
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import java.util.UUID

/**
 * Test the correct operation of the [DefaultEmailMessageDataProcessor] encoding and parsing.
 * Note: Part of these tests do mock the EmailCryptoService and those tests may make more sense
 * as unit tests, but due to the implementation needing a Session, they must be done here.
 */
@RunWith(AndroidJUnit4::class)
class DefaultEmailMessageDataProcessorTest {
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
    private val fromInput = "Foo Bar <foo@bar.com>"
    private val toInput = "Ted Bear <ted.bear@toys.org>"
    private val ccInput = "Andy Pandy <andy.pandy@toys.org>"
    private val bccInput = "bar@foo.com"
    private val subjectInput = "Greetings from the toys"
    private val bodyInput = "Hello there from all the toys. "

    private val keyExchangeAttachment =
        EmailAttachmentEntity(
            fileName = "keyExchange",
            contentId = SecureEmailAttachmentType.KEY_EXCHANGE.contentId,
            mimeType = "application/octet-stream",
            inlineAttachment = false,
            data = ByteArray(256),
        )

    private val plainTextBody = "This is a plain text email body."

    private val bodyAttachment =
        EmailAttachmentEntity(
            fileName = "body",
            contentId = SecureEmailAttachmentType.BODY.contentId,
            mimeType = "application/octet-stream",
            inlineAttachment = false,
            data = plainTextBody.toByteArray(),
        )

    private val mockPublicInfo =
        listOf(
            EmailAddressPublicInfoEntity(
                emailAddress = "recipient@example.com",
                keyId = "keyId",
                publicKeyDetails =
                    EmailAddressPublicKeyEntity(
                        publicKey = "mockKey",
                        keyFormat = PublicKeyFormatEntity.SPKI,
                        algorithm = "algorithm",
                    ),
            ),
        )

    private val mockEmailCryptoService =
        mock<EmailCryptoService>().stub {
            onBlocking { encrypt(any<ByteArray>(), any()) } doReturn
                SecurePackage(
                    keyAttachments = setOf(keyExchangeAttachment),
                    bodyAttachment = bodyAttachment,
                )
        }

    @Test
    fun shouldEncodeBasicMessage() {
        val rfc822Data =
            DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                from = fromInput,
                to = listOf(toInput),
                cc = listOf(ccInput),
                bcc = listOf(bccInput),
                subject = subjectInput,
                body = bodyInput,
                isHtml = false,
                encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
            )

        val message = String(rfc822Data)

        message.shouldContainOrderedStrings(
            listOf(
                "From: $fromInput",
                "To: $toInput",
                "Cc: $ccInput",
                "Bcc: $bccInput",
                "Subject: $subjectInput",
                "Content-Type: text/plain",
                bodyInput,
            ),
        )
    }

    @Test
    fun shouldEncodeHtmlMessage() {
        val rfc822Data =
            DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                from = fromInput,
                to = listOf(toInput),
                cc = listOf(ccInput),
                bcc = listOf(bccInput),
                subject = subjectInput,
                body = "Hello<div>there</div><div>from all the toys</div>",
                isHtml = true,
                encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
            )

        val message = String(rfc822Data)

        message.shouldContainOrderedStrings(
            listOf(
                "From: $fromInput",
                "To: $toInput",
                "Cc: $ccInput",
                "Bcc: $bccInput",
                "Subject: $subjectInput",
                "Content-Type: text/html; charset=UTF-8",
                "Hello<div>there</div><div>from all the toys</div>",
            ),
        )
    }

    @Test
    fun shouldEncodeMessageWithAttachments() {
        val rfc822Data =
            DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                from = fromInput,
                to = listOf(toInput),
                cc = listOf(ccInput),
                bcc = listOf(bccInput),
                subject = subjectInput,
                body =
                    "<div \"ltr\"><br>\n" +
                        "<img \n" +
                        "src=\"file:///path/to/my/image/some_pic.png\" height=\"156\"\n" +
                        "alt=\"ii_ia6yo3z92_14d962f8450cc6f1\" width=144>\n" +
                        "<br>\n" +
                        "Hello there from all the toys<br></div>",
                inlineAttachments =
                    listOf(
                        EmailAttachmentEntity(
                            fileName = "some_pic.png",
                            contentId = "ii_ia6yo3z92_14d962f8450cc6f1",
                            mimeType = "image/png",
                            inlineAttachment = true,
                            data = "This is an inline attachment".toByteArray(),
                        ),
                    ),
                attachments =
                    listOf(
                        EmailAttachmentEntity(
                            fileName = "Path",
                            contentId = "1234",
                            mimeType = "image/png",
                            inlineAttachment = false,
                            data = "sample data".toByteArray(),
                        ),
                    ),
                isHtml = true,
                encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
            )

        val message = String(rfc822Data)

        message.shouldContainOrderedStrings(
            listOf(
                "From: $fromInput",
                "To: $toInput",
                "Cc: $ccInput",
                "Bcc: $bccInput",
                "Subject: $subjectInput",
                "Content-Type: text/html; charset=UTF-8",
                "Hello there from all the toys<br></div>",
                "Content-Type: image/png; name=some_pic.png",
                "Content-Disposition: inline; filename=some_pic.png",
                "This is an inline attachment",
                "Content-Type: image/png; name=Path",
                "Content-Disposition: attachment; filename=Path",
                "sample data",
            ),
        )
    }

    @Test
    fun shouldParseBasicMessage() {
        val rfc822Data =
            DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                from = fromInput,
                to = listOf(toInput),
                cc = listOf(ccInput),
                bcc = listOf(bccInput),
                subject = subjectInput,
                body = bodyInput,
                encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
            )

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rfc822Data)
        with(message) {
            from shouldContainExactlyInAnyOrder listOf(fromInput)
            to shouldContainExactlyInAnyOrder listOf(toInput)
            cc shouldContainExactlyInAnyOrder listOf(ccInput)
            bcc shouldContainExactlyInAnyOrder listOf(bccInput)
            subject shouldBe subjectInput
            body shouldBe bodyInput
            isHtml shouldBe false
            attachments.isEmpty() shouldBe true
            inlineAttachments.isEmpty() shouldBe true
        }
    }

    @Test
    fun shouldParseMessageWithNullFields() {
        val rfc822Data =
            DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                from = fromInput,
                to = listOf(toInput),
                cc = null,
                bcc = null,
                subject = null,
                body = bodyInput,
                encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
            )

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rfc822Data)
        with(message) {
            from shouldContainExactlyInAnyOrder listOf(fromInput)
            to shouldContainExactlyInAnyOrder listOf(toInput)
            cc shouldContainExactlyInAnyOrder emptyList()
            bcc shouldContainExactlyInAnyOrder emptyList()
            subject shouldBe null
            body shouldBe bodyInput
            isHtml shouldBe false
            attachments.isEmpty() shouldBe true
            inlineAttachments.isEmpty() shouldBe true
        }
    }

    @Test
    fun shouldParseMessageWithAttachments() {
        val attachment =
            EmailAttachmentEntity(
                fileName = "goodExtension.pdf",
                contentId = UUID.randomUUID().toString(),
                mimeType = "application/pdf",
                inlineAttachment = false,
                data = "This file has a valid file extension".toByteArray(),
            )
        val inlineAttachment =
            EmailAttachmentEntity(
                fileName = "goodImage.png",
                contentId = UUID.randomUUID().toString(),
                mimeType = "image/png",
                inlineAttachment = true,
                data = ByteArray(42),
            )
        val rfc822Data =
            DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                from = fromInput,
                to = listOf(toInput),
                cc = listOf(ccInput),
                bcc = listOf(bccInput),
                subject = subjectInput,
                body = bodyInput,
                attachments = listOf(attachment),
                inlineAttachments = listOf(inlineAttachment),
                encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
            )

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rfc822Data)
        with(message) {
            from shouldContainExactlyInAnyOrder listOf(fromInput)
            to shouldContainExactlyInAnyOrder listOf(toInput)
            cc shouldContainExactlyInAnyOrder listOf(ccInput)
            bcc shouldContainExactlyInAnyOrder listOf(bccInput)
            subject shouldBe subjectInput
            body shouldBe bodyInput
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

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rawEmail)

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

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rawEmail)

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

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rawEmail)

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

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rawEmail)

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

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rawEmail)

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

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rawEmail)

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

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rawEmail)

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

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rawEmail)

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

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rawEmail)

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

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rawEmail)

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
        val rfc822Data =
            DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                from = fromInput,
                to = listOf(toInput),
                cc = listOf(ccInput),
                bcc = listOf(bccInput),
                subject = subjectInput,
                body = bodyInput,
                encryptionStatus = EncryptionStatusEntity.ENCRYPTED,
            )

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rfc822Data)
        with(message) {
            from shouldContainExactlyInAnyOrder listOf(fromInput)
            to shouldContainExactlyInAnyOrder listOf(toInput)
            cc shouldContainExactlyInAnyOrder listOf(ccInput)
            bcc shouldContainExactlyInAnyOrder listOf(bccInput)
            subject shouldBe subjectInput
            body shouldBe "Encrypted message attached"
            isHtml shouldBe false
        }
    }

    @Test
    fun shouldParseEncryptedMessageWithAttachments() {
        val attachment =
            EmailAttachmentEntity(
                fileName = "goodExtension.pdf",
                contentId = UUID.randomUUID().toString(),
                mimeType = "application/pdf",
                inlineAttachment = false,
                data = "This file has a valid file extension".toByteArray(),
            )
        val inlineAttachment =
            EmailAttachmentEntity(
                fileName = "goodImage.png",
                contentId = UUID.randomUUID().toString(),
                mimeType = "image/png",
                inlineAttachment = true,
                data = ByteArray(42),
            )
        val rfc822Data =
            DefaultEmailMessageDataProcessor(context).encodeToInternetMessageData(
                from = fromInput,
                to = listOf(toInput),
                cc = listOf(ccInput),
                bcc = listOf(bccInput),
                subject = subjectInput,
                body = bodyInput,
                attachments = listOf(attachment),
                inlineAttachments = listOf(inlineAttachment),
                encryptionStatus = EncryptionStatusEntity.ENCRYPTED,
            )

        val message = DefaultEmailMessageDataProcessor(context).parseInternetMessageData(rfc822Data)
        with(message) {
            from shouldContainExactlyInAnyOrder listOf(fromInput)
            to shouldContainExactlyInAnyOrder listOf(toInput)
            cc shouldContainExactlyInAnyOrder listOf(ccInput)
            bcc shouldContainExactlyInAnyOrder listOf(bccInput)
            subject shouldBe subjectInput
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

    @Test
    fun processMessageDataShouldProcessUnencryptedMessage() =
        runTest {
            val processor =
                DefaultEmailMessageDataProcessor(
                    context,
                    mockEmailCryptoService,
                )
            val simplifiedMessage =
                processor.parseInternetMessageData(
                    processor.encodeToInternetMessageData(
                        from = fromInput,
                        to = listOf(toInput),
                        cc = null,
                        bcc = null,
                        subject = subjectInput,
                        body = plainTextBody,
                        attachments = null,
                        inlineAttachments = null,
                        isHtml = false,
                        encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
                        replyingMessageId = null,
                        forwardingMessageId = null,
                    ),
                )

            val result =
                processor.processMessageData(
                    messageData = simplifiedMessage,
                    encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
                    emailAddressesPublicInfo = emptyList(),
                )

            result shouldNotBe null
            result.size shouldNotBe 0

            // Verify it can be parsed back
            val parsed = processor.parseInternetMessageData(result)
            parsed.from shouldBe listOf(fromInput)
        }

    @Test
    fun processMessageDataShouldProcessEncryptedMessage() =
        runTest {
            val processor =
                DefaultEmailMessageDataProcessor(
                    context,
                    mockEmailCryptoService,
                )
            val simplifiedMessage =
                processor.parseInternetMessageData(
                    processor.encodeToInternetMessageData(
                        from = fromInput,
                        to = listOf(toInput),
                        cc = null,
                        bcc = null,
                        subject = subjectInput,
                        body = plainTextBody,
                        attachments = null,
                        inlineAttachments = null,
                        isHtml = false,
                        encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
                        replyingMessageId = null,
                        forwardingMessageId = null,
                    ),
                )

            val result =
                processor.processMessageData(
                    messageData = simplifiedMessage,
                    encryptionStatus = EncryptionStatusEntity.ENCRYPTED,
                    emailAddressesPublicInfo = mockPublicInfo,
                )

            result shouldNotBe null
            result.size shouldNotBe 0

            verify(mockEmailCryptoService).encrypt(any<ByteArray>(), any())

            // Verify encryption header is present
            val rfc822String = String(result)
            rfc822String.contains("X-Sudoplatform-Encryption: sudoplatform") shouldBe true
        }

    @Test
    fun processMessageDataShouldThrowWhenEmailCryptoServiceIsNullForEncryptedMessage() =
        runTest {
            val processor =
                DefaultEmailMessageDataProcessor(context)
            val simplifiedMessage =
                processor.parseInternetMessageData(
                    processor.encodeToInternetMessageData(
                        from = fromInput,
                        to = listOf(toInput),
                        cc = null,
                        bcc = null,
                        subject = subjectInput,
                        body = plainTextBody,
                        attachments = null,
                        inlineAttachments = null,
                        isHtml = false,
                        encryptionStatus = EncryptionStatusEntity.UNENCRYPTED,
                        replyingMessageId = null,
                        forwardingMessageId = null,
                    ),
                )

            val exception =
                shouldThrow<IllegalArgumentException> {
                    processor.processMessageData(
                        messageData = simplifiedMessage,
                        encryptionStatus = EncryptionStatusEntity.ENCRYPTED,
                        emailAddressesPublicInfo = mockPublicInfo,
                    )
                }

            exception.message shouldBe "EmailCryptoService is required to encrypt email message data"
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
