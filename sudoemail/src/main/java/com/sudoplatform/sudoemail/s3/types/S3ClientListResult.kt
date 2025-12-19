/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.s3.types

/**
 * Result from listing S3 objects with pagination support.
 *
 * @property items [List] The list of S3 objects.
 * @property nextToken [String] Optional token for retrieving the next page of results.
 */
data class S3ClientListResult(
    val items: List<S3ClientListOutput>,
    val nextToken: String?,
)
