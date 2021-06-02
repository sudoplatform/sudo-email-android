/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoemail.subscription.EmailMessageSubscriber
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.EmailMessage.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.inputs.filters.filterEmailMessagesBy
import com.sudoplatform.sudoemail.util.Rfc822MessageFactory
import com.sudoplatform.sudoemail.util.Rfc822MessageParser
import com.sudoplatform.sudoemail.util.SimplifiedEmailMessage
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Test the operation of the [SudoEmailClient].
 *
 * @since 2020-08-04
 */
@RunWith(AndroidJUnit4::class)
class SudoEmailClientIntegrationTest : BaseIntegrationTest() {

    private val verbose = false
    private val logLevel = if (verbose) LogLevel.VERBOSE else LogLevel.INFO
    private val logger = Logger("email-test", AndroidUtilsLogDriver(logLevel))

    private lateinit var emailClient: SudoEmailClient

    @Before
    fun init() {
        Timber.plant(Timber.DebugTree())

        if (verbose) {
            java.util.logging.Logger.getLogger("com.amazonaws").level = java.util.logging.Level.FINEST
            java.util.logging.Logger.getLogger("org.apache.http").level = java.util.logging.Level.FINEST
        }

        emailClient = SudoEmailClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setSudoProfilesClient(sudoClient)
            .setLogger(logger)
            .build()
    }

    @After
    fun fini() = runBlocking<Unit> {
        deleteAllSudos()
        emailClient.close()
        Timber.uprootAll()
    }

    @Test
    fun shouldThrowIfRequiredItemsNotProvidedToBuilder() {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        // All required items not provided
        shouldThrow<NullPointerException> {
            SudoEmailClient.builder().build()
        }

        // Context not provided
        shouldThrow<NullPointerException> {
            SudoEmailClient.builder()
                .setSudoUserClient(userClient)
                .setSudoProfilesClient(sudoClient)
                .build()
        }

        // SudoUserClient not provided
        shouldThrow<NullPointerException> {
            SudoEmailClient.builder()
                .setContext(context)
                .setSudoProfilesClient(sudoClient)
                .build()
        }

        // SudoProfilesClient not provided
        shouldThrow<NullPointerException> {
            SudoEmailClient.builder()
                .setContext(context)
                .setSudoUserClient(userClient)
                .build()
        }
    }

    @Test
    fun shouldNotThrowIfTheRequiredItemsAreProvidedToBuilder() {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        SudoEmailClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setSudoProfilesClient(sudoClient)
            .build()
    }

    @Test
    fun shouldNotThrowIfAllItemsAreProvidedToBuilder() {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        val appSyncClient = ApiClientManager.getClient(context, userClient)

        SudoEmailClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setSudoProfilesClient(sudoClient)
            .setAppSyncClient(appSyncClient)
            .setKeyManager(keyManager)
            .setLogger(logger)
            .build()
    }

    private suspend fun getEmailDomains(): List<String> {
        return emailClient.getSupportedEmailDomains(CachePolicy.REMOTE_ONLY)
    }

    @Test
    fun provisionEmailAddressShouldReturnEmailAddress() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val emailAddressInput = UUID.randomUUID().toString() + "@" + emailDomains.first()
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null

        val emailAddress = emailClient.provisionEmailAddress(emailAddressInput, sudo.id!!)
        with(emailAddress) {
            id shouldNotBe null
            emailAddress.emailAddress shouldBe emailAddressInput
            userId shouldBe userClient.getSubject()
            sudoId shouldBe sudo.id
            owners.first().id shouldBe sudo.id
            owners.first().issuer shouldBe "sudoplatform.sudoservice"
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
        }

