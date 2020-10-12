/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

/**
 * This represents the relationship of a unique identifier [id] with the [issuer].
 *
 * @property id Unique Identifier of the owner.
 * @property issuer Issuer of the owner identifier.
 *
 * @since 2020-08-04
 */
@Parcelize
data class Owner(
    val id: String,
    val issuer: String
) : Parcelable
