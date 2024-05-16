/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import android.content.Context
import android.text.SpannableString
import android.text.TextUtils
import androidx.core.text.HtmlCompat
import com.sudoplatform.sudoemail.R
import com.sudoplatform.sudoemail.types.EmailAttachment
import jakarta.mail.Message
import jakarta.mail.Part
import jakarta.mail.internet.MimeMessage
import java.lang.Exception

/**
 * An object containing the email body and a flag indicating whether it is
 * formatted as HTML.
 *
 * @property body [String] The email text body.
 * @property isHtml Flag indicating whether the [body] is formatted as HTML.
 */
internal data class ViewableBody(
    val body: String,
    val isHtml: Boolean,
)

/**
 * An interface defining the email body [Part] and convenience functions for transforming
 * between plain text and HTML when building the email body.
 *
 * @see ViewableBodyPart
 * @see ViewableMessageHeader
 */
internal interface ViewablePart {
    val part: Part

    /**
     * Returns the [part] as a plain text representation.
     */
    fun asPlainText(context: Context): String?

    /**
     * Returns the [part] as a HTML text representation.
     */
    fun asHtmlText(context: Context): String?
}

/**
 * Contains the email body [Part] with the MIME type `text/...`.
 *
 * @see ViewablePart.asPlainText
 * @see ViewablePart.asHtmlText
 */
internal data class ViewableBodyPart(
    override val part: Part,
) : ViewablePart {

    override fun asPlainText(context: Context): String? {
        return when {
            part.isMimeType(MimeTypes.TEXT_ANY) && part.content is String -> part.content as String
            else -> {
                null
            }
        }
    }

    override fun asHtmlText(context: Context): String? {
        return when {
            part.isMimeType(MimeTypes.TEXT_HTML) -> {
                try {
                    part.content as String
                } catch (e: Exception) {
                    when (part) {
                        is MimeMessage -> part.rawInputStream.readBytes().toString(Charsets.UTF_8)
                        else -> {
                            null
                        }
                    }
                }
            }
            part.isMimeType(MimeTypes.TEXT_ANY) -> {
                val encodedText = TextUtils.htmlEncode(part.content as String)
                HtmlCompat.toHtml(
                    SpannableString(encodedText),
                    HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE,
                )
            }
            else -> null
        }
    }
}

/**
 * Contains the email message header information commonly displayed when an email
 * message is forwarded or replied to.
 *
 * @see ViewablePart.asPlainText
 * @see ViewablePart.asHtmlText
 */
internal data class ViewableMessageHeader(
    val message: Message,
) : ViewablePart {
    override val part: Part
        get() = message

    val from = message.from?.joinToString().orEmpty()
    val to = message.getRecipients(Message.RecipientType.TO)?.joinToString().orEmpty()
    val sent = message.sentDate.toString()
    val subject = message.subject.orEmpty()

    override fun asPlainText(context: Context): String {
        return context.getString(
            R.string.plain_text_email_message_header,
            from,
            sent,
            to,
            subject,
        )
    }

    override fun asHtmlText(context: Context): String {
        return context.getString(
            R.string.html_text_email_message_header,
            from,
            sent,
            to,
            subject,
        )
    }
}

/**
 * A container object consisting of the collected parts and attachments.
 *
 * @property parts [List<ViewablePart>] A list of collected [ViewablePart]s.
 * @property attachments [List<EmailAttachment>] A list of collected [EmailAttachment]s.
 */
internal data class CollectedParts(
    val parts: List<ViewablePart>,
    val attachments: List<EmailAttachment>,
)
