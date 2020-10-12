/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
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
import java.lang.AssertionError
import java.util.Date

/**
 * Test the public facing data classes can be written into and read from a [Bundle].
 *
 * @since 2020-08-27
 */
@RunWith(AndroidJUnit4::class)
class ParcelTest {

    @Test
    fun parcellableClassesCanBeParcelledAndUnparcelled() {

        val rfc822Address = "example@sudoplatform.com"
        val emailAddress = EmailMessageTransformer.toEmailAddress(rfc822Address)
            ?: throw AssertionError("Parsing should not fail")
        val owner = Owner("id", "issuer")
        val provisionedEmailAddress = EmailAddress(
            id = "emailAddressId",
            emailAddress = rfc822Address,
            userId = "userId",
            sudoId = "sudoId",
            owners = listOf(owner),
            createdAt = Date(42L),
            updatedAt = Date(43L)
        )
        val emailMessage = EmailMessage(
            messageId = "messageId",
            userId = "userId",
            sudoId = "sudoId",
            emailAddressId = "emailAddressId",
            direction = EmailMessage.Direction.OUTBOUND,
            state = EmailMessage.State.SENT,
            from = listOf(emailAddress),
            to = listOf(emailAddress),
            createdAt = Date(42L),
            updatedAt = Date(43L),
            id = "id",
            algorithm = "algorithm",
            keyId = "keyId"
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
