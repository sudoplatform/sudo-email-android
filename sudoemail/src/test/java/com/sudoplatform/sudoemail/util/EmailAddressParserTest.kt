/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import com.sudoplatform.sudoemail.BaseTests
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test the operation of [EmailAddressParser] under Robolectric
 */
@RunWith(RobolectricTestRunner::class)
class EmailAddressParserTest : BaseTests() {
    @Test
    fun removeDisplayNameProperlyReturnsEmailAddressOnly() {
        val addressesWithWeirdDisplayNames =
            listOf(
                "\"John Doe\" <john.doe@example.com>",
                "\"Jane Smith\" <jane.smith@example.com>",
                "\"User Name\" <username@example.com>",
                "\"Dr. Élodie Müller\" <elodie.muller@example.com>",
                "\"Señor José-Luis García\" <jose.garcia@example.com>",
                "\"O'Connor, Sean\" <sean.oconnor@example.com>",
                "\"Miyuki 山田\" <miyuki.yamada@example.com>",
                "\"Prof. Jean-Pierre L'Écuyer\" <jp.lecuyer@example.com>",
                "\"Ms. Anna-Marie O’Neill\" <anna.oneill@example.com>",
                "\"李小龍 (Bruce Lee)\" <bruce.lee@example.com>",
                "\"D'Angelo, Mónica\" <monica.dangelo@example.com>",
                "\"Herr Günther Schütz\" <guenther.schuetz@example.com>",
                "\"M. François Dupont-Smith\" <francois.dupont-smith@example.com>",
                "\"Srta. María-José Pérez\" <maria.perez@example.com>",
                "\"Capt. Jack Sparrow!\" <jack.sparrow@example.com>",
                "\"Dr. med. Hans Grüber\" <hans.gruber@example.com>",
                "\"Mme. Chloé D'Amour\" <chloe.damour@example.com>",
                "\"Σωκράτης Παπαδόπουλος\" <sokratis.papadopoulos@example.com>",
                "\"Иван Иванович\" <ivan.ivanovich@example.com>",
                "\"Renée O’Malley-Quinn\" <renee.quinn@example.com>",
                "\"Mr. John Q. Public, Jr.\" <john.public@example.com>",
                "\"Dr. med. vet. Anna-Lena Weiß\" <anna.weiss@example.com>",
            )
        for (address in addressesWithWeirdDisplayNames) {
            val result = EmailAddressParser.removeDisplayName(address)
            result shouldBe address.substringAfter('<').substringBefore('>')
        }
    }

    @Test
    fun normalizeReturnsLowercaseAndValidFormat() {
        val cases =
            listOf(
                "John.Doe@Example.com" to "john.doe@example.com",
                "USER@EXAMPLE.COM" to "user@example.com",
                "MiXeD@ExAmPlE.CoM" to "mixed@example.com",
            )
        for ((input, expected) in cases) {
            EmailAddressParser.normalize(input) shouldBe expected
        }
    }

    @Test
    fun validateReturnsTrueForValidEmailsAndFalseForInvalid() {
        val validEmails =
            listOf(
                "john.doe@example.com",
                "user+tag@example.com",
                "user_name@example.com",
                "user.name@sub.example.com",
            )
        val invalidEmails =
            listOf(
                "plainaddress",
                "@missinglocal.org",
                "missingatsign.com",
                "user@.com",
                "user@com",
            )
        for (email in validEmails) {
            EmailAddressParser.validate(email) shouldBe true
        }
        for (email in invalidEmails) {
            EmailAddressParser.validate(email) shouldBe false
        }
    }

    @Test
    fun getDomainReturnsDomainPartOfEmail() {
        val cases =
            listOf(
                "john.doe@example.com" to "example.com",
                "USER@EXAMPLE.COM" to "example.com",
                "user@sub.example.com" to "sub.example.com",
            )
        for ((input, expected) in cases) {
            EmailAddressParser.getDomain(input) shouldBe expected
        }
    }
}
