/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

import com.sudoplatform.sudoemail.internal.domain.entities.common.DateRangeEntity

/**
 * Core entity representation of an email message date range used in the Sudo Platform Email SDK.
 *
 * Note that both timestamps cannot be specified simultaneously.
 *
 * @property sortDate [DateRangeEntity] The specification of the sortDate timestamp to perform
 *  the date range query on.
 * @property updatedAt [DateRangeEntity] The specification of the updatedAt timestamp to perform
 *  the date range query on.
 */
internal data class EmailMessageDateRangeEntity(
    val sortDate: DateRangeEntity? = null,
    val updatedAt: DateRangeEntity? = null,
)
