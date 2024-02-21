/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.TestData
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailAddressPublicInfo
import com.sudoplatform.sudoemail.types.inputs.LookupEmailAddressesPublicInfoInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.beInstanceOf
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.lookupEmailAddressesPublicInfo].
 */
@RunWith(AndroidJUnit4::class)
class LookupEmailAddressesPublicInfoIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

    @Before
    fun setup() {
        sudoClient.reset()
        sudoClient.generateEncryptionKey()
    }

    @After
    fun teardown() = runBlocking {
        emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        sudoList.map { sudoClient.deleteSudo(it) }
        sudoClient.reset()
    }

    private fun setupEmailAddress() = runBlocking {
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)

        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val aliasInput = "John Doe"
        val emailAddress = provisionEmailAddress(emailClient, ownershipProof, alias = aliasInput)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)
    }

    @Test
    fun lookupEmailAddressesPublicInfoShouldReturnEmailAddressPublicInfoResult(): Unit = runBlocking {
        setupEmailAddress()

        val inputEmailAddress = emailAddressList[0].emailAddress
        val input = LookupEmailAddressesPublicInfoInput(listOf(inputEmailAddress))
        val retrievedPublicInfo = emailClient.lookupEmailAddressesPublicInfo(input)

        retrievedPublicInfo shouldBe beInstanceOf<List<EmailAddressPublicInfo>>()
        retrievedPublicInfo.count() shouldBe 1
        with(retrievedPublicInfo[0]) {
            emailAddress shouldBe inputEmailAddress
            keyId.shouldBeInstanceOf<String>()
            publicKey.shouldBeInstanceOf<String>()
        }
    }

    @Test
    fun lookupEmailAddressesPublicInfoShouldReturnEmptyListWhenNoEmailAddressesFound(): Unit = runBlocking {
        setupEmailAddress()

        val inputEmailAddress = "fake@email.com"
        val input = LookupEmailAddressesPublicInfoInput(listOf(inputEmailAddress))
        val retrievedPublicInfo = emailClient.lookupEmailAddressesPublicInfo(input)

        retrievedPublicInfo shouldBe beInstanceOf<List<EmailAddressPublicInfo>>()
        retrievedPublicInfo.count() shouldBe 0
    }

    fun lookupEmailAddressesPublicInfoShouldThrowLimitExceededException(): Unit = runBlocking {
        val inputEmailAddress = "fake@email.com"
        val inputEmailAddresses = List(51) { inputEmailAddress }
        val input = LookupEmailAddressesPublicInfoInput(inputEmailAddresses)

        shouldThrow<SudoEmailClient.EmailAddressException.LimitExceededException> {
            emailClient.lookupEmailAddressesPublicInfo(input)
        }
    }
}
