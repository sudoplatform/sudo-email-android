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
 * Valid status values for an email mask.
 *
 * These statuses control the operational state of an email mask and determine whether
 * messages can be forwarded through the mask.
 */
enum class EmailMaskStatus {
    /** The email mask is currently enabled */
    ENABLED,

    /** The email mask has been disabled by the owner */
    DISABLED,

    /** The email mask has been locked by the system or administrator */
    LOCKED,

    /** The email mask is pending verification */
    PENDING,

    /** API Evolution - if this occurs, it may mean you need to update the library. */
    UNKNOWN,
}

/**
 * Type of real address associated with the email mask.
 *
 * This indicates whether the real email address that receives forwarded messages
 * is managed internally by the email service or is an external address.
 */
enum class EmailMaskRealAddressType {
    /** The real email address is an internal email address managed by the email service */
    INTERNAL,

    /** The real email address is an external email address not managed by the email service */
    EXTERNAL,

    /** API Evolution - if this occurs, it may mean you need to update the library. */
    UNKNOWN,
}

/**
 * Representation of an email mask used in the Sudo Platform Email SDK.
 *
 * An email mask provides a privacy layer by allowing users to create temporary or permanent
 * email addresses that forward messages to their real email address. This enables users to
 * protect their real email address from spam, tracking, and unwanted communications while
 * maintaining the ability to receive and send emails.
 *
 * @property id The unique identifier of the email mask
 * @property owner The identifier of the user who owns this email mask
 * @property owners List of ownership information for this email mask
 * @property identityId The identity identifier associated with this email mask
 * @property maskAddress The publicly visible email address that serves as the mask
 * @property realAddress The actual email address that receives forwarded messages
 * @property realAddressType The type of real address (INTERNAL or EXTERNAL)
 * @property status The current status of the email mask (ENABLED, DISABLED, LOCKED)
 * @property inboundReceived The total number of inbound messages received by this mask
 * @property inboundDelivered The total number of inbound messages successfully delivered
 * @property outboundReceived The total number of outbound messages received from this mask
 * @property outboundDelivered The total number of outbound messages successfully delivered
 * @property spamCount The number of messages identified as spam
 * @property virusCount The number of messages identified as containing viruses
 * @property expiresAt The date and time when this email mask expires, if applicable
 * @property createdAt The date and time when this email mask was created
 * @property updatedAt The date and time when this email mask was last updated
 * @property version The version number
 * @property metadata Optional key-value pairs for storing additional information about the mask
 */
@Parcelize
data class EmailMask(
    val id: String,
    val owner: String,
    val owners: List<Owner>,
    val identityId: String,
    val maskAddress: String,
    val realAddress: String,
    val realAddressType: EmailMaskRealAddressType,
    val status: EmailMaskStatus,
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
 * Partial representation of an email mask used when some data could not be unsealed or processed.
 *
 * This class represents an email mask where some operations may have failed during processing,
 * such as decryption or data unsealing failures. It contains the same core information as
 * [EmailMask] but excludes the metadata field which may have failed to process.
 *
 * @property id The unique identifier of the email mask
 * @property owner The identifier of the user who owns this email mask
 * @property owners List of ownership information for this email mask
 * @property identityId The identity identifier associated with this email mask
 * @property maskAddress The publicly visible email address that serves as the mask
 * @property realAddress The actual email address that receives forwarded messages
 * @property realAddressType The type of real address (INTERNAL or EXTERNAL)
 * @property status The current status of the email mask (ENABLED, DISABLED, LOCKED)
 * @property inboundReceived The total number of inbound messages received by this mask
 * @property inboundDelivered The total number of inbound messages successfully delivered
 * @property outboundReceived The total number of outbound messages received from this mask
 * @property outboundDelivered The total number of outbound messages successfully delivered
 * @property spamCount The number of messages identified as spam
 * @property virusCount The number of messages identified as containing viruses
 * @property expiresAt The date and time when this email mask expires, if applicable
 * @property createdAt The date and time when this email mask was created
 * @property updatedAt The date and time when this email mask was last updated
 * @property version The version number for optimistic locking and conflict resolution
 */
@Parcelize
data class PartialEmailMask(
    val id: String,
    val owner: String,
    val owners: List<Owner>,
    val identityId: String,
    val maskAddress: String,
    val realAddress: String,
    val realAddressType: EmailMaskRealAddressType,
    val status: EmailMaskStatus,
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
