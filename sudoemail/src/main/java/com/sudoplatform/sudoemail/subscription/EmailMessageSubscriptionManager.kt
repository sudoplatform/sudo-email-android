/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.subscription

import com.sudoplatform.sudoemail.types.EmailMessage

/**
 * Manages subscriptions for a specific GraphQL EmailMessage subscription.
 */
internal class EmailMessageSubscriptionManager<T> : SubscriptionManager<T, EmailMessageSubscriber>() {
    internal fun emailMessageChanged(
        emailMessage: EmailMessage,
        type: EmailMessageSubscriber.ChangeType,
    ) {
        var subscribersToNotify: ArrayList<EmailMessageSubscriber>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            subscribersToNotify = ArrayList(this.getSubscribers().values)
        }

        // Notify subscribers.
        for (subscriber in subscribersToNotify) {
            subscriber.emailMessageChanged(emailMessage, type)
        }
    }
}
