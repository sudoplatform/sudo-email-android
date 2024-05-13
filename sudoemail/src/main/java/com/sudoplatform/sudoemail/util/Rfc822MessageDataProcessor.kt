/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import android.os.Parcelable
import com.sudoplatform.sudoemail.secure.types.LEGACY_BODY_MIME_TYPE
import com.sudoplatform.sudoemail.secure.types.LEGACY_KEY_EXCHANGE_MIME_TYPE
import com.sudoplatform.sudoemail.secure.types.SecureEmailAttachmentType
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EncryptionStatus
import jakarta.activation.DataHandler
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import kotlinx.parcelize.Parcelize
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Properties

@Parcelize
data class SimplifiedEmailMessage(
    val from: List<String>,
    val to: List<String>,
    val cc: List<String>,
    val bcc: List<String>,
    val subject: String?,
    val body: String?,
    val attachments: List<EmailAttachment> = emptyList(),
    val inlineAttachments: List<EmailAttachment> = emptyList(),
) : Parcelable

private const val EMAIL_HEADER_NAME_ENCRYPTION = "X-Sudoplatform-Encryption"
private const val PLATFORM_ENCRYPTION = "sudoplatform"

private const val CANNED_TEXT_BODY = "Encrypted message attached"

/**
 * A class which handles the processing of email message data which includes the encoding and
 * parsing of the RFC 822 compatible email message content.
 */
class Rfc822MessageDataProcessor : EmailMessageDataProcessor {

    private val session = Session.getInstance(Properties())

    @Throws(MessagingException::class, IOException::class)
    override fun encodeToInternetMessageData(
        from: String,
        to: List<String>,
        cc: List<String>?,
        bcc: List<String>?,
        subject: String?,
        body: String?,
        attachments: List<EmailAttachment>?,
        inlineAttachments: List<EmailAttachment>?,
        encryptionStatus: EncryptionStatus,
    ): ByteArray {
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(from))
        to.map {
            if (it.isNotBlank()) {
                message.addRecipient(
                    Message.RecipientType.TO,
                    InternetAddress(it),
                )
            }
        }
        cc?.map {
            if (it.isNotBlank()) {
                message.addRecipient(
                    Message.RecipientType.CC,
                    InternetAddress(it),
                )
            }
        }
        bcc?.map {
            if (it.isNotBlank()) {
                message.addRecipient(
                    Message.RecipientType.BCC,
                    InternetAddress(it),
                )
            }
        }
        message.subject = subject

        // Check if encrypted then add a custom header
        if (encryptionStatus == EncryptionStatus.ENCRYPTED) {
            message.setHeader(EMAIL_HEADER_NAME_ENCRYPTION, PLATFORM_ENCRYPTION)
        }

        val topMultiPart = MimeMultipart() // The top level-multipart of the email
        val messageBodyPart = MimeBodyPart() // The body-part which holds the message body text

        // The related multi-part contains the message body and any inline attachments
        val relatedWrapper = MimeBodyPart()
        val relatedMultipart = MimeMultipart("related")
        relatedWrapper.setContent(relatedMultipart)

        val bodyText = body ?: ""
        when (encryptionStatus) {
            EncryptionStatus.ENCRYPTED -> {
                messageBodyPart.setText(CANNED_TEXT_BODY, "UTF-8")
                topMultiPart.addBodyPart(messageBodyPart)
            }

            else -> {
                messageBodyPart.setText(bodyText, "UTF-8")
                messageBodyPart.setHeader("Content-Transfer-Encoding", "QUOTED-PRINTABLE")
                messageBodyPart.setHeader("Content-Disposition", "inline")
                messageBodyPart.setHeader("Content-Type", "text/plain")
                relatedMultipart.addBodyPart(messageBodyPart)
                topMultiPart.addBodyPart(relatedWrapper)
            }
        }

        attachments?.forEach { attachment ->
            val attachmentBodyPart = MimeBodyPart()
            attachmentBodyPart.disposition = Part.ATTACHMENT
            attachmentBodyPart.fileName = attachment.fileName
            attachmentBodyPart.dataHandler =
                DataHandler(ByteArrayDataSource(attachment.data, attachment.mimeType))
            attachmentBodyPart.contentID = "<${attachment.contentId}>"

            // Add normal attachments to the top level multi-part
            topMultiPart.addBodyPart(attachmentBodyPart)
        }

        inlineAttachments?.forEach { inlineAttachment ->
            val attachmentBodyPart = MimeBodyPart()
            attachmentBodyPart.disposition = Part.INLINE
            attachmentBodyPart.fileName = inlineAttachment.fileName
            attachmentBodyPart.dataHandler =
                DataHandler(ByteArrayDataSource(inlineAttachment.data, inlineAttachment.mimeType))
            attachmentBodyPart.contentID = "<${inlineAttachment.contentId}>"

            // Add inline attachments to the "related" multipart
            relatedMultipart.addBodyPart(attachmentBodyPart)
        }

        message.setContent(topMultiPart)

