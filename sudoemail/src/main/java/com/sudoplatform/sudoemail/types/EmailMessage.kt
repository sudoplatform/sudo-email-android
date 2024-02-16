/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Representation of an email message used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email message.
 * @property clientRefId [String] Unique client reference identifier.
 * @property owner [String] Identifier of the user that owns the email message.
 * @property owners [List<Owner>] List of identifiers of the user/sudo associated with this email message.
 * @property emailAddressId [String] Identifier of the email address that is associated with the
 *  email message - which address sent/received this message.
 * @property folderId [String] Unique identifier of the email folder which the message is assigned to.
 * @property previousFolderId [String] Unique identifier of the previous email folder which the message
 *  was assigned to, if any.
 * @property seen [Boolean] True if the user has previously seen the email message.
 * @property direction [Direction] Direction of the email message.
 * @property state [State] Current state of the email message.
 * @property version [Int] Current version of the email message.
 * @property sortDate [Date] When the email message was processed by the service.
 * @property createdAt [Date] When the email message was created.
 * @property updatedAt [Date] When the email message was last updated.
 * @property size [Double] The size of the encrypted RFC822 data stored in the backend in bytes. This value is used to
 *  calculate the total storage used by an email address or user and is used to enforce email storage
 *  related entitlements.
 * @property from [List<EmailMessageAddress>] List of recipients that the email message was sent from.
 * @property to [List<EmailMessageAddress>] List of recipients that the email message is being sent to.
 * @property cc [List<EmailMessageAddress>] List of carbon copy recipients of the email message.
 * @property bcc [List<EmailMessageAddress>] List of blind carbon copy recipients of the email message.
 * @property replyTo [List<EmailMessageAddress>] List of recipients that a reply to this email message
 *  will be sent to.
 * @property subject [String] Subject header of the email message.
 * @property sentAt [Date] When the email message was sent.
 * @property receivedAt [Date] When the email message was received.
 * @property hasAttachments [Boolean] Whether or not the message has attachments. False if message was
 *  sent prior to this property being added.
 */
@Parcelize
data class EmailMessage(
    val id: String,
    val clientRefId: String? = null,
    val owner: String,
    val owners: List<Owner>,
    val emailAddressId: String,
    val folderId: String,
    val previousFolderId: String? = null,
    val seen: Boolean = false,
    val direction: Direction,
    val state: State,
    val version: Int,
    val sortDate: Date,
    val createdAt: Date,
    val updatedAt: Date,
    val size: Double,
    val from: List<EmailAddress>,
    val to: List<EmailAddress>,
    val cc: List<EmailAddress> = emptyList(),
    val bcc: List<EmailAddress> = emptyList(),
    val replyTo: List<EmailAddress> = emptyList(),
    val subject: String? = null,
    val sentAt: Date? = null,
    val receivedAt: Date? = null,
    val hasAttachments: Boolean,
) : Parcelable {

    /**
     * A representation of an email address as used in an [EmailMessage].
     *
     * @property emailAddress [String] Address in format 'local-part@domain' of the email.
     * @property displayName [String] The display name (or personal name) of the email address.
     */
    @Parcelize
    @Keep
    data class EmailAddress(
        val emailAddress: String,
        val displayName: String? = null,
    ) : Parcelable {

        override fun toString(): String {
            return if (displayName?.isNotBlank() == true) {
                "$displayName <$emailAddress>"
            } else {
                emailAddress
            }
        }
    }
}

/**
 * Representation of an email message without its unsealed attributes used in the Sudo
 * Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email message.
 * @property clientRefId [String] Unique client reference identifier.
 * @property owner [String] Identifier of the user that owns the email message.
 * @property owners [List<Owner>] List of identifiers of the user/sudo associated with this email message.
 * @property emailAddressId [String] Identifier of the email address that is associated with the
 *  email message - which address sent/received this message.
 * @property folderId [String] Unique identifier of the email folder which the message is assigned to.
 * @property previousFolderId [String] Unique identifier of the previous email folder which the message
 *  was assigned to, if any.
 * @property seen [Boolean] True if the user has previously seen the email message.
 * @property direction [Direction] Direction of the email message.
 * @property state [State] Current state of the email message.
 * @property version [Int] Current version of the email message.
 * @property sortDate [Date] When the email message was processed by the service.
 * @property createdAt [Date] When the email message was created.
 * @property updatedAt [Date] When the email message was last updated.
 * @property size [Double] The size of the encrypted RFC822 data stored in the backend in bytes. This value is used to
 *  calculate the total storage used by an email address or user and is used to enforce email storage
 *  related entitlements.
 */
@Parcelize
data class PartialEmailMessage(
    val id: String,
    val clientRefId: String? = null,
    val owner: String,
    val owners: List<Owner>,
    val emailAddressId: String,
    val folderId: String,
    val previousFolderId: String? = null,
    val seen: Boolean = false,
    val direction: Direction,
    val state: State,
    val version: Int,
    val sortDate: Date,
    val createdAt: Date,
    val updatedAt: Date,
    val size: Double,
) : Parcelable {

    /**
     * A representation of an email address as used in a [PartialEmailMessage].
     *
     * @property emailAddress [String] Address in format 'local-part@domain' of the email.
     * @property displayName [String] The display name (or personal name) of the email address.
     */
    @Parcelize
    data class EmailAddress(
        val emailAddress: String,
        val displayName: String? = null,
    ) : Parcelable {

        override fun toString(): String {
            return if (displayName?.isNotBlank() == true) {
                "$displayName <$emailAddress>"
            } else {
                emailAddress
            }
        }
    }
}

/**
 * Representation of an enumeration depicting the direction of an email message in the Sudo Platform
 * Email SDK.
 *
 * @enum Direction
 */
enum class Direction {
    /** Message is inbound to the user - message has been received by the user. */
    INBOUND,

    /** Message is outbound to the user - message has been sent by the user. */
    OUTBOUND,

    /** API Evolution - if this occurs, it may mean you need to update the library. */
    UNKNOWN,
}

/**
 * Representation of an enumeration depicting the current state of an email message in the Sudo Platform
 * Email SDK.
 *
 * @enum State
 */
enum class State {
    /** Outbound message is queued to be sent. */
    QUEUED,

    /** Outbound message has been sent. */
    SENT,

    /** Outbound message has been acknowledged as delivered upstream. */
    DELIVERED,

    /** Outbound message has been acknowledged as undelivered upstream. */
    UNDELIVERED,

    /** Outbound message has been acknowledged as failed upstream. */
    FAILED,

    /** Inbound message has been received. */
    RECEIVED,

    /** API Evolution - if this occurs, it may mean you need to update the library. */
    UNKNOWN,
}
