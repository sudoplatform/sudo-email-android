/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.EmailMessageDateRange
import com.sudoplatform.sudoemail.types.SortOrder

/**
 * Input object containing an email address identifier and other properties used to list email messages
 * for an email address.
 *
 * @property emailAddressId [String] The identifier of the email address associated with the email messages.
 * @property dateRange [EmailMessageDateRange] Email messages matching the specified date range inclusive will be fetched.
 * @property cachePolicy [CachePolicy] Determines how the data will be fetched. When using [CachePolicy.CACHE_ONLY],
 *  be aware that this will only return cached results of identical API calls.
 * @property limit [Int] Number of email messages to return. If omitted the limit defaults to 10.
 * @property nextToken [String] A token generated from previous calls to [SudoEmailClient.listEmailMessagesForEmailAddressId].
 *  This is to allow for pagination. This value should be generated from a previous
 *  pagination call, otherwise it will throw an exception. The same arguments should be
 *  supplied to this method if using a previously generated [nextToken].
 * @property sortOrder [SortOrder] The direction in which the email messages are sorted. Defaults to descending.
 * @property includeDeletedMessages [Boolean] Indicates whether or not to include deleted messages. Defaults to false.
 */
data class ListEmailMessagesForEmailAddressIdInput(
    val emailAddressId: String,
    val dateRange: EmailMessageDateRange? = null,
    val cachePolicy: CachePolicy = CachePolicy.REMOTE_ONLY,
    val limit: Int? = SudoEmailClient.DEFAULT_EMAIL_MESSAGE_LIMIT,
    val nextToken: String? = null,
    val sortOrder: SortOrder = SortOrder.DESC,
    val includeDeletedMessages: Boolean = false,
)
