/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.common

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Core entity representation of an owner used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique Identifier of the owner.
 * @property issuer [String] Issuer of the owner identifier.
 */
@Parcelize
internal data class OwnerEntity(
    val id: String,
    val issuer: String,
) : Parcelable
