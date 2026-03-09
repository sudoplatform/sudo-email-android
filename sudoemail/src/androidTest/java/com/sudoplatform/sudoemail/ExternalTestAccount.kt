/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudologging.Logger
import jakarta.activation.DataHandler
import jakarta.activation.DataSource
import jakarta.mail.Authenticator
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import jakarta.mail.search.AndTerm
import jakarta.mail.search.FlagTerm
import jakarta.mail.search.FromStringTerm
import jakarta.mail.search.SearchTerm
import jakarta.mail.search.SentDateTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.Properties

enum class ExternalTestAccountType {
    GMAIL,
    YAHOO,
}

/**
 * Test-only helper for sending and receiving real emails from external providers via SMTP/IMAP.
 * Note that these accounts are shared resources and may be accessed by multiple test runs in parallel, so tests
 * should be designed to avoid conflicts (e.g. by using unique subjects and/or waiting for emails sent by the test itself).
 */
internal class ExternalTestAccount(
    private val context: Context,
    private val log: Logger,
    accountType: ExternalTestAccountType? = null,
) {
    data class ImapConfig(
        val user: String,
        val password: String,
        val host: String,
        val port: Int,
        val tls: Boolean,
    )

    data class SmtpConfig(
        val host: String,
        val port: Int,
        val secure: Boolean,
        val user: String,
        val password: String,
    )

    data class WaitOptions(
        val timeoutMs: Long = 40_000,
        val searchFromDate: Date? = null,
    )

    data class SendParams(
        val to: List<String>? = null,
        val cc: List<String>? = null,
        val bcc: List<String>? = null,
        val subject: String? = null,
        val body: String? = null,
        val inReplyTo: String? = null,
        val attachments: List<EmailAttachment>? = null,
    )

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

    private fun readConfig(fileName: String): Pair<String, String> {
        val lines =
            context.assets
                .open("external/$fileName")
                .bufferedReader()
                .readLines()
        require(lines.size >= 2) { "Config file external/$fileName must contain username on line 1 and password on line 2" }
        return Pair(lines[0].trim(), lines[1].trim())
    }

    private val imapConfigs: Map<ExternalTestAccountType, ImapConfig> by lazy {
        val (gmailUser, gmailPassword) = readConfig("gmail.config")
        val (yahooUser, yahooPassword) = readConfig("yahoo.config")
        mapOf(
            ExternalTestAccountType.YAHOO to
                ImapConfig(
                    user = yahooUser,
                    password = yahooPassword,
                    host = "imap.mail.yahoo.com",
                    port = 993,
                    tls = true,
                ),
            ExternalTestAccountType.GMAIL to
                ImapConfig(
                    user = gmailUser,
                    password = gmailPassword,
                    host = "imap.gmail.com",
                    port = 993,
                    tls = true,
                ),
        )
    }

    private val smtpConfigs: Map<ExternalTestAccountType, SmtpConfig> by lazy {
        val (gmailUser, gmailPassword) = readConfig("gmail.config")
        val (yahooUser, yahooPassword) = readConfig("yahoo.config")
        mapOf(
            ExternalTestAccountType.YAHOO to
                SmtpConfig(
                    host = "smtp.mail.yahoo.com",
                    port = 465,
                    secure = true,
                    user = yahooUser,
                    password = yahooPassword,
                ),
            ExternalTestAccountType.GMAIL to
                SmtpConfig(
                    host = "smtp.gmail.com",
                    port = 465,
                    secure = true,
                    user = gmailUser,
                    password = gmailPassword,
                ),
        )
    }

    private val accountType: ExternalTestAccountType = accountType ?: randomAccountType()

    private var imapStore: Store? = null
    private var imapSession: Session? = null

    init {
        log.debug("Using external test account type: $accountType")
    }

    fun getEmailAddress(): String = imapConfigs.getValue(accountType).user

    fun getAccountType(): ExternalTestAccountType = accountType

    fun closeConnection() {
        val store = imapStore
        log.debug("Closing IMAP connection ${store?.isConnected}")
        try {
            store?.close()
        } catch (e: Throwable) {
            log.error("Error closing IMAP store $e")
        } finally {
            imapStore = null
            imapSession = null
        }
    }

    fun randomAccountType(): ExternalTestAccountType {
        val values = ExternalTestAccountType.entries
        return values.random()
    }

    /**
     * Waits for an UNSEEN email from [sender] (and optionally [subject]) in INBOX or spam folder.
     */
    suspend fun waitForEmailFromSender(
        sender: String,
        subject: String? = null,
        options: WaitOptions = WaitOptions(),
    ): ReceivedEmail =
        withContext(Dispatchers.IO) {
            log.debug(
                "Waiting for email from sender: $sender with subject: $subject",
            )

            val timeoutAt = System.currentTimeMillis() + options.timeoutMs
            val pollIntervalMs = 5_000L

            ensureImapConnected()

            var lastError: Throwable? = null
            while (System.currentTimeMillis() < timeoutAt) {
                try {
                    val inboxResult =
                        searchBoxForMatch(
                            boxName = "INBOX",
                            sender = sender,
                            subject = subject,
                            searchFromDate = options.searchFromDate,
                        )
                    if (inboxResult != null) {
                        closeConnection()
                        return@withContext inboxResult
                    }

                    val spamFolderName = if (accountType == ExternalTestAccountType.GMAIL) "[Gmail]/Spam" else "Bulk"
                    val spamResult =
                        searchBoxForMatch(
                            boxName = spamFolderName,
                            sender = sender,
                            subject = subject,
                            searchFromDate = options.searchFromDate,
                        )
                    if (spamResult != null) {
                        closeConnection()
                        return@withContext spamResult
                    }
                } catch (t: Throwable) {
                    lastError = t
                    log.error("IMAP poll error $t")
                    // reconnect next loop
                    closeConnection()
                    ensureImapConnected()
                }

                delay(pollIntervalMs)
            }

            closeConnection()
            throw IllegalStateException("Timed out waiting for email from sender: $sender", lastError)
        }

    suspend fun sendMessage(params: SendParams): String =
        withContext(Dispatchers.IO) {
            val cfg = smtpConfigs.getValue(accountType)

            val props =
                Properties().apply {
                    put("mail.transport.protocol", "smtp")
                    put("mail.smtp.host", cfg.host)
                    put("mail.smtp.port", cfg.port.toString())
                    put("mail.smtp.auth", "true")
                    if (cfg.secure) {
                        // Implicit TLS (SMTPS)
                        put("mail.smtp.ssl.enable", "true")
                    } else {
                        put("mail.smtp.starttls.enable", "true")
                    }
                }

            val session =
                Session.getInstance(
                    props,
                    object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication = PasswordAuthentication(cfg.user, cfg.password)
                    },
                )

            val message =
                MimeMessage(session).apply {
                    setFrom(InternetAddress(getEmailAddress()))

                    params.to?.takeIf { it.isNotEmpty() }?.let { setRecipients(Message.RecipientType.TO, it.joinToString(",")) }
                    params.cc?.takeIf { it.isNotEmpty() }?.let { setRecipients(Message.RecipientType.CC, it.joinToString(",")) }
                    params.bcc?.takeIf { it.isNotEmpty() }?.let { setRecipients(Message.RecipientType.BCC, it.joinToString(",")) }

                    subject = params.subject ?: "Test Email Subject"
                    params.inReplyTo?.let { setHeader("In-Reply-To", it) }

                    val bodyText = params.body ?: "This is a test email body."
                    val attachments = params.attachments.orEmpty()

                    if (attachments.isEmpty()) {
                        setText(bodyText)
                    } else {
                        val multipart = MimeMultipart()

                        val textPart = MimeBodyPart().apply { setText(bodyText) }
                        multipart.addBodyPart(textPart)

                        attachments.forEach { att ->
                            val part =
                                MimeBodyPart().apply {
                                    fileName = att.fileName
                                    setHeader("Content-Type", att.mimeType)
                                    dataHandler =
                                        DataHandler(
                                            object : DataSource {
                                                override fun getInputStream() = ByteArrayInputStream(att.data)

                                                override fun getOutputStream() = throw UnsupportedOperationException("read-only")

                                                override fun getContentType() = att.mimeType

                                                override fun getName() = att.fileName
                                            },
                                        )
                                }
                            multipart.addBodyPart(part)
                        }

                        setContent(multipart)
                    }

                    sentDate = Date()
                }

            try {
                Transport.send(message)
            } catch (e: MessagingException) {
                log.error("Error sending email $e")
                throw e
            }

            // message-id is normally generated during send; try to return it if available.
            message.messageID ?: message.getHeader("Message-ID")?.firstOrNull() ?: ""
        }

    private fun ensureImapConnected() {
        val cfg = imapConfigs.getValue(accountType)
        val existing = imapStore
        if (existing != null && existing.isConnected) return

        val props =
            Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", cfg.host)
                put("mail.imaps.port", cfg.port.toString())
                put("mail.imaps.ssl.enable", cfg.tls.toString())
                put("mail.imaps.ssl.checkserveridentity", "true")
                // reasonable defaults for tests
                put("mail.imaps.connectiontimeout", "10000")
                put("mail.imaps.timeout", "10000")
            }

        val session =
            Session.getInstance(
                props,
                object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication = PasswordAuthentication(cfg.user, cfg.password)
                },
            )

        val store = session.getStore("imaps")
        store.connect(cfg.host, cfg.port, cfg.user, cfg.password)

        imapSession = session
        imapStore = store
    }

    private fun searchBoxForMatch(
        boxName: String,
        sender: String,
        subject: String?,
        searchFromDate: Date?,
    ): ReceivedEmail? {
        val store = imapStore ?: throw IllegalStateException("IMAP store not connected")

        log.debug(
            "Searching box: $boxName for sender: $sender since: $searchFromDate",
        )

        var folder: Folder? = null
        try {
            folder = store.getFolder(boxName)
            if (!folder.exists()) {
                log.debug("Folder does not exist: $boxName")
                return null
            }
            folder.open(Folder.READ_ONLY)

            val terms = mutableListOf<SearchTerm>()
            terms += FlagTerm(Flags(Flags.Flag.SEEN), false)
            terms += FromStringTerm(sender)
            // NOTE:
            // Some servers/folders (notably Yahoo Bulk, and occasionally others) fail subject searches that contain
            // non-ASCII characters (e.g. emoji) and either throw or return empty results. To keep this stable,
            // we do subject matching client-side below.
            //
            // If you need server-side narrowing for ASCII-only subjects, it can be reintroduced with a guard,
            // but stability is more important for these external tests.
            if (searchFromDate != null) {
                // IMAP "SENTSINCE" is day-granular; add a strict filter below too.
                terms += SentDateTerm(SentDateTerm.GE, searchFromDate)
            }

            val term = if (terms.size == 1) terms.first() else AndTerm(terms.toTypedArray())
            log.debug("Searching with term: ${terms.forEach { it.toString() }}")
            val results = folder.search(term)
            if (results.isNullOrEmpty()) {
                log.debug("No matching emails found in box $boxName")
                return null
            }

            val expectedSubject = subject?.let { normalizeSubjectForCompare(it) }

            // Iterate and pick first match that passes strict datetime + subject filters.
            for (msg in results) {
                val mimeMessage = msg as MimeMessage
                val msgDate = mimeMessage.sentDate ?: mimeMessage.receivedDate
                if (searchFromDate != null && msgDate != null && msgDate.before(searchFromDate)) {
                    log.debug(
                        "Skipping email before searchFromDate: $msgDate\nsubject: ${mimeMessage.subject}",
                    )
                    continue
                }

                if (expectedSubject != null) {
                    val actualSubject = normalizeSubjectForCompare(decodeSubjectSafely(mimeMessage.subject))
                    if (actualSubject != expectedSubject) {
                        log.debug(
                            "Skipping email with non-matching subject: ${mimeMessage.subject}\nexpected: $subject",
                        )
                        continue
                    }
                }

                val fromList: List<EmailMessage.EmailAddress> =
                    mimeMessage.from
                        ?.mapNotNull { addr ->
                            val internet = addr as? InternetAddress
                            val email = internet?.address ?: addr.toString()
                            if (email.isBlank()) return@mapNotNull null
                            EmailMessage.EmailAddress(
                                emailAddress = email,
                                displayName = internet?.personal,
                            )
                        }.orEmpty()

                val toList: List<EmailMessage.EmailAddress> =
                    (mimeMessage.getRecipients(Message.RecipientType.TO) ?: emptyArray())
                        .mapNotNull { addr ->
                            val internet = addr as? InternetAddress
                            val email = internet?.address ?: addr.toString()
                            if (email.isBlank()) return@mapNotNull null
                            EmailMessage.EmailAddress(
                                emailAddress = email,
                                displayName = internet?.personal,
                            )
                        }

                val ccList: List<EmailMessage.EmailAddress> =
                    (mimeMessage.getRecipients(Message.RecipientType.CC) ?: emptyArray())
                        .mapNotNull { addr ->
                            val internet = addr as? InternetAddress
                            val email = internet?.address ?: addr.toString()
                            if (email.isBlank()) return@mapNotNull null
                            EmailMessage.EmailAddress(
                                emailAddress = email,
                                displayName = internet?.personal,
                            )
                        }

                return ReceivedEmail(
                    subject = mimeMessage.subject,
                    from = fromList,
                    to = toList,
                    cc = ccList,
                    date = msgDate,
                    messageId = mimeMessage.getHeader("Message-ID")?.firstOrNull(),
                    textBody = extractTextBody(mimeMessage),
                    attachments = extractAttachments(mimeMessage),
                    rawMessage = mimeMessage,
                )
            }

            return null
        } catch (e: Throwable) {
            log.error("Error searching box $boxName $e")
            throw e
        } finally {
            try {
                folder?.close(false)
            } catch (_: Throwable) {
            }
        }
    }

    private fun extractAttachments(message: MimeMessage): List<ReceivedEmailAttachment> =
        try {
            val out = mutableListOf<ReceivedEmailAttachment>()
            collectAttachmentsFromPart(message, out)
            out
        } catch (e: Throwable) {
            log.debug("Failed to extract attachments: $e")
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

    private fun extractTextBody(message: MimeMessage): String? {
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

    private fun decodeSubjectSafely(subject: String?): String? {
        if (subject == null) return null
        return try {
            // Handles RFC2047 encoded-words; if it's already unicode, this is a no-op.
            MimeUtility.decodeText(subject)
        } catch (_: Throwable) {
            subject
        }
    }

    private fun normalizeSubjectForCompare(subject: String?): String? {
        if (subject == null) return null
        // Normalize common formatting differences without being too clever.
        return subject.trim().replace("\r\n", "\n")
    }
}
