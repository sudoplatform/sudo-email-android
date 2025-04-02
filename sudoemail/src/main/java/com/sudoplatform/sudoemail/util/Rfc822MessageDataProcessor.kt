/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import android.content.Context
import android.os.Parcelable
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EncryptionStatus
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import jakarta.activation.DataHandler
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import jakarta.mail.util.ByteArrayDataSource
import kotlinx.parcelize.Parcelize
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.Exception
import java.util.Properties

@Parcelize
data class SimplifiedEmailMessage(
    val from: List<String>,
    val to: List<String>,
    val cc: List<String>,
    val bcc: List<String>,
    val subject: String?,
    val body: String?,
    val isHtml: Boolean,
    val attachments: List<EmailAttachment> = emptyList(),
    val inlineAttachments: List<EmailAttachment> = emptyList(),
    val replyingMessageId: String? = null,
    val forwardingMessageId: String? = null,
) : Parcelable

private const val EMAIL_HEADER_NAME_ENCRYPTION = "X-Sudoplatform-Encryption"
private const val PLATFORM_ENCRYPTION = "sudoplatform"

private const val CANNED_TEXT_BODY = "Encrypted message attached"

private val HTML_TAG_BODY_REGEX = "(?si)<html.*</html>".toRegex()

/**
 * A class which handles the processing of email message data which includes the encoding and
 * parsing of the RFC 822 compatible email message content.
 */
class Rfc822MessageDataProcessor(private val context: Context) : EmailMessageDataProcessor {

