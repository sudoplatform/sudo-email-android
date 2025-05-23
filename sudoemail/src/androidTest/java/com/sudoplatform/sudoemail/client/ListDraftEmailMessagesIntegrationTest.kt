/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.TestData
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.transformers.DraftEmailMessageTransformer
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.AndroidSQLiteStore
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of [SudoEmailClient.listDraftEmailMessages].
 */
@RunWith(AndroidJUnit4::class)
class ListDraftEmailMessagesIntegrationTest : BaseIntegrationTest() {
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()

    @Before
    fun setup() {
        sudoClient.reset()
        sudoClient.generateEncryptionKey()
    }

    @After
    fun teardown() = runTest {
        emailAddressList.map { emailClient.deprovisionEmailAddress(it.id) }
        sudoList.map { sudoClient.deleteSudo(it) }
        sudoClient.reset()
    }

    @Test
    fun listDraftEmailMessagesShouldReturnEmptyListWhenDraftMessagesNotFound() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val result = emailClient.listDraftEmailMessages()

        result.size shouldBe 0
    }

    @Test
    fun listDraftEmailMessagesShouldReturnListOfDraftMessages() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        for (i in 0 until 2) {
            val rfc822Data = Rfc822MessageDataProcessor(context).encodeToInternetMessageData(
                from = emailAddress.emailAddress,
                to = listOf(emailAddress.emailAddress),
                subject = "Draft $i",
            )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
            emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
        }

        val result = emailClient.listDraftEmailMessages()

        result.size shouldBe 2

        result.forEach { item ->
            item.emailAddressId shouldBe emailAddress.id

            val parsedMessage = Rfc822MessageDataProcessor(context).parseInternetMessageData(item.rfc822Data)
            parsedMessage.to shouldContain emailAddress.emailAddress
            parsedMessage.from shouldContain emailAddress.emailAddress
            parsedMessage.subject shouldContain "Draft"
        }
    }

    @Test
    fun listDraftEmailMessagesShouldListMessagesForEachAddress() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val emailAddress2 = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress2 shouldNotBe null
        emailAddressList.add(emailAddress2)

        for (i in 0 until 2) {
            val rfc822Data = Rfc822MessageDataProcessor(context).encodeToInternetMessageData(
                from = emailAddress.emailAddress,
                to = listOf(emailAddress.emailAddress),
                subject = "Draft $i",
            )
            val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress.id)
            emailClient.createDraftEmailMessage(createDraftEmailMessageInput)
        }

        val rfc822Data = Rfc822MessageDataProcessor(context).encodeToInternetMessageData(
            from = emailAddress2.emailAddress,
            to = listOf(emailAddress2.emailAddress),
            subject = "Another Draft",
        )
        val createDraftEmailMessageInput = CreateDraftEmailMessageInput(rfc822Data, emailAddress2.id)
        emailClient.createDraftEmailMessage(createDraftEmailMessageInput)

        val result = emailClient.listDraftEmailMessages()

        result.size shouldBe 3
    }

    @Test
    fun listDraftEmailMessagesShouldMigrateMessagesFromTransientBucket() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val config = SudoEmailClient.readConfiguration(context, logger)

        val s3TransientClient = DefaultS3Client(
            context,
            userClient,
            region = config.region,
            bucket = config.transientBucket,
            logger,
        )

        val draftIds = mutableListOf<String>()
        for (i in 0 until 2) {
            val rfc822Data = Rfc822MessageDataProcessor(context).encodeToInternetMessageData(
                from = emailAddress.emailAddress,
                to = listOf(emailAddress.emailAddress),
                subject = "Draft $i",
            )
            val serviceKeyManager = DefaultServiceKeyManager(
                keyRingServiceName = "sudo-email",
                userClient = userClient!!,
                keyManager = KeyManagerFactory(context!!).createAndroidKeyManager(
                    SudoEmailClient.DEFAULT_KEY_NAMESPACE,
                    AndroidSQLiteStore.DEFAULT_DATABASE_NAME,
                ),
            )
            val symmetricKeyId = serviceKeyManager.getCurrentSymmetricKeyId() ?: throw InternalError("Could not find symmetric key id")
            val id = UUID.randomUUID().toString()
            draftIds.add(id)
            val s3Key = "email/${emailAddress.id}/draft/$id"
            val metadataObject = mapOf(
                "keyId" to symmetricKeyId,
                "algorithm" to SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
            )
            val sealingService = DefaultSealingService(
                deviceKeyManager = serviceKeyManager,
                logger = logger,
            )
            val uploadData = DraftEmailMessageTransformer.toEncryptedAndEncodedRfc822Data(
                sealingService,
                rfc822Data,
                symmetricKeyId,
            )
            s3TransientClient.upload(uploadData, s3Key, metadataObject)
        }
        draftIds.sort()
        val result = emailClient.listDraftEmailMessages()

        result.size shouldBe 2
        result.sortedBy { it.id }.forEachIndexed { index, draftEmailMessage ->
            draftEmailMessage.id shouldBe draftIds[index]
            draftEmailMessage.emailAddressId shouldBe emailAddress.id
            val parsedMessage = Rfc822MessageDataProcessor(context).parseInternetMessageData(draftEmailMessage.rfc822Data)
            parsedMessage.to shouldContain emailAddress.emailAddress
            parsedMessage.from shouldContain emailAddress.emailAddress
            parsedMessage.subject shouldContain "Draft"
        }

        val transientBucketItems = s3TransientClient.list("email/${emailAddress.id}/draft")
        transientBucketItems.size shouldBe 0
    }
}
