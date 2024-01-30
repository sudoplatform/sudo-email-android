/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.types.CachePolicy

/**
 * Input object containing properties used to list provisioned email addresses.
 *
 * @property cachePolicy [CachePolicy] Determines how the data will be fetched. When using [CachePolicy.CACHE_ONLY],
 *  be aware that this will only return cached results of identical API calls.
 * @property limit [Int] Number of email addresses to return. If omitted the limit defaults to 10.
 * @property nextToken [String] A token generated from previous calls to [SudoEmailClient.listEmailAddresses].
 *  This is to allow for pagination. This value should be generated from a previous
 *  pagination call, otherwise it will throw an exception. The same arguments should be
 *  supplied to this method if using a previously generated [nextToken].
 */
data class ListEmailAddressesInput(
    val cachePolicy: CachePolicy = CachePolicy.REMOTE_ONLY,
    val limit: Int? = SudoEmailClient.DEFAULT_EMAIL_ADDRESS_LIMIT,
    val nextToken: String? = null,
)
