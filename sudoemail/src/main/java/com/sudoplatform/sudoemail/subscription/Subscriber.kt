/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.subscription

/**
 * Generic Subscriber interface which allows notification of connection state change.
 */
interface Subscriber {

    /**
     * Connection state of the subscription.
     *
     * @enum ConnectionState
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
        DISCONNECTED,
    }

    /**
     * Notifies the subscriber that the subscription connection state has changed. The subscriber won't be
     * notified of subscribed changes until the connection status changes to [ConnectionState.CONNECTED]. The subscriber will
     * stop receiving subscribed change notifications when the connection state changes to [ConnectionState.DISCONNECTED].
     *
     * @param state [ConnectionState] connection state.
     */
    fun connectionStatusChanged(state: ConnectionState)
}
