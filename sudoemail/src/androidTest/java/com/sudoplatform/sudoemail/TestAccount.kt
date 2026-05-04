/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.types.EmailMessage
import jakarta.mail.Address
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.io.ByteArrayOutputStream
import java.util.Date

interface TestAccount {
    data class ReceivedEmailAttachment(
        val fileName: String?,
        val mimeType: String?,
        val data: ByteArray,
    )

    data class ReceivedEmail(
        val subject: String?,
        val from: List<EmailMessage.EmailAddress>,
        val to: List<EmailMessage.EmailAddress>,
        val cc: List<EmailMessage.EmailAddress>,
        val date: Date?,
        val messageId: String?,
        val textBody: String?,
        val attachments: List<ReceivedEmailAttachment>,
        val rawMessage: MimeMessage,
    )

    fun extractTextBody(message: MimeMessage): String? {
        return try {
            when (val content = message.content) {
                is String -> content
                is Multipart -> {
                    for (i in 0 until content.count) {
                        val part = content.getBodyPart(i)
                        if (part.isMimeType("text/plain")) {
                            return part.content as? String
                        }
                    }
                    null
                }
                else -> null
            }
        } catch (e: Throwable) {
            null
        }
    }

    fun extractAttachments(message: MimeMessage): List<ReceivedEmailAttachment> =
        try {
            val out = mutableListOf<ReceivedEmailAttachment>()
            collectAttachmentsFromPart(message, out)
            out
        } catch (e: Throwable) {
            emptyList()
        }

    private fun collectAttachmentsFromPart(
        part: Part,
        out: MutableList<ReceivedEmailAttachment>,
    ) {
        when (val content = part.content) {
            is Multipart -> {
                for (i in 0 until content.count) {
                    val bodyPart = content.getBodyPart(i)

                    // If it's a nested multipart (e.g. multipart/alternative inside multipart/mixed)
                    // recurse before/after attachment checks.
                    if (bodyPart.isMimeType("multipart/*")) {
                        collectAttachmentsFromPart(bodyPart, out)
                        continue
                    }

                    val disposition = bodyPart.disposition
                    val isAttachmentDisposition = disposition != null && disposition.equals(Part.ATTACHMENT, ignoreCase = true)
                    val hasFileName = !bodyPart.fileName.isNullOrBlank()

                    // Some providers don’t set disposition=ATTACHMENT but do set filename.
                    if (isAttachmentDisposition || hasFileName) {
                        val bytes = readAllBytes(bodyPart)
                        out +=
                            ReceivedEmailAttachment(
                                fileName = bodyPart.fileName,
                                mimeType = bodyPart.contentType?.substringBefore(';')?.trim(),
                                data = bytes,
                            )
                    }
                }
            }
            else -> {
                // leaf, no-op
            }
        }
    }

    private fun readAllBytes(part: Part): ByteArray {
        val input = part.inputStream
        return input.use {
            val buffer = ByteArrayOutputStream()
            it.copyTo(buffer)
            buffer.toByteArray()
        }
    }

    fun buildFromList(from: Array<Address>?): List<EmailMessage.EmailAddress> =
        from
            ?.mapNotNull { addr ->
                val internet = addr as? InternetAddress
                val email = internet?.address ?: addr.toString()
                if (email.isBlank()) return@mapNotNull null
                EmailMessage.EmailAddress(
                    emailAddress = email,
                    displayName = internet?.personal,
                )
            }.orEmpty()

    fun buildRecipientList(recip: Array<Address>): List<EmailMessage.EmailAddress> =
        recip.mapNotNull { addr ->
            val internet = addr as? InternetAddress
            val email = internet?.address ?: addr.toString()
            if (email.isBlank()) return@mapNotNull null
            EmailMessage.EmailAddress(
                emailAddress = email,
                displayName = internet?.personal,
            )
        }
}
