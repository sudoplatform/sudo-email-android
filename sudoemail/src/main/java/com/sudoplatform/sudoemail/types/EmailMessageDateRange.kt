/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

/**
 * Representation of an email message date range used in the Sudo Platform Email SDK.
 *
 * Note that both timestamps cannot be specified otherwise an [InvalidArgumentException] will occur.
 *
 * @property sortDate [DateRange] The specification of the sortDate timestamp to perform
 *  the date range query on.
 * @property updatedAt [DateRange] The specification of the updatedAt timestamp to perform
 *  the date range query on.
 */
data class EmailMessageDateRange(
    val sortDate: DateRange? = null,
    val updatedAt: DateRange? = null,
)
