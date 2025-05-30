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
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.transformers.DraftEmailMessageTransformer
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.AndroidSQLiteStore
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of [SudoEmailClient.getDraftEmailMessage].
 */
@RunWith(AndroidJUnit4::class)
class GetDraftEmailMessageIntegrationTest : BaseIntegrationTest() {
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
    fun getDraftEmailMessageShouldThrowErrorIfSenderEmailAddressNotFound() = runTest {
        val mockDraftId = UUID.randomUUID()
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)
        val input = GetDraftEmailMessageInput(mockDraftId.toString(), "bogusEmailId")
        shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
            emailClient.getDraftEmailMessage(input)
        }
    }

    @Test
    fun getDraftEmailMessageShouldThrowErrorIfDraftMessageNotFound() = runTest {
        val mockDraftId = UUID.randomUUID()
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)
        val input = GetDraftEmailMessageInput(mockDraftId.toString(), emailAddress.id)
        shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
            emailClient.getDraftEmailMessage(input)
        }
    }

    @Test
    fun getDraftEmailMessageShouldReturnProperMessage() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val rfc822Data = Rfc822MessageDataProcessor(context).encodeToInternetMessageData(
            from = emailAddress.emailAddress,
            to = listOf(emailAddress.emailAddress),
            subject = "Test Draft",
        )

        val createDraftInput = CreateDraftEmailMessageInput(
            rfc822Data = rfc822Data,
            senderEmailAddressId = emailAddress.id,
        )

        val draftId = emailClient.createDraftEmailMessage(createDraftInput)

        val input = GetDraftEmailMessageInput(draftId, emailAddress.id)
        val draftEmailMessage = emailClient.getDraftEmailMessage(input)

        draftEmailMessage.id shouldBe draftId
        draftEmailMessage.emailAddressId shouldBe emailAddress.id
        val parsedMessage = Rfc822MessageDataProcessor(context).parseInternetMessageData(draftEmailMessage.rfc822Data)

        parsedMessage.to shouldContain emailAddress.emailAddress
        parsedMessage.from shouldContain emailAddress.emailAddress
        parsedMessage.subject shouldBe "Test Draft"
    }

    @Test
    fun getDraftEmailMessageShouldMigrateMessagesWithLegacyKeyIdMetadataKey() = runTest {
        val sudo = createSudo(TestData.sudo)
        sudo shouldNotBe null
        sudoList.add(sudo)
        val ownershipProof = getOwnershipProof(sudo)
        ownershipProof shouldNotBe null

        val emailAddress = provisionEmailAddress(emailClient, ownershipProof)
        emailAddress shouldNotBe null
        emailAddressList.add(emailAddress)

        val config = SudoEmailClient.readConfiguration(context, logger)

        val s3EmailClient = DefaultS3Client(
            context,
            userClient,
            region = config.region,
            bucket = config.emailBucket,
            logger,
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
        val rfc822Data = Rfc822MessageDataProcessor(context).encodeToInternetMessageData(
            from = emailAddress.emailAddress,
            to = listOf(emailAddress.emailAddress),
            subject = "Test Draft",
        )
        val draftId = UUID.randomUUID().toString()
        val s3Key = "email/${emailAddress.id}/draft/$draftId"
        val metadataObject = mapOf(
            // legacy key name
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
        s3EmailClient.upload(uploadData, s3Key, metadataObject)

        val input = GetDraftEmailMessageInput(draftId, emailAddress.id)
        val draftEmailMessage = emailClient.getDraftEmailMessage(input)

        draftEmailMessage.id shouldBe draftId
        draftEmailMessage.emailAddressId shouldBe emailAddress.id
        val parsedMessage = Rfc822MessageDataProcessor(context).parseInternetMessageData(draftEmailMessage.rfc822Data)

        parsedMessage.to shouldContain emailAddress.emailAddress
        parsedMessage.from shouldContain emailAddress.emailAddress
        parsedMessage.subject shouldBe "Test Draft"

        val metadata = s3EmailClient.getObjectMetadata("email/${emailAddress.id}/draft/$draftId")
        // Legacy key name
        logger.info(metadata.userMetadata.toString())
        metadata.userMetadata["keyId"] shouldBe null
        // Correct key name
        metadata.userMetadata["key-id"] shouldBe symmetricKeyId
    }
}
