/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.types.EmailMessageReceivedNotification

interface SudoEmailNotificationHandler {
    fun onEmailMessageReceived(message: EmailMessageReceivedNotification) {}
}
