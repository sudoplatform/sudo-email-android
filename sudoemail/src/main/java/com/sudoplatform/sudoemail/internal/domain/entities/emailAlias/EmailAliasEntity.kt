package com.sudoplatform.sudoemail.internal.domain.entities.emailAlias

import android.os.Parcelable
import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import kotlinx.parcelize.Parcelize
import java.util.Date

enum class EmailAliasEntityRealAddressType {
    INTERNAL,
    EXTERNAL,

    /** API Evolution - if this occurs, it may mean you need to update the library. */
    UNKNOWN,
}

enum class EmailAliasEntityStatus {
    ENABLED,
    DISABLED,
    LOCKED,
    PENDING,

    /** API Evolution - if this occurs, it may mean you need to update the library. */
    UNKNOWN,
}

internal sealed class EmailAliasEntity {
    abstract val id: String
    abstract val owner: String
    abstract val owners: List<OwnerEntity>
    abstract val identityId: String
    abstract val aliasAddress: String
    abstract val realAddress: String
    abstract val realAddressType: EmailAliasEntityRealAddressType
    abstract val status: EmailAliasEntityStatus
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
 * Core entity representation of an unsealed email alias used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email alias.
 * @property owner [String] Identifier of the user that owns the email alias.
 * @property owners [List<OwnerEntity>] List of identifiers of the user/sudo associated with this email alias.
 * @property identityId [String] Identity identifier associated with the email alias.
 * @property aliasAddress [String] The alias email address in format 'local-part@domain'.
 * @property realAddress [String] The real email address that the alias forwards to.
 * @property realAddressType [EmailAliasEntityRealAddressType] Type of the real address (internal or external).
 * @property status [EmailAliasEntityStatus] Current status of the email alias.
 * @property inboundReceived [Int] Number of inbound emails received.
 * @property inboundDelivered [Int] Number of inbound emails delivered.
 * @property outboundReceived [Int] Number of outbound emails received.
 * @property outboundDelivered [Int] Number of outbound emails delivered.
 * @property spamCount [Int] Number of spam emails detected.
 * @property virusCount [Int] Number of virus emails detected.* @property expiresAt [Date] Optional expiration date for the email alias.
 * @property version [Int] Current version of the email alias.
 * @property createdAt [java.util.Date] When the email alias was created.
 * @property updatedAt [java.util.Date] When the email alias was last updated.
 * @property metadata [Map<String, String>] Optional unsealed metadata associated with the email alias.
 */
@Parcelize
internal data class UnsealedEmailAliasEntity(
    override val id: String,
    override val owner: String,
    override val owners: List<OwnerEntity>,
    override val identityId: String,
    override val aliasAddress: String,
    override val realAddress: String,
    override val realAddressType: EmailAliasEntityRealAddressType,
    override val status: EmailAliasEntityStatus,
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
) : EmailAliasEntity(),
    Parcelable

/**
 * Core entity representation of a sealed email alias used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email alias.
 * @property owner [String] Identifier of the user that owns the email alias.
 * @property owners [List<OwnerEntity>] List of identifiers of the user/sudo associated with this email alias.
 * @property identityId [String] Identity identifier associated with the email alias.
 * @property aliasAddress [String] The alias email address in format 'local-part@domain'.
 * @property realAddress [String] The real email address that the alias forwards to.
 * @property realAddressType [EmailAliasEntityRealAddressType] Type of the real address (internal or external).
 * @property status [EmailAliasEntityStatus] Current status of the email alias.
 * @property inboundReceived [Int] Number of inbound emails received.
 * @property inboundDelivered [Int] Number of inbound emails delivered.
 * @property outboundReceived [Int] Number of outbound emails received.
 * @property outboundDelivered [Int] Number of outbound emails delivered.
 * @property spamCount [Int] Number of spam emails detected.
 * @property virusCount [Int] Number of virus emails detected.
 * @property expiresAt [Date] Optional expiration date for the email alias.
 * @property version [Int] Current version of the email alias.
 * @property createdAt [java.util.Date] When the email alias was created.
 * @property updatedAt [java.util.Date] When the email alias was last updated.
 * @property sealedMetadata [SealedAttributeEntity] Optional sealed metadata associated with the email alias.
 */
@Parcelize
internal data class SealedEmailAliasEntity(
    override val id: String,
    override val owner: String,
    override val owners: List<OwnerEntity>,
    override val identityId: String,
    override val aliasAddress: String,
    override val realAddress: String,
    override val realAddressType: EmailAliasEntityRealAddressType,
    override val status: EmailAliasEntityStatus,
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
) : EmailAliasEntity(),
    Parcelable

/**
 * Core entity representation of a partial email alias used when unsealing fails in the Sudo Platform Email SDK.
 *
 * This entity contains only the basic information that can be retrieved without decryption.
 *
 * @property id [String] Unique identifier of the email alias.
 * @property owner [String] Identifier of the owner.
 * @property owners [List<OwnerEntity>] List of owners of the email alias.
 * @property identityId [String] Identity identifier of the owner.
 * @property aliasAddress [String] The email alias address.
 * @property realAddress [String] The real email address mapped to the alias.
 * @property realAddressType [EmailAliasEntityRealAddressType] Type of the real address (internal or external).
 * @property status [EmailAliasEntityStatus] Current status of the email alias.
 * @property inboundReceived [Int] Number of inbound emails received.
 * @property inboundDelivered [Int] Number of inbound emails delivered.
 * @property outboundReceived [Int] Number of outbound emails received.
 * @property outboundDelivered [Int] Number of outbound emails delivered.
 * @property spamCount [Int] Number of spam emails blocked.
 * @property virusCount [Int] Number of virus emails blocked.
 * @property expiresAt [Date] Optional expiration date for the email alias.
 * @property version [Int] Current version of the email alias.
 * @property createdAt [java.util.Date] When the email alias was created.
 * @property updatedAt [java.util.Date] When the email alias was last updated.
 */
@Parcelize
internal data class PartialEmailAliasEntity(
    override val id: String,
    override val owner: String,
    override val owners: List<OwnerEntity>,
    override val identityId: String,
    override val aliasAddress: String,
    override val realAddress: String,
    override val realAddressType: EmailAliasEntityRealAddressType,
    override val status: EmailAliasEntityStatus,
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
) : EmailAliasEntity(),
    Parcelable

internal sealed interface EmailAliasStatusFilterInputEntity

/**
 * Filter for messages with a status equal to the specified value.
 *
 * @property equal The [EmailAliasEntityStatus] value to match.
 */
internal data class EqualStatusFilterEntity(
    val equal: EmailAliasEntityStatus,
) : EmailAliasStatusFilterInputEntity

/**
 * Filter for messages with a status matching one of the specified values.
 *
 * @property oneOf The [List] of [EmailAliasEntityStatus] values to match.
 */
internal data class OneOfStatusFilterEntity(
    val oneOf: List<EmailAliasEntityStatus>,
) : EmailAliasStatusFilterInputEntity

/**
 * Filter for messages with a status not equal to the specified value.
 *
 * @property notEqual The [EmailAliasEntityStatus] value to exclude.
 */
internal data class NotEqualStatusFilterEntity(
    val notEqual: EmailAliasEntityStatus,
) : EmailAliasStatusFilterInputEntity

/**
 * Filter for messages with a status not matching any of the specified values.
 *
 * @property notOneOf The [List] of [EmailAliasEntityStatus] values to exclude.
 */
internal data class NotOneOfStatusFilterEntity(
    val notOneOf: List<EmailAliasEntityStatus>,
) : EmailAliasStatusFilterInputEntity

internal sealed interface EmailAliasRealAddressTypeFilterInputEntity

/**
 * Filter for messages with a real address type equal to the specified value.
 *
 * @property equal The [EmailAliasEntityRealAddressType] value to match.
 */
internal data class EqualRealAddressTypeFilterEntity(
    val equal: EmailAliasEntityRealAddressType,
) : EmailAliasRealAddressTypeFilterInputEntity

/**
 * Filter for messages with a real address type matching one of the specified values.
 *
 * @property oneOf The [List] of [EmailAliasEntityRealAddressType] values to match.
 */
internal data class OneOfRealAddressTypeFilterEntity(
    val oneOf: List<EmailAliasEntityRealAddressType>,
) : EmailAliasRealAddressTypeFilterInputEntity

/**
 * Filter for messages with a real address type not equal to the specified value.
 *
 * @property notEqual The [EmailAliasEntityRealAddressType] value to exclude.
 */
internal data class NotEqualRealAddressTypeFilterEntity(
    val notEqual: EmailAliasEntityRealAddressType,
) : EmailAliasRealAddressTypeFilterInputEntity

/**
 * Filter for messages with a real address type not matching any of the specified values.
 *
 * @property notOneOf The [List] of [EmailAliasEntityRealAddressType] values to exclude.
 */
internal data class NotOneOfRealAddressTypeFilterEntity(
    val notOneOf: List<EmailAliasEntityRealAddressType>,
) : EmailAliasRealAddressTypeFilterInputEntity

internal data class EmailAliasFilterInputEntity(
    val status: EmailAliasStatusFilterInputEntity? = null,
    val addressType: EmailAliasRealAddressTypeFilterInputEntity? = null,
)
