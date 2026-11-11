/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Representation of an email alias list result used in the Sudo Platform Email SDK.
 *
 * This is identical to the [EmailAlias] type except in that it does not include the `realAddress` property.
 *
 * @property id The unique identifier of the email alias
 * @property owner The identifier of the user who owns this email alias
 * @property owners List of ownership information for this email alias
 * @property identityId The identity identifier associated with this email alias
 * @property aliasAddress The publicly visible email address that serves as the alias
 * @property realAddressType The type of real address (INTERNAL or EXTERNAL)
 * @property status The current status of the email alias (ENABLED, DISABLED, LOCKED)
 * @property inboundReceived The total number of inbound messages received by this alias
 * @property inboundDelivered The total number of inbound messages successfully delivered
 * @property outboundReceived The total number of outbound messages received from this alias
 * @property outboundDelivered The total number of outbound messages successfully delivered
 * @property spamCount The number of messages identified as spam
 * @property virusCount The number of messages identified as containing viruses
 * @property expiresAt The date and time when this email alias expires, if applicable
 * @property createdAt The date and time when this email alias was created
 * @property updatedAt The date and time when this email alias was last updated
 * @property version The version number
 * @property metadata Optional key-value pairs for storing additional information about the alias
 */
@Parcelize
data class EmailAliasListItem(
    val id: String,
    val owner: String,
    val owners: List<Owner>,
    val identityId: String,
    val aliasAddress: String,
    val realAddressType: EmailAliasRealAddressType,
    val status: EmailAliasStatus,
    val inboundReceived: Int,
    val inboundDelivered: Int,
    val outboundReceived: Int,
    val outboundDelivered: Int,
    val spamCount: Int,
    val virusCount: Int,
    val expiresAt: Date?,
    val createdAt: Date,
    val updatedAt: Date,
    val version: Int,
    val metadata: Map<String, String>?,
) : Parcelable

/**
 * Partial representation of an email alias list item used when some data could not be unsealed or processed.
 *
 * This is identical to the [PartialEmailAlias] type except in that it does not include the `realAddress` property.
 *
 * @property id The unique identifier of the email alias
 * @property owner The identifier of the user who owns this email alias
 * @property owners List of ownership information for this email alias
 * @property identityId The identity identifier associated with this email alias
 * @property aliasAddress The publicly visible email address that serves as the alias
 * @property realAddressType The type of real address (INTERNAL or EXTERNAL)
 * @property status The current status of the email alias (ENABLED, DISABLED, LOCKED)
 * @property inboundReceived The total number of inbound messages received by this alias
 * @property inboundDelivered The total number of inbound messages successfully delivered
 * @property outboundReceived The total number of outbound messages received from this alias
 * @property outboundDelivered The total number of outbound messages successfully delivered
 * @property spamCount The number of messages identified as spam
 * @property virusCount The number of messages identified as containing viruses
 * @property expiresAt The date and time when this email alias expires, if applicable
 * @property createdAt The date and time when this email alias was created
 * @property updatedAt The date and time when this email alias was last updated
 * @property version The version number for optimistic locking and conflict resolution
 */
@Parcelize
data class PartialEmailAliasListItem(
    val id: String,
    val owner: String,
    val owners: List<Owner>,
    val identityId: String,
    val aliasAddress: String,
    val realAddressType: EmailAliasRealAddressType,
    val status: EmailAliasStatus,
    val inboundReceived: Int,
    val inboundDelivered: Int,
    val outboundReceived: Int,
    val outboundDelivered: Int,
    val spamCount: Int,
    val virusCount: Int,
    val expiresAt: Date?,
    val createdAt: Date,
    val updatedAt: Date,
    val version: Int,
) : Parcelable
