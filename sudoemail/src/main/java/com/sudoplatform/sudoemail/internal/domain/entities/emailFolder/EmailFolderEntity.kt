/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailFolder

import android.os.Parcelable
import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import kotlinx.parcelize.Parcelize
import java.util.Date

internal sealed class EmailFolderEntity {
    abstract val id: String
    abstract val owner: String
    abstract val owners: List<OwnerEntity>
    abstract val emailAddressId: String
    abstract val folderName: String
    abstract val size: Double
    abstract val unseenCount: Int
    abstract val version: Int
    abstract val createdAt: Date
    abstract val updatedAt: Date
}

/**
 * Core entity representation of an unsealed email folder used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email folder.
 * @property owner [String] Identifier of the user that owns the email folder.
 * @property owners [List<OwnerEntity>] List of identifiers of the user/accounts associated with this email folder.
 * @property emailAddressId [String] Identifier of the email address associated with the email folder.
 * @property folderName [String] Name assigned to the email folder (i.e. INBOX, SENT, TRASH, OUTBOX).
 * @property size [Double] The total size of all email messages assigned to the email folder in bytes.
 * @property unseenCount [Int] The total count of unseen email messages assigned to the email folder.
 * @property version [Int] Current version of the email folder.
 * @property createdAt [java.util.Date] When the email folder was created.
 * @property updatedAt [java.util.Date] When the email folder was last updated.
 * @property customFolderName [String] Custom name assigned to the email folder.
 */
@Parcelize
internal data class UnsealedEmailFolderEntity(
    override val id: String,
    override val owner: String,
    override val owners: List<OwnerEntity>,
    override val emailAddressId: String,
    override val folderName: String,
    override val size: Double,
    override val unseenCount: Int,
    override val version: Int,
    override val createdAt: Date,
    override val updatedAt: Date,
    val customFolderName: String? = null,
) : EmailFolderEntity(),
    Parcelable

/**
 * Core entity representation of an email folder used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email folder.
 * @property owner [String] Identifier of the user that owns the email folder.
 * @property owners [List<OwnerEntity>] List of identifiers of the user/accounts associated with this email folder.
 * @property emailAddressId [String] Identifier of the email address associated with the email folder.
 * @property folderName [String] Name assigned to the email folder (i.e. INBOX, SENT, TRASH, OUTBOX).
 * @property size [Double] The total size of all email messages assigned to the email folder in bytes.
 * @property unseenCount [Int] The total count of unseen email messages assigned to the email folder.
 * @property version [Int] Current version of the email folder.
 * @property createdAt [java.util.Date] When the email folder was created.
 * @property updatedAt [java.util.Date] When the email folder was last updated.
 * @property sealedCustomFolderName [String] Sealed custom name assigned to the email folder.
 */
@Parcelize
internal data class SealedEmailFolderEntity(
    override val id: String,
    override val owner: String,
    override val owners: List<OwnerEntity>,
    override val emailAddressId: String,
    override val folderName: String,
    override val size: Double,
    override val unseenCount: Int,
    override val version: Int,
    override val createdAt: Date,
    override val updatedAt: Date,
    val sealedCustomFolderName: SealedAttributeEntity? = null,
) : EmailFolderEntity(),
    Parcelable
