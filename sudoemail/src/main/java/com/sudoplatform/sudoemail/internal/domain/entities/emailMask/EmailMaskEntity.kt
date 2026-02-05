package com.sudoplatform.sudoemail.internal.domain.entities.emailMask

import android.os.Parcelable
import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import kotlinx.parcelize.Parcelize
import java.util.Date

enum class EmailMaskEntityRealAddressType {
    INTERNAL,
    EXTERNAL,

    /** API Evolution - if this occurs, it may mean you need to update the library. */
    UNKNOWN,
}

enum class EmailMaskEntityStatus {
    ENABLED,
    DISABLED,
    LOCKED,
    PENDING,

    /** API Evolution - if this occurs, it may mean you need to update the library. */
    UNKNOWN,
}

internal sealed class EmailMaskEntity {
    abstract val id: String
    abstract val owner: String
    abstract val owners: List<OwnerEntity>
    abstract val identityId: String
    abstract val maskAddress: String
    abstract val realAddress: String
    abstract val realAddressType: EmailMaskEntityRealAddressType
    abstract val status: EmailMaskEntityStatus
    abstract val inboundReceived: Int
    abstract val inboundDelivered: Int
    abstract val outboundReceived: Int
    abstract val outboundDelivered: Int
    abstract val spamCount: Int
    abstract val virusCount: Int
    abstract val expiresAt: Date?
    abstract val createdAt: Date
    abstract val updatedAt: Date
    abstract val version: Int
}

/**
 * Core entity representation of an unsealed email mask used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email mask.
 * @property owner [String] Identifier of the user that owns the email mask.
 * @property owners [List<OwnerEntity>] List of identifiers of the user/sudo associated with this email mask.
 * @property identityId [String] Identity identifier associated with the email mask.
 * @property maskAddress [String] The mask email address in format 'local-part@domain'.
 * @property realAddress [String] The real email address that the mask forwards to.
 * @property realAddressType [EmailMaskEntityRealAddressType] Type of the real address (internal or external).
 * @property status [EmailMaskEntityStatus] Current status of the email mask.
 * @property inboundReceived [Int] Number of inbound emails received.
 * @property inboundDelivered [Int] Number of inbound emails delivered.
 * @property outboundReceived [Int] Number of outbound emails received.
 * @property outboundDelivered [Int] Number of outbound emails delivered.
 * @property spamCount [Int] Number of spam emails detected.
 * @property virusCount [Int] Number of virus emails detected.* @property expiresAt [Date] Optional expiration date for the email mask.
 * @property version [Int] Current version of the email mask.
 * @property createdAt [java.util.Date] When the email mask was created.
 * @property updatedAt [java.util.Date] When the email mask was last updated.
 * @property metadata [Map<String, String>] Optional unsealed metadata associated with the email mask.
 */
@Parcelize
internal data class UnsealedEmailMaskEntity(
    override val id: String,
    override val owner: String,
    override val owners: List<OwnerEntity>,
    override val identityId: String,
    override val maskAddress: String,
    override val realAddress: String,
    override val realAddressType: EmailMaskEntityRealAddressType,
    override val status: EmailMaskEntityStatus,
    override val inboundReceived: Int,
    override val inboundDelivered: Int,
    override val outboundReceived: Int,
    override val outboundDelivered: Int,
    override val spamCount: Int,
    override val virusCount: Int,
    override val expiresAt: Date?,
    override val version: Int,
    override val createdAt: Date,
    override val updatedAt: Date,
    val metadata: Map<String, String>? = null,
) : EmailMaskEntity(),
    Parcelable

/**
 * Core entity representation of a sealed email mask used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email mask.
 * @property owner [String] Identifier of the user that owns the email mask.
 * @property owners [List<OwnerEntity>] List of identifiers of the user/sudo associated with this email mask.
 * @property identityId [String] Identity identifier associated with the email mask.
 * @property maskAddress [String] The mask email address in format 'local-part@domain'.
 * @property realAddress [String] The real email address that the mask forwards to.
 * @property realAddressType [EmailMaskEntityRealAddressType] Type of the real address (internal or external).
 * @property status [EmailMaskEntityStatus] Current status of the email mask.
 * @property inboundReceived [Int] Number of inbound emails received.
 * @property inboundDelivered [Int] Number of inbound emails delivered.
 * @property outboundReceived [Int] Number of outbound emails received.
 * @property outboundDelivered [Int] Number of outbound emails delivered.
 * @property spamCount [Int] Number of spam emails detected.
 * @property virusCount [Int] Number of virus emails detected.
 * @property expiresAt [Date] Optional expiration date for the email mask.
 * @property version [Int] Current version of the email mask.
 * @property createdAt [java.util.Date] When the email mask was created.
 * @property updatedAt [java.util.Date] When the email mask was last updated.
 * @property sealedMetadata [SealedAttributeEntity] Optional sealed metadata associated with the email mask.
 */
@Parcelize
internal data class SealedEmailMaskEntity(
    override val id: String,
    override val owner: String,
    override val owners: List<OwnerEntity>,
    override val identityId: String,
    override val maskAddress: String,
    override val realAddress: String,
    override val realAddressType: EmailMaskEntityRealAddressType,
    override val status: EmailMaskEntityStatus,
    override val inboundReceived: Int,
    override val inboundDelivered: Int,
    override val outboundReceived: Int,
    override val outboundDelivered: Int,
    override val spamCount: Int,
    override val virusCount: Int,
    override val expiresAt: Date?,
    override val version: Int,
    override val createdAt: Date,
    override val updatedAt: Date,
    val sealedMetadata: SealedAttributeEntity? = null,
) : EmailMaskEntity(),
    Parcelable

