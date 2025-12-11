/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.common

import java.util.Date

/**
 * Core entity representation of a date range used in the Sudo Platform Email SDK.
 *
 * @property startDate [Date] The starting date of the range to query.
 * @property endDate [Date] The ending date of the range to query.
 */
internal data class DateRangeEntity(
    val startDate: Date,
    val endDate: Date,
)
