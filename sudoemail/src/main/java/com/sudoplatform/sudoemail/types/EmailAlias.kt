/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Valid status values for an email alias.
 *
 * These statuses control the operational state of an email alias and determine whether
 * messages can be forwarded through the alias.
 */
enum class EmailAliasStatus {
    /** The email alias is currently enabled */
    ENABLED,

    /** The email alias has been disabled by the owner */
    DISABLED,

    /** The email alias has been locked by the system or administrator */
    LOCKED,

    /** The email alias is pending verification */
    PENDING,

    /** API Evolution - if this occurs, it may mean you need to update the library. */
    UNKNOWN,
}

/**
 * Type of real address associated with the email alias.
 *
 * This indicates whether the real email address that receives forwarded messages
 * is managed internally by the email service or is an external address.
 */
enum class EmailAliasRealAddressType {
    /** The real email address is an internal email address managed by the email service */
    INTERNAL,

    /** The real email address is an external email address not managed by the email service */
    EXTERNAL,

    /** API Evolution - if this occurs, it may mean you need to update the library. */
    UNKNOWN,
}

/**
 * Representation of an email alias used in the Sudo Platform Email SDK.
 *
 * An email alias provides a privacy layer by allowing users to create temporary or permanent
 * email addresses that forward messages to their real email address. This enables users to
 * protect their real email address from spam, tracking, and unwanted communications while
 * maintaining the ability to receive and send emails.
 *
 * @property id The unique identifier of the email alias
 * @property owner The identifier of the user who owns this email alias
 * @property owners List of ownership information for this email alias
 * @property identityId The identity identifier associated with this email alias
 * @property aliasAddress The publicly visible email address that serves as the alias
 * @property realAddress The actual email address that receives forwarded messages
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
data class EmailAlias(
    val id: String,
    val owner: String,
    val owners: List<Owner>,
    val identityId: String,
    val aliasAddress: String,
    val realAddress: String,
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
 * Partial representation of an email alias used when some data could not be unsealed or processed.
 *
 * This class represents an email alias where some operations may have failed during processing,
 * such as decryption or data unsealing failures. It contains the same core information as
 * [EmailAlias] but excludes the metadata field which may have failed to process.
 *
 * @property id The unique identifier of the email alias
 * @property owner The identifier of the user who owns this email alias
 * @property owners List of ownership information for this email alias
 * @property identityId The identity identifier associated with this email alias
 * @property aliasAddress The publicly visible email address that serves as the alias
 * @property realAddress The actual email address that receives forwarded messages
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
data class PartialEmailAlias(
    val id: String,
    val owner: String,
    val owners: List<Owner>,
    val identityId: String,
    val aliasAddress: String,
    val realAddress: String,
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
