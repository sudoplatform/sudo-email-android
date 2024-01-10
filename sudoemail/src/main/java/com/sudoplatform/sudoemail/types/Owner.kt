/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * This represents the relationship of a unique identifier [id] with the [issuer].
 *
 * @property id [String] Unique Identifier of the owner.
 * @property issuer [String] Issuer of the owner identifier.
 */
@Parcelize
data class Owner(
    val id: String,
    val issuer: String,
) : Parcelable
