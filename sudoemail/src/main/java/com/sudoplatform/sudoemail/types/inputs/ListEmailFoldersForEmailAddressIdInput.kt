/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.types.CachePolicy

/**
 * Input object containing an email address identifier and other properties used to list
 * email folders for an email address.
 *
 * @property emailAddressId [String] The identifier of the email address associated with the email folders.
 * @property cachePolicy [CachePolicy] Determines how the data will be fetched. When using [CachePolicy.CACHE_ONLY],
 *  be aware that this will only return cached results of identical API calls.
 * @property limit [Int] Number of email folders to return. If omitted the limit defaults to 10.
 * @property nextToken [String] A token generated from previous calls to [SudoEmailClient.listEmailFoldersForEmailAddressId].
 *  This is to allow for pagination. This value should be generated from a previous
 *  pagination call, otherwise it will throw an exception. The same arguments should be
 *  supplied to this method if using a previously generated [nextToken].
 */
data class ListEmailFoldersForEmailAddressIdInput(
    val emailAddressId: String,
    val cachePolicy: CachePolicy = CachePolicy.REMOTE_ONLY,
    val limit: Int? = SudoEmailClient.DEFAULT_EMAIL_FOLDER_LIMIT,
    val nextToken: String? = null,
)
