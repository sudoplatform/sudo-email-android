/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.notifications.MessageReceivedNotification
import com.sudoplatform.sudoemail.types.EmailMessageReceivedNotification
import java.util.Date

internal object EmailMessageReceivedNotificationTransformer {
    fun toEntity(notification: MessageReceivedNotification): EmailMessageReceivedNotification {
        return EmailMessageReceivedNotification(
            id = notification.messageId,
            owner = notification.owner,
            emailAddressId = notification.emailAddressId,
            sudoId = notification.sudoId,
            folderId = notification.folderId,
            from = notification.from,
            replyTo = notification.replyTo,
            subject = notification.subject,
            sentAt = Date(notification.sentAtEpochMs),
            receivedAt = Date(notification.receivedAtEpochMs),
            encryptionStatus = notification.encryptionStatus,
            hasAttachments = notification.hasAttachments,
        )
    }
}
