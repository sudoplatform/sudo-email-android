/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudologging.Logger
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Properties

/**
 * Test account that reads sent emails from the S3 mail transporter bucket
 * instead of polling an external IMAP mailbox. Objects are stored under
 * `{identityId}/{emailMessageId}.eml` and the bucket is a UserReadOnlyBucket,
 * so the caller must use Cognito-authenticated S3 credentials.
 */
class S3TestAccount(
    private val s3: S3Client,
    private val identityId: String,
    private val logger: Logger,
) : TestAccount {
    /**
     * Options for [waitForEmail].
     *
     * @property timeoutMs Maximum time to wait in milliseconds. Defaults to 40000.
     * @property searchFromDate If set, objects last modified before this date are skipped.
     * @property pollIntervalMs How long to wait between polling cycles. Defaults to 2000.
     */
    data class WaitForEmailOptions(
        val timeoutMs: Long = 40_000L,
        val searchFromDate: Date? = null,
        val pollIntervalMs: Long = 2_000L,
    )

    /**
     * Polls the S3 bucket for an email, optionally matching a [sender] and/or [subject].
     * Returns the parsed [MimeMessage] when found.
     *
     * @param sender Optional sender address to match (case-insensitive). Checked against both
     *   the RFC822 From header and the S3 object metadata `sender` field.
     * @param subject Optional subject line to match exactly.
     * @param options Additional polling options.
     * @return The first matching [MimeMessage].
     * @throws Exception if no matching email is found before the timeout.
     */
    suspend fun waitForEmail(
        sender: String? = null,
        subject: String? = null,
        options: WaitForEmailOptions = WaitForEmailOptions(),
    ): TestAccount.ReceivedEmail {
        val deadline = System.currentTimeMillis() + options.timeoutMs
        val checkedKeys = mutableSetOf<String>()
        val session = Session.getDefaultInstance(Properties())

        while (System.currentTimeMillis() < deadline) {
            val listed = s3.list(prefix = "$identityId/", limit = 100, options = S3Client.KeyOptions(isKeyCredentialled = true))
            for (obj in listed.items) {
                if (checkedKeys.contains(obj.key)) continue
                checkedKeys.add(obj.key)

                // Skip objects older than searchFromDate if specified
                if (options.searchFromDate != null && obj.lastModified < options.searchFromDate) continue

                val data = s3.download(key = obj.key, options = S3Client.KeyOptions(isKeyCredentialled = true))
                val message = MimeMessage(session, data.inputStream())

                // Match sender against the RFC822 From header (which may be rewritten
                // by the mask pipeline) or the S3 metadata sender field
                if (sender != null) {
                    val senderLower = sender.lowercase()
                    val fromAddresses =
                        (message.from ?: emptyArray())
                            .filterIsInstance<InternetAddress>()
                            .mapNotNull { it.address?.lowercase() }
                    val metadata = s3.getObjectMetadata(key = obj.key, options = S3Client.KeyOptions(isKeyCredentialled = true))
                    val metaSender = metadata.userMetadata?.get("sender")?.lowercase()
                    if (!fromAddresses.contains(senderLower) && metaSender != senderLower) continue
                }
                if (subject == null || message.subject == subject) {
                    logger.debug("Found matching email in S3: key=${obj.key}, sender=$sender, subject=${message.subject}")
                    return TestAccount.ReceivedEmail(
                        subject = message.subject,
                        from = buildFromList(message.from),
                        to = buildRecipientList(message.getRecipients(Message.RecipientType.TO) ?: emptyArray()),
                        cc = buildRecipientList(message.getRecipients(Message.RecipientType.CC) ?: emptyArray()),
                        date = message.sentDate ?: message.receivedDate,
                        messageId = message.getHeader("Message-ID")?.firstOrNull(),
                        textBody = extractTextBody(message),
                        attachments = extractAttachments(message),
                        rawMessage = message,
                    )
                }
            }

            delay(options.pollIntervalMs)
        }

        throw Exception(
            "Timeout waiting for email from $sender" +
                (if (subject != null) " with subject \"$subject\"" else "") +
                " in S3 bucket ${s3.bucket}",
        )
    }
}
