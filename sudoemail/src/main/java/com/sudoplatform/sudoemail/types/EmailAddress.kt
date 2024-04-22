/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Representation of an email address used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email address.
 * @property owner [String] Identifier of the user that owns the email address.
 * @property owners [List<Owner>] List of identifiers of the user/sudo associated with this email address.
 * @property emailAddress [String] Address in format 'local-part@domain' of the email.
 * @property size [Double] The total size of all email messages assigned to the email address in bytes.
 * @property numberOfEmailMessages [Int] The total number of email messages assigned to the email address.
 * @property version [Int] Current version of the email address.
 * @property createdAt [Date] When the email address was created.
 * @property updatedAt [Date] When the email address was last updated.
 * @property lastReceivedAt [Date] When the email address last received an email message.
 * @property alias [String] An optional user defined alias name for the the email address.
 * @property folders [List<EmailFolder>] List of folders associated with this email address.
 */
@Parcelize
data class EmailAddress(
    val id: String,
    val owner: String,
    val owners: List<Owner>,
    val emailAddress: String,
    val size: Double,
    val numberOfEmailMessages: Int,
    val version: Int,
    val createdAt: Date,
    val updatedAt: Date,
    val lastReceivedAt: Date? = null,
    val alias: String? = null,
    val folders: List<EmailFolder>,
) : Parcelable

/**
 * Representation of an email address without its unsealed attributes used in the Sudo
 * Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email address.
 * @property owner [String] Identifier of the user that owns the email address.
 * @property owners [List<Owner>] List of identifiers of the user/sudo associated with this email address.
 * @property emailAddress [String] Address in format 'local-part@domain' of the email.
 * @property size [Double] The total size of all email messages assigned to the email address in bytes.
 * @property version [Int] Current version of the email address.
 * @property createdAt [Date] When the email address was created.
 * @property updatedAt [Date] When the email address was last updated.
 * @property lastReceivedAt [Date] When the email address last received an email message.
 * @property folders [List<EmailFolder>] List of folders associated with this email address.
 */
@Parcelize
data class PartialEmailAddress(
    val id: String,
    val owner: String,
    val owners: List<Owner>,
    val emailAddress: String,
    val size: Double,
    val numberOfEmailMessages: Int,
    val version: Int,
    val createdAt: Date,
    val updatedAt: Date,
    val lastReceivedAt: Date? = null,
    val folders: List<EmailFolder>,
) : Parcelable
