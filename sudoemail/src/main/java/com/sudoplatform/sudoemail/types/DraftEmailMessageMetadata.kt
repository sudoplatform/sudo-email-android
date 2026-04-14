/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Representation of the metadata of a draft email message in the Sudo Platform Email SDK.
 *
 * @implements [DraftEmailMessage]
 * @property id [String] Unique identifier of the draft message.
 * @property emailAddressId [String] Unique identifier of the email address associated with the draft
 *  email message.
 * @property updatedAt [Date] When the draft message was last updated.
 * @property emailMaskId [String?] Optional email mask ID associated with the draft.
 */
@Parcelize
data class DraftEmailMessageMetadata(
    override val id: String,
    override val emailAddressId: String,
    override val updatedAt: Date,
    override val emailMaskId: String? = null,
) : Parcelable,
    DraftEmailMessage