    private val logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))

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
        isHtml: Boolean,
        encryptionStatus: EncryptionStatus,
        replyingMessageId: String?,
        forwardingMessageId: String?,
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
        when {
            encryptionStatus == EncryptionStatus.ENCRYPTED -> {
                messageBodyPart.setText(CANNED_TEXT_BODY, "UTF-8")
                topMultiPart.addBodyPart(messageBodyPart)
            }
            isHtml -> {
                messageBodyPart.setContent(bodyText, MimeTypes.TEXT_HTML_UTF8)
                relatedMultipart.addBodyPart(messageBodyPart)
                topMultiPart.addBodyPart(relatedWrapper)
            }
            else -> {
                messageBodyPart.setText(bodyText, "UTF-8")
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

        if (!replyingMessageId.isNullOrEmpty()) {
            message.setHeader("In-Reply-To", "<$replyingMessageId>")
        }
        if (!forwardingMessageId.isNullOrEmpty()) {
            message.setHeader("References", "<$forwardingMessageId>")
        }

        message.setContent(topMultiPart)

        val byteOutputStream = ByteArrayOutputStream()
        message.writeTo(byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    @Throws(MessagingException::class, IOException::class)
    override fun parseInternetMessageData(rfc822Data: ByteArray): SimplifiedEmailMessage {
        val rfc822Input = ByteArrayInputStream(rfc822Data)
        val mimeMessage = MimeMessage(session, rfc822Input)
        return toSimplifiedEmailMessage(mimeMessage)
    }

    @Throws(MessagingException::class, IOException::class)
    private fun toSimplifiedEmailMessage(message: Message): SimplifiedEmailMessage {
        val from = message.from?.map { it.toString() } ?: emptyList()
        val to = message.getRecipients(Message.RecipientType.TO)?.map { it.toString() } ?: emptyList()
        val cc = message.getRecipients(Message.RecipientType.CC)?.map { it.toString() } ?: emptyList()
        val bcc = message.getRecipients(Message.RecipientType.BCC)?.map { it.toString() } ?: emptyList()

        val parts = mutableListOf<ViewablePart>()
        val allAttachments = mutableListOf<EmailAttachment>()

        // Partition and collect the parts of the email message.
        partitionEmailBody(message, parts, allAttachments)

        // Build the email body based on the partitioned parts.
        val (body, isHtml) = buildEmailBody(parts)

        // Determine if attachments are inline or regular attachments based on the fact the CID appears in the body.
        // The content disposition header does not seem to be reliable.
        val (inlineAttachments, attachments) = allAttachments.partition {
            it.inlineAttachment || (it.contentId.isNotEmpty() && body.contains("cid:${it.contentId}"))
        }.let { (inline, regular) ->
            inline.map { it.copy(inlineAttachment = true) } to regular.map { it.copy(inlineAttachment = false) }
        }

        return SimplifiedEmailMessage(
            from,
            to,
            cc,
            bcc,
            message.subject,
            body,
            isHtml,
            attachments,
            inlineAttachments,
        )
    }

    /**
     * Partition the email body [Part]s into [ViewablePart]s and [EmailAttachment]s.
     *
     * This function is called recursively until all email body parts have been checked.
     *
     * @param message [Part] The email body part to partition.
     * @param parts [MutableList<ViewablePart>] The email body parts which contain `text/..` MIME type
     *  or [Message] parts.
     * @param attachments [MutableList<EmailAttachment>] The email attachments which are inline or
     *  regular attachments.
     */
    private fun partitionEmailBody(message: Part, parts: MutableList<ViewablePart>, attachments: MutableList<EmailAttachment>) {
        when {
            message.fileName != null -> attachments.add(extractAttachment(message))
            message.isMimeType(MimeTypes.MULTIPART_ALTERNATIVE) -> {
                chooseAlternative(message.content as Multipart)?.let { childParts ->
                    parts.addAll(childParts.parts)
                    attachments.addAll(childParts.attachments)
                }
            }
            message.isMimeType(MimeTypes.MULTIPART) -> {
                val multipart = message.content as Multipart
                for (i in 0 until multipart.count) {
                    val bodyPart = multipart.getBodyPart(i)
                    partitionEmailBody(bodyPart, parts, attachments)
                }
            }
            message.isMimeType(MimeTypes.RFC822_MESSAGE) -> {
                val partContent = message.content as? Part
                if (partContent is Message) {
                    parts.add(ViewableMessageHeader(partContent))
                    partitionEmailBody(partContent, parts, attachments)
                }
            }
            message.isMimeType(MimeTypes.CALENDAR) -> attachments.add(extractAttachment(message, "invite.ics"))
            message.isMimeType(MimeTypes.TEXT_ANY) -> parts.add(ViewableBodyPart(message))
            else -> logger.error("Unhandled part. content-type ${message.contentType}, disposition: ${message.disposition}")
        }
    }

    /**
     * Build an email body string based on the given [ViewablePart]s.
     *
     * @param parts [ViewablePart] A list of [ViewablePart]s.
     * @return The [ViewableBody] containing email text body and HTML flag.
     */
    private fun buildEmailBody(parts: List<ViewablePart>): ViewableBody {
        val isHtml = parts.any { it.part.isMimeType(MimeTypes.TEXT_HTML) } || parts.any { it is ViewableMessageHeader }
        val body = parts.mapNotNull {
            if (isHtml) {
                it.asHtmlText(context)
            } else {
                it.asPlainText(context)
            }
        }.joinToString(separator = if (isHtml) "\n<br>\n" else "\n\n").let { text ->
            // There can be instances where a HTML body is not wrapped in <html> tags, add them.
            if (isHtml && !HTML_TAG_BODY_REGEX.containsMatchIn(text)) {
                "<html>$text</html>"
            } else {
                text
            }
        }
        return ViewableBody(body, isHtml)
    }

    /**
     * Extracts the attachment data based on the given [Part].
     *
     * @param part [Part] The part to extract the attachment from.
     * @param defaultFileName [String] The file name of the attachment.
     * @return The [EmailAttachment].
     */
    private fun extractAttachment(part: Part, defaultFileName: String = "unknown"): EmailAttachment {
        val mimeType = part.contentType.substringBefore(';').trim()
        val decodedFileName = try {
            MimeUtility.decodeText(part.fileName)
        } catch (e: Exception) {
            defaultFileName
        }
        val contentId = part.getHeader("Content-ID")?.firstOrNull()?.trim('<', '>') ?: ""
        val data = (part.content as? InputStream) ?: part.inputStream
        val isInLine = part.disposition.equals(Part.INLINE, true)
        return EmailAttachment(decodedFileName, contentId, mimeType, isInLine, data.readBytes())
    }

    /**
     * See [RFC][https://www.rfc-editor.org/rfc/rfc2046.html#section-5.1.4]
     *
     * Correctly handles the uncommon RFC `multipart/alternative` structure.
     *
     * The most common `multipart/alternative` structure is:
     * ```
     *   multipart/alternative
     *     text/plain
     *     text/html
     * ```
     * However RFC standard does not limit the structure, so it could be anything like:
     * ```
     *   multipart/alternative
     *     text/plain
     *     multipart/related
     *       text/html
     *       image/jpeg
     * ```
     *
     * @param alternative [Multipart] The `multipart/alternative` from which to choose the most
     *  appropriate child part.
     * @return The collected parts and attachments of the chosen child.
     */
    private fun chooseAlternative(alternative: Multipart): CollectedParts? {
        val parts: MutableList<ViewablePart> = mutableListOf()
        val attachments: MutableList<EmailAttachment> = mutableListOf()
        var index = alternative.count - 1
        while (index >= 0) {
            val childPart = alternative.getBodyPart(index--)
            partitionEmailBody(childPart, parts, attachments)
            if (parts.any { it.part.isMimeType(MimeTypes.TEXT_HTML) }) {
                break
            }
        }
        return if (parts.isEmpty() && attachments.isEmpty()) {
            null
        } else {
            CollectedParts(parts, attachments)
        }
    }
}
