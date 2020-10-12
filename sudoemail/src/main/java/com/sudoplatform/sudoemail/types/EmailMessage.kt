/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.util.Date

/**
 * The Platform SDK representation of an email message.
 *
 * @property messageId Unique identifier of the email message.
 * @property clientRefId Unique client reference identifier.
 * @property userId Identifier of the user that owns the email message.
 * @property sudoId Identifier of the sudo that owns the email message.
 * @property emailAddressId Identifier if the email address account that is associated the email message - which account sent/received this message.
 * @property seen True if the user has seen the email message previously.
 * @property direction Direction of the email message.
 * @property state Current state of the email message.
 * @property from List of recipients that the email message was sent from.
 * @property to List of recipients that the email message is being sent to.
 * @property cc List of carbon copy recipients of the email message.
 * @property bcc List of blind carbon copy recipients of the email message.
 * @property replyTo List of recipients that the email message is being replied to.
 * @property subject Subject header of the email message.
 * @property createdAt [Date] When the email message was created.
 * @property updatedAt [Date] When the email message was last updated.
 *
 * @since 2020-08-11
 */
@Parcelize
data class EmailMessage(
    val messageId: String,
    val clientRefId: String? = null,
    val userId: String,
    val sudoId: String,
    val emailAddressId: String,
    val seen: Boolean = false,
    val direction: Direction,
    val state: State,
    val from: List<EmailAddress>,
    val to: List<EmailAddress>,
    val cc: List<EmailAddress> = emptyList(),
    val bcc: List<EmailAddress> = emptyList(),
    val replyTo: List<EmailAddress> = emptyList(),
    val subject: String? = null,
    val createdAt: Date,
    val updatedAt: Date,
    /** Unique identifier of the email message. Combination of the [messageId] and the [keyId].  E.g. "$messageId-keyId" **/
    internal val id: String,
    internal val keyId: String,
    internal val algorithm: String
) : Parcelable {

    /** The direction of the message */
    enum class Direction {
        /** Message is inbound to the user - message has been received by the user. */
        INBOUND,
        /** Message is outbound to the user - message has been sent by the user. */
        OUTBOUND,
        /** API Evolution - if this occurs, it may mean you need to update the library. */
        UNKNOWN
    }

    /** The current state of the message */
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
        UNKNOWN
    }

    /**
     * A representation of an email address as used in an [EmailMessage].
     *
     * @property address Address in format 'local-part@domain' of the email.
     * @property displayName The display name (or personal name) of the email address.
     */
    @Parcelize
    data class EmailAddress(
        val address: String,
        val displayName: String? = null
    ) : Parcelable {

        override fun toString(): String {
            return if (displayName?.isNotBlank() == true) {
                "$displayName <$address>"
            } else {
                address
            }
        }
    }
}
