/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import android.net.Uri
import android.os.StrictMode
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EmailFolder
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.InternetMessageFormatHeader
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.PartialEmailMessage
import com.sudoplatform.sudoemail.types.SendEmailMessageResult
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailFoldersForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesInput
import com.sudoplatform.sudoemail.types.inputs.ProvisionEmailAddressInput
import com.sudoplatform.sudoemail.types.inputs.SendEmailMessageInput
import com.sudoplatform.sudoentitlements.SudoEntitlementsClient
import com.sudoplatform.sudoentitlementsadmin.SudoEntitlementsAdminClient
import com.sudoplatform.sudoentitlementsadmin.types.Entitlement
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudoprofiles.Sudo
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.TESTAuthenticationProvider
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.awaitility.kotlin.has
import org.awaitility.kotlin.untilCallTo
import org.junit.AfterClass
import org.junit.BeforeClass
import timber.log.Timber
import java.util.UUID
import java.util.logging.Logger

/**
 * Test the operation of the [SudoEmailClient].
 */
abstract class BaseIntegrationTest {

    protected val toSimulatorAddress = "ooto@simulator.amazonses.com"
    protected val fromSimulatorAddress = "MAILER-DAEMON@amazonses.com"

    companion object {

        val context: Context = ApplicationProvider.getApplicationContext()

        private const val verbose = false
        private val logLevel = if (verbose) LogLevel.VERBOSE else LogLevel.INFO
        val logger =
            com.sudoplatform.sudologging.Logger("email-test", AndroidUtilsLogDriver(logLevel))

        @JvmStatic
        protected val entitlements = listOf(
            Entitlement("sudoplatform.sudo.max", "test", 3),
            Entitlement("sudoplatform.email.emailAddressUserEntitled", "test", 1),
            Entitlement("sudoplatform.email.emailStorageMaxPerUser", "test", 500000),
            Entitlement("sudoplatform.email.emailAddressMaxPerSudo", "test", 3),
            Entitlement("sudoplatform.email.emailStorageMaxPerEmailAddress", "test", 500000),
            Entitlement("sudoplatform.email.emailMessageSendUserEntitled", "test", 1),
            Entitlement("sudoplatform.email.emailMessageReceiveUserEntitled", "test", 1),
            Entitlement("sudoplatform.email.emailAddressMaxProvisionsExpendable", "test", 60),
        )

        @JvmStatic
        protected val userClient by lazy {
            SudoUserClient.builder(context)
                .setNamespace("eml-client-test")
                .build()
        }

        @JvmStatic
        protected val sudoClient by lazy {
            val containerURI = Uri.fromFile(context.cacheDir)
            SudoProfilesClient.builder(context, userClient, containerURI)
                .build()
        }

        @JvmStatic
        protected val entitlementsClient by lazy {
            SudoEntitlementsClient.builder()
                .setContext(context)
                .setSudoUserClient(userClient)
                .build()
        }

        @JvmStatic
        protected val entitlementsAdminClient by lazy {
            val adminApiKey = readArgument("ADMIN_API_KEY", "api.key")
            SudoEntitlementsAdminClient.builder(context, adminApiKey).build()
        }

        @JvmStatic
        protected val keyManager by lazy {
            KeyManagerFactory(context).createAndroidKeyManager("eml-client-test")
        }

        @JvmStatic
        protected lateinit var emailClient: SudoEmailClient

        private fun readTextFile(fileName: String): String {
            return context.assets.open(fileName).bufferedReader().use {
                it.readText().trim()
            }
        }

        @JvmStatic
        protected fun readArgument(argumentName: String, fallbackFileName: String?): String {
            val argumentValue =
                InstrumentationRegistry.getArguments().getString(argumentName)?.trim()
            if (argumentValue != null) {
                return argumentValue
            }
            if (fallbackFileName != null) {
                return readTextFile(fallbackFileName)
            }
            throw IllegalArgumentException("$argumentName property not found")
        }

        private suspend fun register() {
            userClient.isRegistered() shouldBe false

            val privateKey = readArgument("REGISTER_KEY", "register_key.private")
            val keyId = readArgument("REGISTER_KEY_ID", "register_key.id")

            val authProvider = TESTAuthenticationProvider(
                name = "eml-client-test",
                privateKey = privateKey,
                publicKey = null,
                keyManager = keyManager,
                keyId = keyId,
            )

            userClient.registerWithAuthenticationProvider(authProvider, "eml-client-test")
        }

        @JvmStatic
        protected suspend fun deregister() {
            userClient.deregister()
        }

        private suspend fun signIn() {
            userClient.signInWithKey()
        }

        private suspend fun registerAndSignIn() {
            userClient.isRegistered() shouldBe false
            register()
            userClient.isRegistered() shouldBe true
            signIn()
            userClient.isSignedIn() shouldBe true
        }

        @JvmStatic
        protected suspend fun registerSignInAndEntitle() {
            registerAndSignIn()
            val externalId = entitlementsClient.getExternalId()
            entitlementsAdminClient.applyEntitlementsToUser(externalId, entitlements)
            entitlementsClient.redeemEntitlements()
        }

        @BeforeClass
        @JvmStatic
        fun init() {
            Timber.plant(Timber.DebugTree())
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build(),
            )
            if (verbose) {
                Logger.getLogger("com.amazonaws").level = java.util.logging.Level.FINEST
                Logger.getLogger("org.apache.http").level = java.util.logging.Level.FINEST
            }

            sudoClient.reset()
            userClient.reset()

            sudoClient.generateEncryptionKey()
            emailClient = SudoEmailClient.builder()
                .setContext(context)
                .setSudoUserClient(userClient)
                .setLogger(logger)
                .build()

            runTest {
                registerSignInAndEntitle()
            }
        }

