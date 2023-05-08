/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import java.util.Date

/**
 * Representation of the metadata of a draft email message in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the draft message.
 * @property updatedAt [Date] When the draft message was last updated.
 */
internal interface DraftEmailMessage {
    val id: String
    val updatedAt: Date
}
