/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.configuration

import android.os.Parcelable
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailAttachmentEntity
import kotlinx.parcelize.Parcelize
import java.io.File

/**
 * Core entity representation of the email service configuration data used in the Sudo Platform Email SDK.
 *
 * @property deleteEmailMessagesLimit [Int] The number of email messages that can be deleted at a time.
 * @property updateEmailMessagesLimit [Int] The number of email messages that can be updated at a time.
 * @property emailMessageMaxInboundMessageSize [Int] The maximum allowed size of an inbound email message.
 * @property emailMessageMaxOutboundMessageSize [Int] The maximum allowed size of an outbound email message.
 * @property emailMessageRecipientsLimit [Int] The maximum number of recipients for an out-of-network email message.
 * @property encryptedEmailMessageRecipientsLimit [Int] The maximum number of recipients for an in-network encrypted
 *  email message.
 * @property prohibitedFileExtensions [List] The set of file extensions [String] which are not permitted to be sent
 *  as attachments.
 */
@Parcelize
internal data class ConfigurationDataEntity(
    val deleteEmailMessagesLimit: Int,
    val updateEmailMessagesLimit: Int,
    val emailMessageMaxInboundMessageSize: Int,
    val emailMessageMaxOutboundMessageSize: Int,
    val emailMessageRecipientsLimit: Int,
    val encryptedEmailMessageRecipientsLimit: Int,
    val prohibitedFileExtensions: List<String>,
) : Parcelable {
    fun verifyAttachmentValidity(
        attachments: List<EmailAttachmentEntity>,
        inlineAttachments: List<EmailAttachmentEntity>,
    ) {
        attachments
            .plus(inlineAttachments)
            .map {
                File(it.fileName).extension
            }.forEach {
                if (prohibitedFileExtensions.contains(".$it")) {
                    throw SudoEmailClient.EmailMessageException.InvalidMessageContentException("Extension not supported")
                }
            }
    }
}
