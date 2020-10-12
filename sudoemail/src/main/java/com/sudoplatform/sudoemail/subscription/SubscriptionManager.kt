/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.subscription

import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall
import com.sudoplatform.sudoemail.types.EmailMessage

/**
 * Manages subscriptions for a specific GraphQL subscription.
 */
internal class SubscriptionManager<T> {

    /**
     * Subscribers.
     */
    private val subscribers: MutableMap<String, EmailMessageSubscriber> = mutableMapOf()

    /**
     * AppSync subscription watcher.
     */
    internal var watcher: AppSyncSubscriptionCall<T>? = null

    /**
     * Adds or replaces a subscriber with the specified ID.
     *
     * @param id subscriber ID.
     * @param subscriber subscriber to subscribe.
     */
    internal fun replaceSubscriber(id: String, subscriber: EmailMessageSubscriber) {
        synchronized(this) {
            subscribers[id] = subscriber
        }
    }

    /**
     * Removes the subscriber with the specified ID.
     *
     * @param id subscriber ID.
     */
    internal fun removeSubscriber(id: String) {
        synchronized(this) {
            subscribers.remove(id)

            if (subscribers.isEmpty()) {
                watcher?.cancel()
                watcher = null
            }
        }
    }

    /**
     * Removes all subscribers.
     */
    internal fun removeAllSubscribers() {
        synchronized(this) {
            subscribers.clear()
            watcher?.cancel()
            watcher = null
        }
    }

    /**
     * Notifies subscribers of a new [EmailMessage].
     *
     * @param emailMessage new [EmailMessage].
     */
    internal fun emailMessageCreated(emailMessage: EmailMessage) {
        var subscribersToNotify: ArrayList<EmailMessageSubscriber>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            subscribersToNotify = ArrayList(subscribers.values)
        }

        // Notify subscribers.
        for (subscriber in subscribersToNotify) {
            subscriber.emailMessageCreated(emailMessage)
        }
    }

    /**
     * Notifies subscribers of a deleted [EmailMessage].
     *
     * @param emailMessage deleted [EmailMessage].
     */
    internal fun emailMessageDeleted(emailMessage: EmailMessage) {
        var subscribersToNotify: ArrayList<EmailMessageSubscriber>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            subscribersToNotify = ArrayList(subscribers.values)
        }

        // Notify subscribers.
        for (subscriber in subscribersToNotify) {
            subscriber.emailMessageDeleted(emailMessage)
        }
    }

    /**
     * Processes AppSync subscription connection status change.
     *
     * @param state connection state.
     */
    internal fun connectionStatusChanged(state: EmailMessageSubscriber.ConnectionState) {
        var subscribersToNotify: ArrayList<EmailMessageSubscriber>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            subscribersToNotify = ArrayList(subscribers.values)

            // If the subscription was disconnected then remove all subscribers.
            if (state == EmailMessageSubscriber.ConnectionState.DISCONNECTED) {
                subscribers.clear()
                if (watcher?.isCanceled == false) {
                    watcher?.cancel()
                }
                watcher = null
            }
        }

        // Notify subscribers.
        for (subscriber in subscribersToNotify) {
            subscriber.connectionStatusChanged(state)
        }
    }
}
