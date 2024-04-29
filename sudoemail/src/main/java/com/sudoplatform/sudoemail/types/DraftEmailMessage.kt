/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import java.util.Date

/**
 * Representation of the metadata of a draft email message in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the draft message.
 * @property emailAddressId [String] Unique identifier of the email address associated with the draft
 *  email message.
 * @property updatedAt [Date] When the draft message was last updated.
 */
internal interface DraftEmailMessage {
    val id: String
    val emailAddressId: String
    val updatedAt: Date
}
