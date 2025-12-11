/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailFolder

import android.os.Parcelable
import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Core entity representation of a partial email folder used in the Sudo Platform Email SDK.
 *
 * This entity represents an email folder that could not be fully unsealed/decrypted.
 *
 * @property id [String] Unique identifier of the email folder.
 * @property owner [String] Identifier of the user that owns the email folder.
 * @property owners [List] of [OwnerEntity] identifiers of the user/accounts associated with this email folder.
 * @property emailAddressId [String] Identifier of the email address associated with the email folder.
 * @property folderName [String] Name assigned to the email folder (i.e. INBOX, SENT, TRASH, OUTBOX).
 * @property size [Double] The total size of all email messages assigned to the email folder in bytes.
 * @property unseenCount [Int] The total count of unseen email messages assigned to the email folder.
 * @property version [Int] Current version of the email folder.
 * @property createdAt [Date] When the email folder was created.
 * @property updatedAt [Date] When the email folder was last updated.
 */
@Parcelize
internal data class PartialEmailFolderEntity(
    val id: String,
    val owner: String,
    val owners: List<OwnerEntity>,
    val emailAddressId: String,
    val folderName: String,
    val size: Double,
    val unseenCount: Int,
    val version: Int,
    val createdAt: Date,
    val updatedAt: Date,
) : Parcelable
