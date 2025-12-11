/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.util

import android.content.Context
import android.net.Uri
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressPublicInfoEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailAttachmentEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SimplifiedEmailMessageEntity
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.util.MimeTypes
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Properties

private const val EMAIL_HEADER_NAME_ENCRYPTION = "X-Sudoplatform-Encryption"
private const val PLATFORM_ENCRYPTION = "sudoplatform"

private const val CANNED_TEXT_BODY = "Encrypted message attached"

private val HTML_TAG_BODY_REGEX = "(?si)<html.*</html>".toRegex()

internal class DefaultEmailMessageDataProcessor(
    private val context: Context,
    private val emailCryptoService: EmailCryptoService? = null,
) : EmailMessageDataProcessor {
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
        attachments: List<EmailAttachmentEntity>?,
        inlineAttachments: List<EmailAttachmentEntity>?,
        isHtml: Boolean,
        encryptionStatus: EncryptionStatusEntity,
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
        if (encryptionStatus == EncryptionStatusEntity.ENCRYPTED) {
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
            encryptionStatus == EncryptionStatusEntity.ENCRYPTED -> {
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
    override fun parseInternetMessageData(rfc822Data: ByteArray): SimplifiedEmailMessageEntity {
        val rfc822Input = ByteArrayInputStream(rfc822Data)
        val mimeMessage = MimeMessage(session, rfc822Input)
        return toSimplifiedEmailMessage(mimeMessage)
    }

    override suspend fun processMessageData(
        messageData: SimplifiedEmailMessageEntity,
        encryptionStatus: EncryptionStatusEntity,
        emailAddressesPublicInfo: List<EmailAddressPublicInfoEntity>,
    ): ByteArray {
        val cleanBody =
            if (messageData.inlineAttachments.isNotEmpty() && messageData.body.isNullOrEmpty()) {
                replaceInlinePathsWithCids(
                    messageData.body!!,
                    messageData.inlineAttachments,
                )
            } else {
                messageData.body
            }

        var rfc822Data =
            encodeToInternetMessageData(
                from = messageData.from[0],
                to = messageData.to,
                cc = messageData.cc,
                bcc = messageData.bcc,
                subject = messageData.subject,
                cleanBody,
                messageData.attachments,
                messageData.inlineAttachments,
                isHtml = true,
                EncryptionStatusEntity.UNENCRYPTED,
                messageData.replyingMessageId,
                messageData.forwardingMessageId,
            )

        if (encryptionStatus == EncryptionStatusEntity.ENCRYPTED) {
            requireNotNull(emailCryptoService) {
                "EmailCryptoService is required to encrypt email message data"
            }
            val secureAttachments = emailCryptoService.encrypt(rfc822Data, emailAddressesPublicInfo).toList()

            // Encode the RFC 822 data with the secureAttachments
            rfc822Data =
                encodeToInternetMessageData(
                    from = messageData.from[0],
                    to = messageData.to,
                    cc = messageData.cc,
                    bcc = messageData.bcc,
                    subject = messageData.subject,
                    messageData.body,
                    secureAttachments,
                    messageData.inlineAttachments,
                    isHtml = false,
                    encryptionStatus = encryptionStatus,
                    messageData.replyingMessageId,
                    messageData.forwardingMessageId,
                )
        }
        return rfc822Data
    }

    @Throws(MessagingException::class, IOException::class)
    private fun toSimplifiedEmailMessage(message: Message): SimplifiedEmailMessageEntity {
        val from = message.from?.map { it.toString() } ?: emptyList()
        val to = message.getRecipients(Message.RecipientType.TO)?.map { it.toString() } ?: emptyList()
        val cc = message.getRecipients(Message.RecipientType.CC)?.map { it.toString() } ?: emptyList()
        val bcc = message.getRecipients(Message.RecipientType.BCC)?.map { it.toString() } ?: emptyList()

        val parts = mutableListOf<ViewablePart>()
        val allAttachments = mutableListOf<EmailAttachmentEntity>()

        // Partition and collect the parts of the email message.
        partitionEmailBody(message, parts, allAttachments)

        // Build the email body based on the partitioned parts.
        val (body, isHtml) = buildEmailBody(parts)

        // Determine if attachments are inline or regular attachments based on the fact the CID appears in the body.
        // The content disposition header does not seem to be reliable.
        val (inlineAttachments, attachments) =
            allAttachments
                .partition {
                    it.inlineAttachment || (it.contentId.isNotEmpty() && body.contains("cid:${it.contentId}"))
                }.let { (inline, regular) ->
                    inline.map { it.copy(inlineAttachment = true) } to regular.map { it.copy(inlineAttachment = false) }
                }

        return SimplifiedEmailMessageEntity(
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
     * Partition the email body [Part]s into [ViewablePart]s and [EmailAttachmentEntity]s.
     *
     * This function is called recursively until all email body parts have been checked.
     *
     * @param message [Part] The email body part to partition.
     * @param parts [MutableList<ViewablePart>] The email body parts which contain `text/..` MIME type
     *  or [Message] parts.
     * @param attachments [MutableList<EmailAttachmentEntity>] The email attachments which are inline or
     *  regular attachments.
     */
    private fun partitionEmailBody(
        message: Part,
        parts: MutableList<ViewablePart>,
        attachments: MutableList<EmailAttachmentEntity>,
    ) {
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
        val body =
            parts
                .mapNotNull {
                    if (isHtml) {
                        it.asHtmlText(context)
                    } else {
                        it.asPlainText(context)
                    }
                }.joinToString(separator = if (isHtml) "\n<br>\n" else "\n\n")
                .let { text ->
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
     * @return The [EmailAttachmentEntity].
     */
    private fun extractAttachment(
        part: Part,
        defaultFileName: String = "unknown",
    ): EmailAttachmentEntity {
        val mimeType = part.contentType.substringBefore(';').trim()
        val decodedFileName =
            try {
                MimeUtility.decodeText(part.fileName)
            } catch (e: Exception) {
                defaultFileName
            }
        val contentId = part.getHeader("Content-ID")?.firstOrNull()?.trim('<', '>') ?: ""
        val data = (part.content as? InputStream) ?: part.inputStream
        val isInLine = part.disposition.equals(Part.INLINE, true)
        return EmailAttachmentEntity(decodedFileName, contentId, mimeType, isInLine, data.readBytes())
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
        val attachments: MutableList<EmailAttachmentEntity> = mutableListOf()
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

    companion object {
        private const val SRC_TAG = "src"
        private const val FILE_TAG = "file:"
        private const val CID_TAG = "cid:"
        private const val STYLE = "style=\"width: 288px;\""

        /** Regex to match all image tags. */
        private val IMAGE_SOURCE_REGEX = "<img[^>]*>".toRegex()

        /** Regex to identify the parameter type and the value within the tag. */
        private val IMAGE_PARAMETERS_REGEX = "(src|alt)=\"([^\"\\s]*)\"".toRegex()

        fun replaceInlinePathsWithCids(
            htmlBody: String,
            inlineAttachments: List<EmailAttachmentEntity>,
        ): String? {
            if (htmlBody.isBlank()) return null

            val inlineAttachmentMap = inlineAttachments.associateBy { it.fileName }
            var cleanHTML = htmlBody

            // Find inline images with tags and replace paths or cids with the correct values
            if (IMAGE_SOURCE_REGEX.containsMatchIn(cleanHTML)) {
                val matches = IMAGE_SOURCE_REGEX.findAll(cleanHTML)
                matches.forEach {
                    val fullTag = it.value
                    val tags = IMAGE_PARAMETERS_REGEX.findAll(fullTag)
                    // If no file uri can be extracted then this tag is skipped
                    val uri = extractUriFromTags(tags) ?: return@forEach
                    val path = Uri.parse(uri).path
                    requireNotNull(path)
                    // Get the filename
                    val filename = path.substringAfterLast('/')
                    // Get the inline email attachment for the file. If it does not exist then exit
                    val inlineAttachment = inlineAttachmentMap[filename] ?: return@forEach
                    val cid = resolveCidForImage(path, inlineAttachment)
                    cleanHTML = replaceUriForCid(fullTag, cleanHTML, uri, cid)
                }
            }
            return cleanHTML
        }

        /**
         * Extract a URI based on a sequence of regular expression matches
         * on HTML tags.
         */
        private fun extractUriFromTags(tags: Sequence<MatchResult>): String? =
            tags
                .mapNotNull {
                    val tag = it.groups[1]?.value
                    val tagValue = it.groups[2]?.value

                    if (tag == SRC_TAG && tagValue?.startsWith(FILE_TAG) == true) {
                        tagValue
                    } else {
                        null
                    }
                }.firstOrNull()

        /**
         * Replaces a URI in a HTML tag with a Content-ID (cid) and updates the HTML body accordingly.
         */
        private fun replaceUriForCid(
            originalTag: String,
            body: String,
            uri: String,
            cid: String,
        ): String {
            val replacementTag =
                originalTag
                    .replace(STYLE, "")
                    .replace("$SRC_TAG=\"$uri\"", "$SRC_TAG=\"$CID_TAG$cid\" $STYLE")
            return body.replace(originalTag, replacementTag)
        }

        private fun resolveCidForImage(
            path: String,
            inlineAttachment: EmailAttachmentEntity,
        ): String {
            var cid = path.hashCode().toString()
            inlineAttachment.contentId.let { cid = it }
            return cid
        }
    }
}
