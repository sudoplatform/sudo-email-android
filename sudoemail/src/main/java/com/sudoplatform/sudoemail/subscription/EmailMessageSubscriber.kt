/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.subscription

import com.sudoplatform.sudoemail.types.EmailMessage

/**
 * Subscriber for receiving notifications about changes to [EmailMessage]s.
 */
interface EmailMessageSubscriber : Subscriber {
    /**
     * Type of change the message has undergone
     *
     * @enum ChangeType
     */
    enum class ChangeType {
        /**
         * The message was created
         */
        CREATED,

        /**
         * The message was updated
         */
        UPDATED,

        /**
         * The message was deleted
         */
        DELETED,
    }

    /**
     * Notifies the subscriber of a modified [EmailMessage].
     *
     * @param emailMessage [EmailMessage] The modified email message.
     * @param type [ChangeType] The type of change the message has undergone.
     */
    fun emailMessageChanged(
        emailMessage: EmailMessage,
        type: ChangeType,
    )
}