/**
 * Core entity representation of a partial email mask used when unsealing fails in the Sudo Platform Email SDK.
 *
 * This entity contains only the basic information that can be retrieved without decryption.
 *
 * @property id [String] Unique identifier of the email mask.
 * @property owner [String] Identifier of the owner.
 * @property owners [List<OwnerEntity>] List of owners of the email mask.
 * @property identityId [String] Identity identifier of the owner.
 * @property maskAddress [String] The email mask address.
 * @property realAddress [String] The real email address mapped to the mask.
 * @property realAddressType [EmailMaskEntityRealAddressType] Type of the real address (internal or external).
 * @property status [EmailMaskEntityStatus] Current status of the email mask.
 * @property inboundReceived [Int] Number of inbound emails received.
 * @property inboundDelivered [Int] Number of inbound emails delivered.
 * @property outboundReceived [Int] Number of outbound emails received.
 * @property outboundDelivered [Int] Number of outbound emails delivered.
 * @property spamCount [Int] Number of spam emails blocked.
 * @property virusCount [Int] Number of virus emails blocked.
 * @property expiresAt [Date] Optional expiration date for the email mask.
 * @property version [Int] Current version of the email mask.
 * @property createdAt [java.util.Date] When the email mask was created.
 * @property updatedAt [java.util.Date] When the email mask was last updated.
 */
@Parcelize
internal data class PartialEmailMaskEntity(
    override val id: String,
    override val owner: String,
    override val owners: List<OwnerEntity>,
    override val identityId: String,
    override val maskAddress: String,
    override val realAddress: String,
    override val realAddressType: EmailMaskEntityRealAddressType,
    override val status: EmailMaskEntityStatus,
    override val inboundReceived: Int,
    override val inboundDelivered: Int,
    override val outboundReceived: Int,
    override val outboundDelivered: Int,
    override val spamCount: Int,
    override val virusCount: Int,
    override val expiresAt: Date?,
    override val version: Int,
    override val createdAt: Date,
    override val updatedAt: Date,
) : EmailMaskEntity(),
    Parcelable

internal sealed interface EmailMaskStatusFilterInputEntity

/**
 * Filter for messages with a status equal to the specified value.
 *
 * @property equal The [EmailMaskEntityStatus] value to match.
 */
internal data class EqualStatusFilterEntity(
    val equal: EmailMaskEntityStatus,
) : EmailMaskStatusFilterInputEntity

/**
 * Filter for messages with a status matching one of the specified values.
 *
 * @property oneOf The [List] of [EmailMaskEntityStatus] values to match.
 */
internal data class OneOfStatusFilterEntity(
    val oneOf: List<EmailMaskEntityStatus>,
) : EmailMaskStatusFilterInputEntity

/**
 * Filter for messages with a status not equal to the specified value.
 *
 * @property notEqual The [EmailMaskEntityStatus] value to exclude.
 */
internal data class NotEqualStatusFilterEntity(
    val notEqual: EmailMaskEntityStatus,
) : EmailMaskStatusFilterInputEntity

/**
 * Filter for messages with a status not matching any of the specified values.
 *
 * @property notOneOf The [List] of [EmailMaskEntityStatus] values to exclude.
 */
internal data class NotOneOfStatusFilterEntity(
    val notOneOf: List<EmailMaskEntityStatus>,
) : EmailMaskStatusFilterInputEntity

internal sealed interface EmailMaskRealAddressTypeFilterInputEntity

/**
 * Filter for messages with a real address type equal to the specified value.
 *
 * @property equal The [EmailMaskEntityRealAddressType] value to match.
 */
internal data class EqualRealAddressTypeFilterEntity(
    val equal: EmailMaskEntityRealAddressType,
) : EmailMaskRealAddressTypeFilterInputEntity

/**
 * Filter for messages with a real address type matching one of the specified values.
 *
 * @property oneOf The [List] of [EmailMaskEntityRealAddressType] values to match.
 */
internal data class OneOfRealAddressTypeFilterEntity(
    val oneOf: List<EmailMaskEntityRealAddressType>,
) : EmailMaskRealAddressTypeFilterInputEntity

/**
 * Filter for messages with a real address type not equal to the specified value.
 *
 * @property notEqual The [EmailMaskEntityRealAddressType] value to exclude.
 */
internal data class NotEqualRealAddressTypeFilterEntity(
    val notEqual: EmailMaskEntityRealAddressType,
) : EmailMaskRealAddressTypeFilterInputEntity

/**
 * Filter for messages with a real address type not matching any of the specified values.
 *
 * @property notOneOf The [List] of [EmailMaskEntityRealAddressType] values to exclude.
 */
internal data class NotOneOfRealAddressTypeFilterEntity(
    val notOneOf: List<EmailMaskEntityRealAddressType>,
) : EmailMaskRealAddressTypeFilterInputEntity

internal data class EmailMaskFilterInputEntity(
    val status: EmailMaskStatusFilterInputEntity? = null,
    val addressType: EmailMaskRealAddressTypeFilterInputEntity? = null,
)
