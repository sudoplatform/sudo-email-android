/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Representation of the email service configuration data used in the Sudo Platform Email SDK.
 *
 * @property deleteEmailMessagesLimit [Int] The number of email messages that can be deleted at a time.
 * @property updateEmailMessagesLimit [Int] The number of email messages that can be updated at a time.
 * @property emailMessageMaxInboundMessageSize [Int] The maximum allowed size of an inbound email message.
 * @property emailMessageMaxOutboundMessageSize [Int] The maximum allowed size of an outbound email message.
 */
@Parcelize
data class ConfigurationData(
    val deleteEmailMessagesLimit: Int,
    val updateEmailMessagesLimit: Int,
    val emailMessageMaxInboundMessageSize: Int,
    val emailMessageMaxOutboundMessageSize: Int
) : Parcelable