        // Should only be able to create one email address per Sudo
        val emailAddressInputTwo = UUID.randomUUID().toString() + "@" + emailDomains.first()
        shouldThrow<SudoEmailClient.EmailAddressException.InsufficientEntitlementsException> {
            emailClient.provisionEmailAddress(emailAddressInputTwo, sudo.id!!)
        }
    }

    @Test
    fun provisionEmailAddressShouldFailWithBogusInput() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null

        var emailAddressInput = UUID.randomUUID().toString() + "@gmail.com"
        shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
            emailClient.provisionEmailAddress(emailAddressInput, sudo.id!!)
        }

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        emailAddressInput = "@" + emailDomains.first()
        shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
            emailClient.provisionEmailAddress(emailAddressInput, sudo.id!!)
        }
    }

    @Test
    fun provisionEmailAddressShouldThrowWithExistingAddressExceptionAfterDeprovisioning() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val emailAddressInput = UUID.randomUUID().toString() + "@" + emailDomains.first()
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null

        val emailAddress = emailClient.provisionEmailAddress(emailAddressInput, sudo.id!!)

        emailAddress shouldNotBe null

        val deprovisionedEmailAddress = emailClient.deprovisionEmailAddress(emailAddress.id)

        deprovisionedEmailAddress shouldNotBe null

        // Attempt to provision with an already de-provisioned email address
        shouldThrow<SudoEmailClient.EmailAddressException.UnavailableEmailAddressException> {
            emailClient.provisionEmailAddress(emailAddressInput, sudo.id!!)
        }
    }

    @Test
    fun deprovisionEmailAddressShouldReturnEmailAddressResult() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val emailAddressInput = UUID.randomUUID().toString() + "@" + emailDomains.first()
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null

        val emailAddress = emailClient.provisionEmailAddress(emailAddressInput, sudo.id!!)

        emailAddress shouldNotBe null

        val deprovisionedEmailAddress = emailClient.deprovisionEmailAddress(emailAddress.id)

        deprovisionedEmailAddress.id shouldBe emailAddress.id
        deprovisionedEmailAddress.emailAddress shouldBe emailAddress.emailAddress
        deprovisionedEmailAddress.userId shouldBe emailAddress.userId
        deprovisionedEmailAddress.sudoId shouldBe emailAddress.sudoId
        deprovisionedEmailAddress.owners shouldBe emailAddress.owners
        deprovisionedEmailAddress.createdAt.time shouldBeGreaterThan 0L
        deprovisionedEmailAddress.updatedAt.time shouldBeGreaterThan 0L
    }

    @Test
    fun deprovisionEmailAddressShouldThrowWithNonExistentAddress() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val emailAddressInput = UUID.randomUUID().toString() + "@" + emailDomains.first()
        shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
            emailClient.deprovisionEmailAddress(emailAddressInput)
        }
    }

    @Test
    fun getEmailAddressShouldReturnEmailAddressResult() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val emailAddressInput = UUID.randomUUID().toString() + "@" + emailDomains.first()
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null

        val emailAddress = emailClient.provisionEmailAddress(emailAddressInput, sudo.id!!)
        emailAddress shouldNotBe null

        val retrievedEmailAddress = emailClient.getEmailAddress(emailAddress.id)
            ?: throw AssertionError("should not be null")

        retrievedEmailAddress.id shouldBe emailAddress.id
        retrievedEmailAddress.emailAddress shouldBe emailAddress.emailAddress
        retrievedEmailAddress.userId shouldBe emailAddress.userId
        retrievedEmailAddress.sudoId shouldBe emailAddress.sudoId
        retrievedEmailAddress.owners shouldBe emailAddress.owners
        retrievedEmailAddress.createdAt.time shouldBeGreaterThan 0L
        retrievedEmailAddress.updatedAt.time shouldBeGreaterThan 0L
    }

    @Test
    fun getEmailAddressShouldReturnNullForNonExistentAddress() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val emailAddressInput = UUID.randomUUID().toString() + "@" + emailDomains.first()

        // when
        val retrievedEmailAddress = emailClient.getEmailAddress(emailAddressInput)

        // then
        retrievedEmailAddress shouldBe null
    }

    @Test
    fun listEmailAddressesShouldReturnSingleEmailAddressListOutputResult() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val emailAddressInput = UUID.randomUUID().toString() + "@" + emailDomains.first()
        val sudo = sudoClient.createSudo(TestData.sudo)
        sudo shouldNotBe null

        val emailAddress = emailClient.provisionEmailAddress(emailAddressInput, sudo.id!!)
        emailAddress shouldNotBe null

        val listEmailAddresses = emailClient.listEmailAddresses()
        listEmailAddresses.items.isEmpty() shouldBe false
        listEmailAddresses.items.size shouldBe 1
        listEmailAddresses.nextToken shouldBe null

        val emailAddresses = listEmailAddresses.items
        emailAddresses[0].id shouldBe emailAddress.id
        emailAddresses[0].emailAddress shouldBe emailAddress.emailAddress
        emailAddresses[0].userId shouldBe emailAddress.userId
        emailAddresses[0].sudoId shouldBe emailAddress.sudoId
        emailAddresses[0].owners shouldBe emailAddress.owners
        emailAddresses[0].createdAt.time shouldBeGreaterThan 0L
        emailAddresses[0].updatedAt.time shouldBeGreaterThan 0L
    }

    @Test
    fun completeFlowShouldSucceed() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        // Add a subscriber for emails
        val createdEmailMessages = mutableListOf<EmailMessage>()
        val deletedEmailMessages = mutableListOf<EmailMessage>()
        val subscriptionId = UUID.randomUUID().toString()
        emailClient.subscribeToEmailMessages(
            subscriptionId,
            onEmailMessageCreated = {
                createdEmailMessages.add(it)
            },
            onEmailMessageDeleted = {
                deletedEmailMessages.add(it)
            }
        )

        val sudo = sudoClient.createSudo(TestData.sudo)

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val emailAddress = emailClient.provisionEmailAddress(
            UUID.randomUUID().toString() + "@" + emailDomains.first(),
            sudo.id!!
        )

        emailClient.listEmailAddresses().items.first().emailAddress shouldBe emailAddress.emailAddress

        val messageSubject = "Hello ${UUID.randomUUID()}"
        val body = buildString {
            for (i in 0 until 500) {
                appendLine("Body of message ${UUID.randomUUID()}")
            }
        }
        val rfc822Data = Rfc822MessageFactory.makeRfc822Data(
            from = emailAddress.emailAddress,
            to = emailAddress.emailAddress,
            subject = messageSubject,
            body = body
        )

        val messageCount = 10
        val sentEmailIds = mutableSetOf<String>()
        for (i in 0 until messageCount) {
            val emailId = emailClient.sendEmailMessage(rfc822Data, emailAddress.id)
            emailId.isBlank() shouldBe false
            sentEmailIds.add(emailId)
        }

        // Wait for all the messages to arrive
        Awaitility.await()
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    val listOutput = emailClient.listEmailMessages(limit = messageCount * 2)
                    listOutput.items.size == messageCount * 2
                }
            }

        // Make sure the messages can be listed with paging and filtering
        val pageSize = Math.max(1, messageCount / 5)
        var nextToken: String? = null
        val outgoingMessages = mutableSetOf<EmailMessage>()
        while (true) {
            val listOutput = emailClient.listEmailMessages(limit = pageSize, nextToken = nextToken) {
                filterEmailMessagesBy {
                    allOf(
                        direction equalTo outbound,
                        state equalTo sent
                    )
                }
            }
            outgoingMessages.addAll(listOutput.items)
            nextToken = listOutput.nextToken
            if (nextToken == null) {
                break
            }
        }

        val incomingMessages = mutableSetOf<EmailMessage>()
        nextToken = null
        while (true) {
            val listOutput = emailClient.listEmailMessages(limit = pageSize, nextToken = nextToken) {
                filterEmailMessagesBy {
                    allOf(
                        direction equalTo inbound,
                        state equalTo received
                    )
                }
            }
            incomingMessages.addAll(listOutput.items)
            nextToken = listOutput.nextToken
            if (nextToken == null) {
                break
            }
        }

        outgoingMessages.forEach { messageOut ->
            with(messageOut) {
                messageId.isBlank() shouldBe false
                userId shouldBe emailAddress.userId
                sudoId shouldBe emailAddress.sudoId
                emailAddressId shouldBe emailAddress.id
                from.shouldContainExactlyInAnyOrder(EmailAddress(emailAddress.emailAddress))
                to.shouldContainExactlyInAnyOrder(EmailAddress(emailAddress.emailAddress))
                subject shouldBe messageSubject
                direction shouldBe EmailMessage.Direction.OUTBOUND
                state shouldBe EmailMessage.State.SENT
                createdAt.time shouldBeGreaterThan 0L
                updatedAt.time shouldBeGreaterThan 0L

                val rfc822Data1 = emailClient.getEmailMessageRfc822Data(messageId)
                rfc822Data1 shouldNotBe null

                val simplifiedMessage = Rfc822MessageParser.parseRfc822Data(rfc822Data1!!)
                checkSimplifiedMessage(this, body, simplifiedMessage)
            }
        }
        sentEmailIds.shouldContainExactlyInAnyOrder(outgoingMessages.map { it.messageId })

        incomingMessages.forEach { messageIn ->
            with(messageIn) {
                messageId.isBlank() shouldBe false
                userId shouldBe emailAddress.userId
                sudoId shouldBe emailAddress.sudoId
                emailAddressId shouldBe emailAddress.id
                from.shouldContainExactlyInAnyOrder(EmailAddress(emailAddress.emailAddress))
                to.shouldContainExactlyInAnyOrder(EmailAddress(emailAddress.emailAddress))
                subject shouldBe messageSubject
                direction shouldBe EmailMessage.Direction.INBOUND
                state shouldBe EmailMessage.State.RECEIVED
                createdAt.time shouldBeGreaterThan 0L
                updatedAt.time shouldBeGreaterThan 0L

                val rfc822Data2 = emailClient.getEmailMessageRfc822Data(messageId)
                rfc822Data2 shouldNotBe null

                val simplifiedMessage = Rfc822MessageParser.parseRfc822Data(rfc822Data2!!)
                checkSimplifiedMessage(this, body, simplifiedMessage)
            }
        }

        outgoingMessages.clear()
        incomingMessages.clear()

        // Wait for the subscription messages to arrive
        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS)
            .until {
                createdEmailMessages.size == messageCount * 2
            }

        // Make sure the subscription API provided notification of all the new messages
        outgoingMessages.addAll(createdEmailMessages.filter { it.direction == EmailMessage.Direction.OUTBOUND })
        incomingMessages.addAll(createdEmailMessages.filter { it.direction == EmailMessage.Direction.INBOUND })
        createdEmailMessages.size shouldBe messageCount * 2
        outgoingMessages.size shouldBe messageCount
        incomingMessages.size shouldBe messageCount
        sentEmailIds.shouldContainExactlyInAnyOrder(outgoingMessages.map { it.messageId })

        // Delete all the outgoing messages and check that we receive notifications
        deletedEmailMessages.isEmpty() shouldBe true
        outgoingMessages.forEach { msg ->
            emailClient.deleteEmailMessage(msg.messageId) shouldBe msg.messageId
        }
        // Wait for the notifications to arrive
        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS)
            .until {
                deletedEmailMessages.size == messageCount
            }
        deletedEmailMessages.forEach { msg ->
            msg.direction shouldBe EmailMessage.Direction.OUTBOUND
        }
        deletedEmailMessages.clear()

        // Delete all the incoming messages and check that we receive notifications
        deletedEmailMessages.isEmpty() shouldBe true
        incomingMessages.forEach { msg ->
            emailClient.deleteEmailMessage(msg.messageId) shouldBe msg.messageId
        }
        // Wait for the notifications to arrive
        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS)
            .until {
                deletedEmailMessages.size == messageCount
            }
        deletedEmailMessages.forEach { msg ->
            msg.direction shouldBe EmailMessage.Direction.INBOUND
        }
    }

    private fun checkSimplifiedMessage(message: EmailMessage, body: String, simplifiedMessage: SimplifiedEmailMessage) {
        simplifiedMessage.to.shouldContainExactlyInAnyOrder(message.to.map { it.address })
        simplifiedMessage.from.shouldContainExactlyInAnyOrder(message.from.map { it.address })
        simplifiedMessage.cc.shouldContainExactlyInAnyOrder(message.cc.map { it.address })
        simplifiedMessage.bcc.shouldContainExactlyInAnyOrder(message.bcc.map { it.address })
        simplifiedMessage.subject shouldBe message.subject
        fixNl(simplifiedMessage.body) shouldBe fixNl(body)
    }

    private fun fixNl(s: String): String {
        return s.replace("\r\n", "\n")
    }

    @Test
    fun sendEmailShouldThrowWhenBogusEmailSent() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val sudo = sudoClient.createSudo(TestData.sudo)

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val emailAddress = emailClient.provisionEmailAddress(
            UUID.randomUUID().toString() + "@" + emailDomains.first(),
            sudo.id!!
        )

        emailClient.listEmailAddresses().items.first().emailAddress shouldBe emailAddress.emailAddress

        shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
            emailClient.sendEmailMessage(ByteArray(42), emailAddress.id)
        }
    }

    @Test
    fun sendEmailShouldThrowWhenBogusRecipientAddressUsed() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val sudo = sudoClient.createSudo(TestData.sudo)

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val emailAddress = emailClient.provisionEmailAddress(
            UUID.randomUUID().toString() + "@" + emailDomains.first(),
            sudo.id!!
        )

        val rfc822Data = Rfc822MessageFactory.makeRfc822Data(
            from = emailAddress.emailAddress,
            to = "bogusEmailAddress"
        )

        shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
            emailClient.sendEmailMessage(rfc822Data, emailAddress.id)
        }
    }

    @Test
    fun sendEmailShouldThrowWhenBogusSenderAddressUsed() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val sudo = sudoClient.createSudo(TestData.sudo)

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val emailAddress = emailClient.provisionEmailAddress(
            UUID.randomUUID().toString() + "@" + emailDomains.first(),
            sudo.id!!
        )

        var rfc822Data = Rfc822MessageFactory.makeRfc822Data(
            from = emailAddress.emailAddress,
            to = emailAddress.emailAddress
        )
        shouldThrow<SudoEmailClient.EmailMessageException.UnauthorizedAddressException> {
            emailClient.sendEmailMessage(rfc822Data, "bogusEmailAddressId")
        }

        rfc822Data = Rfc822MessageFactory.makeRfc822Data(
            from = "bogusEmailAddress",
            to = emailAddress.emailAddress
        )
        shouldThrow<SudoEmailClient.EmailMessageException.InvalidMessageContentException> {
            emailClient.sendEmailMessage(rfc822Data, emailAddress.id)
        }
    }

    @Test
    fun getEmailMessageShouldReturnEmailMessageResult() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val sudo = sudoClient.createSudo(TestData.sudo)

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val emailAddress = emailClient.provisionEmailAddress(
            UUID.randomUUID().toString() + "@" + emailDomains.first(),
            sudo.id!!
        )

        emailClient.listEmailAddresses().items.first().emailAddress shouldBe emailAddress.emailAddress

        val messageSubject = "Hello ${UUID.randomUUID()}"
        val body = buildString {
            for (i in 1..100) {
                appendLine("Body of message ${UUID.randomUUID()}")
            }
        }
        val rfc822Data = Rfc822MessageFactory.makeRfc822Data(
            from = emailAddress.emailAddress,
            to = emailAddress.emailAddress,
            subject = messageSubject,
            body = body
        )
        val emailId = emailClient.sendEmailMessage(rfc822Data, emailAddress.id)
        emailId.isBlank() shouldBe false

        // Wait for all the messages to arrive
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    emailClient.getEmailMessage(emailId) != null
                }
            }

        val retrievedEmailMessage = emailClient.getEmailMessage(emailId)
            ?: throw AssertionError("should not be null")
        retrievedEmailMessage.messageId shouldBe emailId
        retrievedEmailMessage.userId shouldBe emailAddress.userId
        retrievedEmailMessage.sudoId shouldBe emailAddress.sudoId
        retrievedEmailMessage.emailAddressId shouldBe emailAddress.id
        retrievedEmailMessage.from.shouldContainExactlyInAnyOrder(EmailAddress(emailAddress.emailAddress))
        retrievedEmailMessage.to.shouldContainExactlyInAnyOrder(EmailAddress(emailAddress.emailAddress))
        retrievedEmailMessage.subject shouldBe messageSubject
        retrievedEmailMessage.seen shouldBe true
        retrievedEmailMessage.direction shouldBe EmailMessage.Direction.OUTBOUND
        retrievedEmailMessage.state shouldBe EmailMessage.State.SENT
        retrievedEmailMessage.createdAt.time shouldBeGreaterThan 0L
        retrievedEmailMessage.updatedAt.time shouldBeGreaterThan 0L
    }

    @Test
    fun getEmailMessageShouldReturnNullForNonExistentMessage() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        // when
        val retrievedEmailAddress = emailClient.getEmailMessage("nonExistentId")

        // then
        retrievedEmailAddress shouldBe null
    }

    @Test
    fun checkEmailAddressAvailabilityShouldSucceed() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val sudo = sudoClient.createSudo(TestData.sudo)

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val localParts = listOf(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString()
        )

        val emailAddresses = emailClient.checkEmailAddressAvailability(
            localParts = localParts,
            domains = emailDomains
        )
        emailAddresses.isEmpty() shouldBe false
        emailAddresses.forEach { address ->
            val parts = address.split("@")
            emailDomains shouldContain parts[1]

            // Provision it to prove it's real
            provision(address, sudo.id!!)
        }

        // Check without the domains
        emailClient.checkEmailAddressAvailability(localParts = localParts, domains = emptyList()).isEmpty() shouldBe true
    }

    private suspend fun provision(address: String, sudoId: String) {
        val provisionedAddress = emailClient.provisionEmailAddress(address, sudoId)
        provisionedAddress.emailAddress shouldBe address
        emailClient.deprovisionEmailAddress(provisionedAddress.id)
    }

    @Test
    fun checkEmailAddressAvailabilityWithBadInputShouldFail() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val emailDomains = getEmailDomains()
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val localParts = listOf(UUID.randomUUID().toString())

        shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
            emailClient.checkEmailAddressAvailability(localParts = localParts, domains = listOf("gmail.com"))
        }

        shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
            emailClient.checkEmailAddressAvailability(localParts = listOf("foo@gmail.com"), domains = emailDomains)
        }

        shouldThrow<SudoEmailClient.EmailAddressException.InvalidEmailAddressException> {
            emailClient.checkEmailAddressAvailability(localParts = listOf(""), domains = emailDomains)
        }
    }

    private val emailMessageSubscriber = object : EmailMessageSubscriber {
        override fun emailMessageCreated(emailMessage: EmailMessage) { }
        override fun emailMessageDeleted(emailMessage: EmailMessage) { }
        override fun connectionStatusChanged(state: EmailMessageSubscriber.ConnectionState) { }
    }

    @Test
    fun subscribeUnsubscribeShouldNotFail() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)
        emailClient.unsubscribeAll()

        emailClient.subscribeToEmailMessages("id", emailMessageSubscriber)
        emailClient.unsubscribeFromEmailMessages("id")

        emailClient.subscribeToEmailMessages(
            "id",
            onEmailMessageCreated = {},
            onEmailMessageDeleted = {}
        )

        emailClient.close()
    }

    @Test
    fun deleteEmailMessageShouldThrowForNonexistentMessage() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        // when
        shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
            emailClient.deleteEmailMessage("nonExistentId")
        }
    }
}
