/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.s3.types

import java.util.Date

/**
 * Representation of a S3 object's metadata.
 *
 * @property key [String] The key of the object.
 * @property lastModified [Date] The date the object was last modified.
 */
data class S3ClientListOutput(
    val key: String,
    val lastModified: Date,
)
