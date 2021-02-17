/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.inputs.filters.filterEmailMessagesBy
import com.sudoplatform.sudoemail.util.Rfc822MessageFactory
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import io.kotlintest.fail
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Test the speed of sending and receiving emails.
 *
 * @since 2020-08-20
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SudoEmailClientPerformanceTest : BaseIntegrationTest() {

    companion object {
        private const val NUMBER_EMAILS = 10
        private const val MAX_MEAN_MS_PER_EMAIL: Double = 5000.0
    }

    enum class Step {
        GET_DOMAINS,
        PROVISION_EMAIL,
        LIST_EMAILS,
        SEND_EMAILS,
        WAIT_FOR_EMAIL_DELIVERY,
        FETCH_ENVELOPES,
        FETCH_BODIES
    }

    private val verbose = false
    private val logLevel = if (verbose) LogLevel.VERBOSE else LogLevel.INFO
    private val logger = Logger("email-test", AndroidUtilsLogDriver(logLevel))
    private val timings = mutableMapOf<Step, Long>()

    private lateinit var emailClient: SudoEmailClient

    private suspend fun <T> measure(step: Step, block: suspend () -> T): T {
        val start = Instant.now()
        val result = block.invoke()
        val end = Instant.now()
        timings[step] = Duration.between(start, end).toMillis()
        return result
    }

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

        timings.forEach { println("timing: ${it.key} took ${it.value} ms") }
        timings.clear()

        deleteAllSudos()

        emailClient.close()

        Timber.uprootAll()
    }

    @Test
    fun measureTimeToSendAndReceiveManyEmails() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val sudo = sudoClient.createSudo(TestData.sudo)

        val emailDomains = measure(Step.GET_DOMAINS) {
            emailClient.getSupportedEmailDomains(CachePolicy.REMOTE_ONLY)
        }
        emailDomains.size shouldBeGreaterThanOrEqual 1

        val emailAddress = measure(Step.PROVISION_EMAIL) {
            emailClient.provisionEmailAddress(
                UUID.randomUUID().toString() + "@" + emailDomains.first(),
                sudo.id!!
            )
        }

        measure(Step.LIST_EMAILS) {
            emailClient.listEmailAddresses().items.first().emailAddress shouldBe emailAddress.emailAddress
        }

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

        measure(Step.SEND_EMAILS) {
            for (i in 0 until NUMBER_EMAILS) {
                val emailId = emailClient.sendEmailMessage(rfc822Data, emailAddress.id)
                emailId.isBlank() shouldBe false
            }
        }

        // Wait for all the messages to arrive
        measure(Step.WAIT_FOR_EMAIL_DELIVERY) {
            Awaitility.await()
                .atMost(1, TimeUnit.MINUTES)
                .pollInterval(10, TimeUnit.SECONDS)
                .until {
                    runBlocking {
                        val listOutput = emailClient.listEmailMessages(limit = NUMBER_EMAILS * 2)
                        listOutput.items.size == NUMBER_EMAILS * 2
                    }
                }
        }

        val incomingMessages = mutableListOf<EmailMessage>()
        measure(Step.FETCH_ENVELOPES) {
            var nextToken: String? = null
            while (incomingMessages.size < NUMBER_EMAILS) {
                val listOutput = emailClient.listEmailMessages(limit = NUMBER_EMAILS, nextToken = nextToken) {
                    filterEmailMessagesBy {
                        allOf(
                            direction equalTo inbound,
                            state equalTo received
                        )
                    }
                }
                incomingMessages.addAll(listOutput.items)
                nextToken = listOutput.nextToken
            }
            if (incomingMessages.size < NUMBER_EMAILS) {
                incomingMessages.forEach {
                    logger.info("${it.id} ${it.state} ${it.direction}")
                }
            }
        }

        var totalReceivedBodySize = 0L
        measure(Step.FETCH_BODIES) {
            incomingMessages.forEach { msg ->
                val data = emailClient.getEmailMessageRfc822Data(msg.messageId, CachePolicy.REMOTE_ONLY)
                data shouldNotBe null
                totalReceivedBodySize += (data?.size?.toLong() ?: 0L)
            }
        }
        val meanReceivedBodySize = totalReceivedBodySize.toDouble() / NUMBER_EMAILS.toDouble()
        println("timing: sent body size ${rfc822Data.size}")
        println("timing: mean received body size $meanReceivedBodySize")

        // Check the timings
        val failures = buildString {
            timings.forEach { (step, totalMilliseconds) ->
                if (step == Step.SEND_EMAILS || step == Step.FETCH_ENVELOPES || step == Step.FETCH_BODIES) {
                    val meanMillisecondsPerTxn = totalMilliseconds.toDouble() / NUMBER_EMAILS.toDouble()
                    if (meanMillisecondsPerTxn > MAX_MEAN_MS_PER_EMAIL) {
                        appendLine("$step had mean of $meanMillisecondsPerTxn ms per email (should be < $MAX_MEAN_MS_PER_EMAIL)")
                    }
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail(failures)
        }
    }
}
