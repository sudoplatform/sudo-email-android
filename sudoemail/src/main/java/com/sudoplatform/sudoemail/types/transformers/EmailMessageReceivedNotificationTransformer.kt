/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.notifications.MessageReceivedNotification
import com.sudoplatform.sudoemail.types.EmailMessageReceivedNotification
import java.util.Date

/**
 * Transformer responsible for transforming the [MessageReceivedNotification] data
 * to the [EmailMessageReceivedNotification] entity type that is exposed to users.
 */
internal object EmailMessageReceivedNotificationTransformer {
    /**
     * Transform the [MessageReceivedNotification] type to its [EmailMessageReceivedNotification]
     *  entity type.
     *
     * @param notification [MessageReceivedNotification] The message received notification type.
     * @return The [EmailMessageReceivedNotification] entity type.
     */
    fun toEntity(notification: MessageReceivedNotification): EmailMessageReceivedNotification =
        EmailMessageReceivedNotification(
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
