/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Representation of an email folder used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email folder.
 * @property owner [String] Identifier of the user that owns the email folder.
 * @property owners [List<Owner>] List of identifiers of the user/accounts associated with this email folder.
 * @property emailAddressId [String] Identifier of the email address associated with the email folder.
 * @property folderName [String] Name assigned to the email folder (i.e. INBOX, SENT, TRASH, OUTBOX).
 * @property size [Double] The total size of all email messages assigned to the email folder in bytes.
 * @property unseenCount [Int] The total count of unseen email messages assigned to the email folder.
 * @property version [Int] Current version of the email folder.
 * @property createdAt [Date] When the email folder was created.
 * @property updatedAt [Date] When the email folder was last updated.
 */
@Parcelize
data class EmailFolder(
    val id: String,
    val owner: String,
    val owners: List<Owner>,
    val emailAddressId: String,
    val folderName: String,
    val size: Double,
    val unseenCount: Int,
    val version: Int,
    val createdAt: Date,
    val updatedAt: Date,
) : Parcelable