        val byteOutputStream = ByteArrayOutputStream()
        message.writeTo(byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    @Throws(MessagingException::class, IOException::class)
    override fun parseInternetMessageData(rfc822Data: ByteArray): SimplifiedEmailMessage {
        val rfc822Input = ByteArrayInputStream(rfc822Data)
        val javaMailMessage = LocalMimeMessage(session, rfc822Input)
        return toSimplifiedEmailMessage(javaMailMessage)
    }

    private class LocalMimeMessage(
        session: Session,
        rfc822Input: InputStream,
    ) : MimeMessage(session, rfc822Input) {
        override fun toString(): String {
            val from = from?.map { it.toString() } ?: emptyList()
            val to = getRecipients(Message.RecipientType.TO)?.map { it.toString() } ?: emptyList()
            val cc = getRecipients(Message.RecipientType.CC)?.map { it.toString() } ?: emptyList()
            val bcc = getRecipients(Message.RecipientType.BCC)?.map { it.toString() } ?: emptyList()

            return buildString {
                if (dataHandler.dataSource.contentType.contains("text/plain")) {
                    appendLine(dataHandler.content)
                } else {
                    appendLine("MimeMessage [")
                    appendLine("  From: $from")
                    appendLine("  To: $to")
                    appendLine("  Cc: $cc")
                    appendLine("  Bcc: $bcc")
                    appendLine("  Subject: $subject")
                    appendLine("  Received: $receivedDate")
                    appendLine("  ContentType: $contentType")
                    appendLine("  Description: $description")
                    appendLine("  Encoding: $encoding")
                    appendLine("  Size: $size")
                    val multipart = MimeMultipart(dataHandler.dataSource)
                    appendLine("  Preamble: ${multipart.preamble}")
                    for (i in 0 until multipart.count) {
                        val bodyPart = multipart.getBodyPart(i)
                        appendLine("  Body Part $i [")
                        appendLine("    ContentType: ${bodyPart.contentType}")
                        appendLine("    Description: ${bodyPart.description}")
                        appendLine("    Size: ${bodyPart.size}")
                        if (bodyPart.contentType.contains("text/plain")) {
                            appendLine("    Content: ${bodyPart.content}")
                        }
                        val disposition = bodyPart.disposition
                        if (disposition != null && (disposition.equals(Part.ATTACHMENT, true))) {
                            val attachmentDateSource = bodyPart.dataHandler.dataSource
                            val contentId = bodyPart.getHeader("Content-ID")
                            appendLine("    Attachment: ${bodyPart.fileName}")
                            appendLine("    Content-Disposition: ${bodyPart.disposition}")
                            appendLine("    Content-Type: ${bodyPart.contentType}")
                            appendLine("    Content-ID: $contentId")
                        }
                        if (disposition != null && (
                                bodyPart.disposition.equals(
                                    Part.INLINE,
                                    true,
                                )
                                )
                        ) {
                            val contentId = bodyPart.getHeader("Content-ID")
                            if (contentId != null) {
                                appendLine("    Inline Attachment: ${bodyPart.fileName}")
                                appendLine("    Content-Disposition: ${bodyPart.disposition}")
                                appendLine("    Content-Type: ${bodyPart.contentType}")
                                appendLine("    Content-ID: $contentId")
                            }
                        }
                        appendLine("  ]")
                    }
                    appendLine("]")
                }
            }
        }
    }

    @Throws(MessagingException::class, IOException::class)
    private fun toSimplifiedEmailMessage(message: Message): SimplifiedEmailMessage {
        val from = message.from?.map { it.toString() } ?: emptyList()
        val to =
            message.getRecipients(Message.RecipientType.TO)?.map { it.toString() } ?: emptyList()
        val cc =
            message.getRecipients(Message.RecipientType.CC)?.map { it.toString() } ?: emptyList()
        val bcc =
            message.getRecipients(Message.RecipientType.BCC)?.map { it.toString() } ?: emptyList()

        val body: String
        val attachments: MutableList<EmailAttachment> = mutableListOf()
        val inlineAttachments: MutableList<EmailAttachment> = mutableListOf()
        if (message.isMimeType("text/plain")) {
            body = buildString { append(message) }
        } else {
            val multipart = MimeMultipart(message.dataHandler.dataSource)
            body = buildString {
                for (i in 0 until multipart.count) {
                    val bodyPart = multipart.getBodyPart(i)
                    if (bodyPart.isMimeType("text/plain")) {
                        append(bodyPart.content)
                    } else if (bodyPart.isMimeType("multipart/*")) {
                        val mimeMultipart = bodyPart.content as MimeMultipart
                        for (j in 0 until mimeMultipart.count) {
                            val mimeBodyPart = mimeMultipart.getBodyPart(j)
                            if (mimeBodyPart.isMimeType("text/plain")) {
                                append(mimeBodyPart.content)
                            }
                        }
                    } else if (
                        bodyPart.isMimeType(SecureEmailAttachmentType.KEY_EXCHANGE.mimeType) ||
                        bodyPart.isMimeType(SecureEmailAttachmentType.BODY.mimeType) ||
                        bodyPart.isMimeType(LEGACY_KEY_EXCHANGE_MIME_TYPE) ||
                        bodyPart.isMimeType(LEGACY_BODY_MIME_TYPE)
                    ) {
                        val secureAttachmentPart = multipart.getBodyPart(i)
                        val disposition = secureAttachmentPart.disposition
                        if (disposition != null) {
                            var contentId = secureAttachmentPart.getHeader("Content-ID").first()
                            contentId = contentId.replace("<", "").replace(">", "")
                            val attachment = EmailAttachment(
                                secureAttachmentPart.fileName,
                                contentId,
                                secureAttachmentPart.contentType,
                                false,
                                secureAttachmentPart.inputStream.readBytes(),
                            )
                            attachments.add(attachment)
                        }
                    }
                }
            }
        }
        return SimplifiedEmailMessage(
            from,
            to,
            cc,
            bcc,
            message.subject,
            body,
            attachments.toList(),
            inlineAttachments.toList(),
        )
    }
}
