/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.types.EmailMessageDateRange
import com.sudoplatform.sudoemail.types.SortOrder

/**
 * Input object containing properties used to list all email messages for a user.
 *
 * @property dateRange [EmailMessageDateRange] Email messages matching the specified date range inclusive will be fetched.
 * @property limit [Int] Number of email messages to return. If omitted the limit defaults to 10.
 * @property nextToken [String] A token generated from previous calls to [SudoEmailClient.listEmailMessages].
 *  This is to allow for pagination. This value should be generated from a previous
 *  pagination call, otherwise it will throw an exception. The same arguments should be
 *  supplied to this method if using a previously generated [nextToken].
 * @property sortOrder [SortOrder] The direction in which the email messages are sorted. Defaults to descending.
 * @property includeDeletedMessages [Boolean] Indicates whether or not to include deleted messages. Defaults to false.
 */
data class ListEmailMessagesInput(
    val dateRange: EmailMessageDateRange? = null,
    val limit: Int? = SudoEmailClient.DEFAULT_EMAIL_MESSAGE_LIMIT,
    val nextToken: String? = null,
    val sortOrder: SortOrder = SortOrder.DESC,
    val includeDeletedMessages: Boolean = false,
)
