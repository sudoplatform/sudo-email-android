/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import java.util.Date
import kotlinx.parcelize.Parcelize

/**
 * Representation of the metadata of a draft email message in the Sudo Platform Email SDK.
 *
 * @implements [DraftEmailMessage]
 * @property id [String] Unique identifier of the draft message.
 * @property updatedAt [Date] When the draft message was last updated.
 */
@Parcelize
data class DraftEmailMessageMetadata(
    override val id: String,
    override val updatedAt: Date
) : Parcelable, DraftEmailMessage
