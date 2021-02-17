/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * A representation of an email address provisioned by the email service.
 *
 * @property id Unique identifier of the email address
 * @property emailAddress Address in format 'local-part@domain' of the email.
 * @property userId Identifier of the user that owns the email address.
 * @property sudoId Identifier of the sudo that owns the email address.
 * @property owners List of identifiers of user/accounts associated with this email address. Typically, this will
 * consist of at least the user id and sudo id of the account.
 * @property createdAt [Date] when the email address was created.
 * @property updatedAt [Date] when the email address was last updated.
 *
 * @since 2020-08-04
 */
@Parcelize
data class EmailAddress(
    val id: String,
    val emailAddress: String,
    val userId: String,
    val sudoId: String,
    val owners: List<Owner>,
    val createdAt: Date,
    val updatedAt: Date
) : Parcelable
