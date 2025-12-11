/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.draftMessage

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Core entity representation of the metadata of a draft email message in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the draft message.
 * @property emailAddressId [String] Unique identifier of the email address associated with the draft
 *  email message.
 * @property updatedAt [Date] When the draft message was last updated.
 */
@Parcelize
internal data class DraftEmailMessageMetadataEntity(
    val id: String,
    val emailAddressId: String,
    val updatedAt: Date,
) : Parcelable
