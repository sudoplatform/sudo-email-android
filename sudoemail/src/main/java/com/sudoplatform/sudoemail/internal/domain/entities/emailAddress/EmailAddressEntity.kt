/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailAddress

import android.os.Parcelable
import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.SealedEmailFolderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.UnsealedEmailFolderEntity
import kotlinx.parcelize.Parcelize
import java.util.Date

internal sealed class EmailAddressEntity {
    abstract val id: String
    abstract val owner: String
    abstract val owners: List<OwnerEntity>
    abstract val emailAddress: String
    abstract val size: Double
    abstract val numberOfEmailMessages: Int
    abstract val version: Int
    abstract val createdAt: Date
    abstract val updatedAt: Date
    abstract val lastReceivedAt: Date?
}

/**
 * Core entity representation of an unsealed email address used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email address.
 * @property owner [String] Identifier of the user that owns the email address.
 * @property owners [List<OwnerEntity>] List of identifiers of the user/sudo associated with this email address.
 * @property emailAddress [String] Address in format 'local-part@domain' of the email.
 * @property size [Double] The total size of all email messages assigned to the email address in bytes.
 * @property numberOfEmailMessages [Int] The total number of email messages assigned to the email address.
 * @property version [Int] Current version of the email address.
 * @property createdAt [java.util.Date] When the email address was created.
 * @property updatedAt [java.util.Date] When the email address was last updated.
 * @property lastReceivedAt [java.util.Date] When the email address last received an email message.
 * @property alias [String] An optional user defined alias name for the email address.
 * @property folders [List<EmailFolderEntity>] List of folders associated with this email address.
 */
@Parcelize
internal data class UnsealedEmailAddressEntity(
    override val id: String,
    override val owner: String,
    override val owners: List<OwnerEntity>,
    override val emailAddress: String,
    override val size: Double,
    override val numberOfEmailMessages: Int,
    override val version: Int,
    override val createdAt: Date,
    override val updatedAt: Date,
    override val lastReceivedAt: Date? = null,
    val alias: String? = null,
    val folders: List<UnsealedEmailFolderEntity>,
) : EmailAddressEntity(),
    Parcelable

/**
 * Core entity representation of a sealed email address used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email address.
 * @property owner [String] Identifier of the user that owns the email address.
 * @property owners [List<OwnerEntity>] List of identifiers of the user/sudo associated with this email address.
 * @property emailAddress [String] Address in format 'local-part@domain' of the email.
 * @property size [Double] The total size of all email messages assigned to the email address in bytes.
 * @property numberOfEmailMessages [Int] The total number of email messages assigned to the email address.
 * @property version [Int] Current version of the email address.
 * @property createdAt [java.util.Date] When the email address was created.
 * @property updatedAt [java.util.Date] When the email address was last updated.
 * @property lastReceivedAt [java.util.Date] When the email address last received an email message.
 * @property sealedAlias [SealedAttributeEntity] An optional sealed user defined alias name for the email address.
 * @property folders [List<SealedEmailFolderEntity>] List of folders associated with this email address.
 */
@Parcelize
internal data class SealedEmailAddressEntity(
    override val id: String,
    override val owner: String,
    override val owners: List<OwnerEntity>,
    override val emailAddress: String,
    override val size: Double,
    override val numberOfEmailMessages: Int,
    override val version: Int,
    override val createdAt: Date,
    override val updatedAt: Date,
    override val lastReceivedAt: Date? = null,
    val sealedAlias: SealedAttributeEntity? = null,
    val folders: List<SealedEmailFolderEntity>,
) : EmailAddressEntity(),
    Parcelable
