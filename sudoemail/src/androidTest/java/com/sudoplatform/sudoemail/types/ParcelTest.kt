/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.types.transformers.EmailMessageTransformer
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Test the public facing data classes can be written into and read from a [Bundle].
 */
@RunWith(AndroidJUnit4::class)
class ParcelTest {

    @Test
    fun parcellableClassesCanBeParcelledAndUnparcelled() {
        val rfc822Address = "example@sudoplatform.com"
        val emailAddress = EmailMessageTransformer.toEmailAddress(rfc822Address)
            ?: throw AssertionError("Parsing should not fail")
        val owner = Owner("id", "issuer")
        val emailFolder = EmailFolder(
            "folderId",
            "owner",
            owners = listOf(owner),
            "emailAddressId",
            "INBOX",
            0.0,
            0,
            1,
            createdAt = Date(42L),
            updatedAt = Date(43L),
            customFolderName = null,
        )
        val provisionedEmailAddress = EmailAddress(
            id = "emailAddressId",
            owner = "owner",
            owners = listOf(owner),
            emailAddress = rfc822Address,
            size = 0.0,
            numberOfEmailMessages = 0,
            version = 1,
            createdAt = Date(42L),
            updatedAt = Date(43L),
            null,
            null,
            listOf(emailFolder),
        )
        val emailMessage = EmailMessage(
            id = "id",
            owner = "owner",
            owners = listOf(owner),
            emailAddressId = "emailAddressId",
            folderId = "folderId",
            previousFolderId = "previousFolderId",
            seen = false,
            repliedTo = false,
            forwarded = false,
            direction = Direction.OUTBOUND,
            state = State.SENT,
            version = 1,
            sortDate = Date(42L),
            from = listOf(emailAddress),
            to = listOf(emailAddress),
            createdAt = Date(42L),
            updatedAt = Date(43L),
            size = 0.0,
            hasAttachments = false,
            encryptionStatus = EncryptionStatus.UNENCRYPTED,
        )

        val bundle = Bundle()
        bundle.putParcelable("owner", owner)
        bundle.putParcelable("provisionedEmailAddress", provisionedEmailAddress)
        bundle.putParcelable("emailMessage", emailMessage)
        bundle.putParcelable("emailAddress", emailAddress)

        val bundleTwo = Bundle(bundle)
        val ownerTwo = bundleTwo.getParcelable<Owner>("owner")
        val emailAddressTwo = bundleTwo.getParcelable<EmailAddress>("provisionedEmailAddress")
        val emailMessageTwo = bundleTwo.getParcelable<EmailMessage>("emailMessage")
        val addressTwo = bundleTwo.getParcelable<EmailMessage.EmailAddress>("emailAddress")

        ownerTwo shouldBe owner
        emailAddressTwo shouldBe provisionedEmailAddress
        emailMessageTwo shouldBe emailMessage
        addressTwo shouldBe emailAddress
    }
}
