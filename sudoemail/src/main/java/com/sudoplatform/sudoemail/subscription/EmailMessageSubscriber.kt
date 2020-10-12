/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.subscription

import com.sudoplatform.sudoemail.types.EmailMessage

/**
 * Subscriber for receiving notifications about new and deleted [EmailMessage]s.
 *
 * @since 2020-08-19
 */
interface EmailMessageSubscriber {

    /**
     * Connection state of the subscription.
     */
    enum class ConnectionState {

        /**
         * Connected and receiving updates.
         */
        CONNECTED,

        /**
         * Disconnected and won't receive any updates. When disconnected all subscribers will be
         * unsubscribed so the consumer must re-subscribe.
         */
        DISCONNECTED
    }

    /**
     * Notifies the subscriber of a new [EmailMessage].
     *
     * @param emailMessage new [EmailMessage].
     */
    fun emailMessageCreated(emailMessage: EmailMessage)

    /**
     * Notifies the subscriber of a deleted [EmailMessage].
     *
     * @param emailMessage deleted [EmailMessage].
     */
    fun emailMessageDeleted(emailMessage: EmailMessage)

    /**
     * Notifies the subscriber that the subscription connection state has changed. The subscriber won't be
     * notified of [EmailMessage] changes until the connection status changes to [ConnectionState.CONNECTED]. The subscriber will
     * stop receiving [EmailMessage] change notifications when the connection state changes to [ConnectionState.DISCONNECTED].
     *
     * @param state connection state.
     */
    fun connectionStatusChanged(state: ConnectionState)
}
