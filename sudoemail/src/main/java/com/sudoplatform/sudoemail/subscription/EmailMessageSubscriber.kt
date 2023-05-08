/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
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
     * Notifies the subscriber of a modified [EmailMessage].
     *
     * @param emailMessage [EmailMessage] The modified email message.
     */
    fun emailMessageChanged(emailMessage: EmailMessage)
}
