/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

internal data class SimplifiedEmailMessage(
    val from: List<String>,
    val to: List<String>,
    val cc: List<String>,
    val bcc: List<String>,
    val subject: String,
    val body: String
)

/**
 * A parser of RFC 822 compatible email message content. Note that parsing is also compatible with the more recent RFC 6854
 * standard which supersedes RFC 822.
 *
 * @since 2020-08-18
 */
internal object Rfc822MessageParser {

    private val session = Session.getDefaultInstance(System.getProperties(), null)

    private class LocalMimeMessage(session: Session, rfc822Ins: InputStream) : MimeMessage(session, rfc822Ins) {

        override fun toString(): String {

            val sender = from?.map { it.toString() } ?: emptyList()
            val toRecipients = getRecipients(Message.RecipientType.TO)?.map { it.toString() } ?: emptyList()
            val ccRecipients = getRecipients(Message.RecipientType.CC)?.map { it.toString() } ?: emptyList()
            val bccRecipients = getRecipients(Message.RecipientType.BCC)?.map { it.toString() } ?: emptyList()

            return buildString {
                appendln("MimeMessage [")
                appendln("  From: $sender")
                appendln("  To: $toRecipients")
                appendln("  Cc: $ccRecipients")
                appendln("  Bcc: $bccRecipients")
                appendln("  Subject: $subject")
                appendln("  Received: $receivedDate")
                appendln("  ContentType: $contentType")
                appendln("  Description: $description")
                appendln("  Encoding: $encoding")
                appendln("  Size: $size")
                val multipart = MimeMultipart(dataHandler.dataSource)
                appendln("  Preamble: ${multipart.preamble}")
                for (i in 0 until multipart.count) {
                    val bodyPart = multipart.getBodyPart(i)
                    appendln("  Body Part $i [")
                    appendln("    ContentType: ${bodyPart.contentType}")
                    appendln("    Description: ${bodyPart.description}")
                    appendln("    Size: ${bodyPart.size}")
                    if (bodyPart.contentType == "text/plain") {
                        appendln("    Content: ${bodyPart.content}")
                    }
                    appendln("  ]")
                }
                appendln("]")
            }
        }
    }

    /**
     * Parse an RFC 822 compatible mail message.
     */
    @Throws(MessagingException::class, IOException::class)
    fun parseRfc822Data(rfc822Data: ByteArray): SimplifiedEmailMessage {
        val rfc822Ins = ByteArrayInputStream(rfc822Data)
        val javaMailMessage = LocalMimeMessage(
            session,
            rfc822Ins
        )
        return toSimplifiedEmailMessage(javaMailMessage)
    }

    @Throws(MessagingException::class, IOException::class)
    private fun toSimplifiedEmailMessage(message: Message): SimplifiedEmailMessage {

        val sender = message.from?.map { it.toString() } ?: emptyList()
        val toRecipients = message.getRecipients(Message.RecipientType.TO)?.map { it.toString() } ?: emptyList()
        val ccRecipients = message.getRecipients(Message.RecipientType.CC)?.map { it.toString() } ?: emptyList()
        val bccRecipients = message.getRecipients(Message.RecipientType.BCC)?.map { it.toString() } ?: emptyList()

        val multipart = MimeMultipart(message.dataHandler.dataSource)
        val body = buildString {
            for (i in 0 until multipart.count) {
                val bodyPart = multipart.getBodyPart(i)
                if (bodyPart.contentType == "text/plain") {
                    append(bodyPart.content)
                }
            }
        }

        return SimplifiedEmailMessage(
            from = sender,
            to = toRecipients,
            cc = ccRecipients,
            bcc = bccRecipients,
            subject = message.subject ?: "<No subject>",
            body = body
        )
    }
}