        @AfterClass
        @JvmStatic
        fun fini() = runTest {
            if (userClient.isRegistered()) {
                deregister()
            }
            emailClient.reset()
            sudoClient.reset()
            userClient.reset()
            Timber.uprootAll()
        }
    }

    protected suspend fun createSudo(sudoInput: Sudo): Sudo {
        return sudoClient.createSudo(sudoInput)
    }

    protected suspend fun getOwnershipProof(sudo: Sudo): String {
        return sudoClient.getOwnershipProof(sudo, "sudoplatform.email.email-address")
    }

    protected suspend fun getEmailDomains(client: SudoEmailClient): List<String> {
        return client.getSupportedEmailDomains(CachePolicy.REMOTE_ONLY)
    }

    protected fun generateSafeLocalPart(prefix: String? = null): String {
        val safePrefix = prefix ?: "safe-"
        val safeMap = mapOf(
            "-" to "-",
            "0" to "0",
            "1" to "1",
            "2" to "2",
            "3" to "3",
            "4" to "4",
            "5" to "5",
            "6" to "6",
            "7" to "7",
            "8" to "8",
            "9" to "9",
            "a" to "10",
            "b" to "11",
            "c" to "12",
            "d" to "13",
            "e" to "14",
            "f" to "15",
        )
        val pref = if (safePrefix.endsWith("-")) safePrefix else "$safePrefix-"
        val uuid = UUID.randomUUID().toString().map { it.toString() }
        val localPart = (pref + uuid.map { safeMap[it] }.joinToString(""))
        if (localPart.length > 64) {
            return localPart.substring(0, 63)
        }
        return localPart
    }

    protected suspend fun provisionEmailAddress(
        client: SudoEmailClient,
        ownershipProofToken: String,
        address: String? = null,
        alias: String? = null,
        keyId: String? = null,
        prefix: String? = null,
    ): EmailAddress {
        val emailDomains = client.getSupportedEmailDomains(CachePolicy.REMOTE_ONLY)
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val localPart = generateSafeLocalPart(prefix)
        val emailAddress = address ?: (localPart + "@" + emailDomains.first())
        val provisionInput = ProvisionEmailAddressInput(
            emailAddress = emailAddress,
            ownershipProofToken = ownershipProofToken,
            alias = alias,
            keyId = keyId,
        )
        return client.provisionEmailAddress(provisionInput)
    }

    protected suspend fun sendEmailMessage(
        client: SudoEmailClient,
        fromAddress: EmailAddress,
        toAddresses: List<EmailMessage.EmailAddress> = listOf(
            EmailMessage.EmailAddress(
                toSimulatorAddress,
            ),
        ),
        ccAddresses: List<EmailMessage.EmailAddress> = emptyList(),
        bccAddresses: List<EmailMessage.EmailAddress> = emptyList(),
        replyToAddresses: List<EmailMessage.EmailAddress> = emptyList(),
        body: String? = null,
        attachments: List<EmailAttachment> = emptyList(),
        inlineAttachments: List<EmailAttachment> = emptyList(),
        subject: String? = null,
    ): SendEmailMessageResult {
        val messageSubject = subject ?: "Hello ${UUID.randomUUID()}"
        val emailBody = body ?: buildString {
            for (i in 0 until 500) {
                appendLine("Body of message ${UUID.randomUUID()}")
            }
        }
        val emailMessageHeader = InternetMessageFormatHeader(
            EmailMessage.EmailAddress(fromAddress.emailAddress, fromAddress.alias),
            toAddresses,
            ccAddresses,
            bccAddresses,
            replyToAddresses,
            messageSubject,
        )
        val sendEmailMessageInput = SendEmailMessageInput(
            fromAddress.id,
            emailMessageHeader,
            emailBody,
            attachments,
            inlineAttachments,
        )
        return client.sendEmailMessage(sendEmailMessageInput)
    }

    protected suspend fun getFolderByName(
        client: SudoEmailClient,
        emailAddressId: String,
        folderName: String,
    ): EmailFolder? {
        val listFoldersInput = ListEmailFoldersForEmailAddressIdInput(emailAddressId)
        return client.listEmailFoldersForEmailAddressId(listFoldersInput).items.find { it.folderName == folderName }
    }

    /** Wait for single message to arrive. */
    protected fun waitForMessage(id: String) {
        Awaitility.await()
            .atMost(Duration.TEN_SECONDS)
            .pollInterval(Duration.ONE_SECOND)
            .until {
                runBlocking {
                    with(emailClient) {
                        getEmailMessage(GetEmailMessageInput(id)) != null
                    }
                }
            }
    }

    /** Wait for multiple messages to arrive. */
    protected fun waitForMessages(
        count: Int,
        listInput: ListEmailMessagesInput = ListEmailMessagesInput(),
    ): ListAPIResult<EmailMessage, PartialEmailMessage> {
        return await
            .atMost(Duration.ONE_MINUTE)
            .pollInterval(Duration.TWO_HUNDRED_MILLISECONDS)
            .untilCallTo {
                runBlocking {
                    with(emailClient) {
                        listEmailMessages(listInput)
                    }
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == count }
    }

    /** Wait for multiple messages to arrive. */
    protected fun waitForMessagesByFolder(
        count: Int,
        listInput: ListEmailMessagesForEmailFolderIdInput,
    ): ListAPIResult<EmailMessage, PartialEmailMessage> {
        return await
            .atMost(Duration.ONE_MINUTE)
            .pollInterval(Duration.TWO_HUNDRED_MILLISECONDS)
            .untilCallTo {
                runBlocking {
                    with(emailClient) {
                        listEmailMessagesForEmailFolderId(listInput)
                    }
                }
            } has { (this as ListAPIResult.Success<EmailMessage>).result.items.size == count }
    }
}
