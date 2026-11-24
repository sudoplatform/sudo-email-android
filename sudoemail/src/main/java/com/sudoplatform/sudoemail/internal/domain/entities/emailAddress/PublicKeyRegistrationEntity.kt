/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailAddress

/**
 * One registration of a public key with the email service: the key, and the key ring of the email
 * address or email alias it is registered for. A key shared by several email addresses or email
 * aliases has one registration for each of them.
 *
 * @property keyId [String] Identifier of the key pair the public key belongs to.
 * @property keyRingId [String] Identifier of the key ring of the email address or email alias the
 *  key is registered for.
 */
internal data class PublicKeyRegistrationEntity(
    val keyId: String,
    val keyRingId: String,
)
