/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.ExternalTestAccount
import com.sudoplatform.sudoemail.ExternalTestAccountType
import com.sudoplatform.sudoemail.S3TestAccount
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.TestData
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMask
import com.sudoplatform.sudoemail.types.EmailMaskStatus
import com.sudoplatform.sudoemail.types.inputs.DeprovisionEmailMaskInput
import com.sudoplatform.sudoemail.types.inputs.VerifyExternalEmailAddressInput
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.UUID

/**
 * Test the operation of [SudoEmailClient.verifyExternalEmailAddress].
 */
@RunWith(AndroidJUnit4::class)
class VerifyExternalEmailAddressIntegrationTest : BaseIntegrationTest() {
    private val emailMaskList = mutableListOf<EmailMask>()
    private val emailAddressList = mutableListOf<EmailAddress>()
    private val sudoList = mutableListOf<Sudo>()
    private var runTests = true

    private lateinit var sudo: Sudo
    private lateinit var ownershipProof: String
    private lateinit var maskDomains: List<String>
    private lateinit var config: com.sudoplatform.sudoemail.types.ConfigurationData
    private lateinit var externalTestAccount: ExternalTestAccount
    private lateinit var s3TestAccount: S3TestAccount
    private lateinit var externalAddress: String

    @Before
    fun setup() {
        runTest {
            config = emailClient.getConfigurationData()
            runTests = config.emailMasksEnabled
            Assume.assumeTrue("Test suite skipped due to external masks not being enabled.", runTests)
            sudo = createSudo(TestData.sudo)
            sudo shouldNotBe null
            sudoList.add(sudo)
            ownershipProof = getOwnershipProof(sudo)
            ownershipProof.isBlank() shouldBe false

            maskDomains = getMaskDomains(emailClient)
            maskDomains.size shouldBeGreaterThan 0

            if (testSentEmailBucket == null) {
                externalTestAccount = ExternalTestAccount(context, logger, ExternalTestAccountType.GMAIL)
                externalAddress = externalTestAccount.getEmailAddress()
            } else {
                s3TestAccount =
                    S3TestAccount(
                        DefaultS3Client(
                            context,
                            userClient,
                            region!!,
                            testSentEmailBucket!!,
                            logger,
                        ),
                        userClient.getCredentialsProvider().identityId,
                        logger,
                    )
                externalAddress = "${UUID.randomUUID()}@sudoplatform.com"
            }
        }
    }

    @After
    fun teardown() =
        runTest {
            emailMaskList.forEach {
                emailClient.deprovisionEmailMask(DeprovisionEmailMaskInput(it.id))
            }
            emailAddressList.forEach { emailClient.deprovisionEmailAddress(it.id) }
            if (sudoList.isNotEmpty()) {
                sudoList.map { sudoClient.deleteSudo(it) }
            }
            if (runTests && testSentEmailBucket == null) {
                externalTestAccount.closeConnection()
            }
        }

    @Test
    fun verifyExternalEmailAddressShouldTriggerVerificationEmail() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("verif-external")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            // Provision an email mask with external address
            val emailMask = provisionEmailMask(maskAddress, externalAddress, ownershipProof)
            emailMaskList.add(emailMask)

            emailMask shouldNotBe null
            emailMask.status shouldBe EmailMaskStatus.PENDING

            try {
                // Trigger verification email (no code provided)
                val triggerResult =
                    emailClient.verifyExternalEmailAddress(
                        VerifyExternalEmailAddressInput(
                            emailAddress = externalAddress,
                            emailMaskId = emailMask.id,
                            verificationCode = null,
                        ),
                    )

                triggerResult shouldNotBe null
                triggerResult.isVerified shouldBe false
                triggerResult.reason shouldBe "Verification email sent"
            } catch (e: Exception) {
                logger.error("Failed to trigger verification email: ${e.message}")
                if (e is SudoEmailClient.EmailMaskException.FailedException &&
                    e.message!!.contains(serviceQuotaExceededErrorMessage)
                ) {
                    logger.info("Service quota exceeded, skipping verification flow test")
                    return@runTest
                } else {
                    throw e
                }
            }
        }

    @Test
    fun verifyExternalEmailAddressShouldCompleteVerificationFlowAndRejectReuse() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("verif-flow")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            // Provision an email mask with external address
            val emailMask = provisionEmailMask(maskAddress, externalAddress, ownershipProof)
            emailMaskList.add(emailMask)

            emailMask shouldNotBe null
            emailMask.status shouldBe EmailMaskStatus.PENDING

            // Trigger verification email
            val searchFromDate = Date(System.currentTimeMillis() - 60_000)
            try {
                val triggerResult =
                    emailClient.verifyExternalEmailAddress(
                        VerifyExternalEmailAddressInput(
                            emailAddress = externalAddress,
                            emailMaskId = emailMask.id,
                            verificationCode = null,
                        ),
                    )

                triggerResult.isVerified shouldBe false
            } catch (e: Exception) {
                logger.error("Failed to trigger verification email: ${e.message}")
                if (e is SudoEmailClient.EmailMaskException.FailedException &&
                    e.message!!.contains(serviceQuotaExceededErrorMessage)
                ) {
                    logger.info("Service quota exceeded, skipping verification flow test")
                    return@runTest
                } else {
                    throw e
                }
            }
            // Wait for verification emails and try all codes
            var verificationResult: VerificationResult? = null
            if (testSentEmailBucket == null) {
                val result =
                    waitForVerificationAndTryAllCodes(
                        externalAddress = externalAddress,
                        emailMaskId = emailMask.id,
                        searchFromDate = searchFromDate,
                    )

                result shouldNotBe null

                // Delete the email that was successfully used
                if (result?.messageId != null) {
                    externalTestAccount.deleteEmail(result.messageId)
                    verificationResult = result
                }
            } else {
                val verificationEmail =
                    s3TestAccount.waitForEmail(
                        subject = "Verify your email address",
                        options = S3TestAccount.WaitForEmailOptions(timeoutMs = 120_000, searchFromDate = searchFromDate),
                    )
                val verificationCode = extractVerificationCode(verificationEmail.textBody ?: "")
                verificationCode shouldNotBe null
                try {
                    val verifyResult =
                        emailClient.verifyExternalEmailAddress(
                            VerifyExternalEmailAddressInput(
                                emailAddress = externalAddress,
                                emailMaskId = emailMask.id,
                                verificationCode = verificationCode,
                            ),
                        )

                    if (verifyResult.isVerified) {
                        logger.info("Successfully verified with code: $verificationCode")
                        verificationResult = VerificationResult(verificationCode!!, verificationEmail.messageId)
                    } else {
                        logger.debug("Verification failed with code $verificationCode: ${verifyResult.reason}")
                    }
                } catch (e: Exception) {
                    logger.debug("Verification failed with code $verificationCode: ${e.message}")
                    // Continue trying other codes
                }
            }
            verificationResult shouldNotBe null

            // Verify mask status changed to ENABLED
            val updatedMask =
                emailClient.listEmailMasksForOwner(
                    com.sudoplatform.sudoemail.types.inputs
                        .ListEmailMasksForOwnerInput(),
                )
            val mask = (updatedMask as com.sudoplatform.sudoemail.types.ListAPIResult.Success).result.items.find { it.id == emailMask.id }
            mask shouldNotBe null
            mask!!.status shouldBe EmailMaskStatus.ENABLED

            // Try to verify again with the same code — should be rejected
            val secondResult =
                emailClient.verifyExternalEmailAddress(
                    VerifyExternalEmailAddressInput(
                        emailAddress = externalAddress,
                        emailMaskId = emailMask.id,
                        verificationCode = verificationResult!!.verificationCode,
                    ),
                )

            secondResult shouldNotBe null
            secondResult.isVerified shouldBe false
            secondResult.reason shouldBe "No verification code found for email address"
        }

    @Test
    fun verifyExternalEmailAddressShouldFailWithInvalidCode() =
        runTest {
            val maskLocalPart = generateSafeLocalPart("verif-invalid")
            val maskAddress = "$maskLocalPart@${maskDomains.first()}"

            // Provision an email mask with external address — mask starts in PENDING state
            val emailMask = provisionEmailMask(maskAddress, externalAddress, ownershipProof)
            emailMaskList.add(emailMask)

            emailMask shouldNotBe null
            emailMask.status shouldBe EmailMaskStatus.PENDING

            // Try to verify with invalid code (no trigger needed — mask is already PENDING)
            val verifyResult =
                emailClient.verifyExternalEmailAddress(
                    VerifyExternalEmailAddressInput(
                        emailAddress = externalAddress,
                        emailMaskId = emailMask.id,
                        verificationCode = "000000",
                    ),
                )

            verifyResult shouldNotBe null
            verifyResult.isVerified shouldBe false
            verifyResult.reason shouldNotBe null

            // Verify mask status remains PENDING
            val updatedMask =
                emailClient.listEmailMasksForOwner(
                    com.sudoplatform.sudoemail.types.inputs
                        .ListEmailMasksForOwnerInput(),
                )
            val mask = (updatedMask as com.sudoplatform.sudoemail.types.ListAPIResult.Success).result.items.find { it.id == emailMask.id }
            mask shouldNotBe null
            mask!!.status shouldBe EmailMaskStatus.PENDING
        }

    @Test
    fun verifyExternalEmailAddressShouldThrowWithNonExistentMask() =
        runTest {
            shouldThrow<SudoEmailClient.EmailMaskException.EmailMaskNotFoundException> {
                emailClient.verifyExternalEmailAddress(
                    VerifyExternalEmailAddressInput(
                        emailAddress = externalAddress,
                        emailMaskId = "non-existent-mask-id",
                    ),
                )
            }
        }

    /**
     * Extracts a 6-digit verification code from email body text.
     * Tries multiple patterns to find the code.
     */
    private fun extractVerificationCode(emailBody: String): String? {
        // Prefer 6-digit code as the sole content within an HTML tag, e.g. >000000<
        val htmlTagPattern = Regex(">([0-9]{6})<")
        val htmlMatch = htmlTagPattern.find(emailBody)
        if (htmlMatch != null) {
            return htmlMatch.groupValues[1]
        }

        // Alternatively: line with only 6 digits
        val lines = emailBody.split("\n", "\r\n")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^[0-9]{6}$"))) {
                return trimmed
            }
        }

        // Fall back to: any 6 digits with word boundaries, excluding a leading # (colour specs)
        val pattern = Regex("(?<!#)\\b[0-9]{6}\\b")
        val match = pattern.find(emailBody)
        if (match != null) {
            return match.value
        }

        return null
    }

    /**
     * Helper data class to hold verification result.
     */
    private data class VerificationResult(
        val verificationCode: String,
        val messageId: String?,
    )

    /**
     * Waits for verification emails and tries each code until one succeeds.
     * Once the successful code is identified, deletes it. This allows us to
     * run tests concurrently without building up too much noise in the external
     * accounts - we can't know the contents of the email or the sender's address
     * since it varies with environments.
     *
     * @param externalAddress The external email address to verify
     * @param emailMaskId The ID of the email mask being verified
     * @param searchFromDate The date to start searching for emails from
     * @param timeoutMs Timeout in milliseconds for waiting for emails
     * @return VerificationResult containing the successful code and message ID, or null if none succeeded
     */
    private suspend fun waitForVerificationAndTryAllCodes(
        externalAddress: String,
        emailMaskId: String,
        searchFromDate: Date,
        timeoutMs: Long = 30_000L,
    ): VerificationResult? {
        // Retrieve all verification emails that arrived after triggering the verification
        // with the given subject.
        val verificationEmails =
            externalTestAccount.waitForAllEmailsBySubject(
                subject = "Verify your email address",
                options =
                    ExternalTestAccount.WaitOptions(
                        searchFromDate = searchFromDate,
                        timeoutMs = timeoutMs,
                    ),
            )

        if (verificationEmails.isEmpty()) {
            logger.error("No verification emails found")
            return null
        }

        logger.info("Found ${verificationEmails.size} verification email(s)")

        // Try each verification code until one succeeds
        for (email in verificationEmails) {
            val verificationCode = extractVerificationCode(email.textBody ?: "")
            if (verificationCode == null) {
                logger.debug("Could not extract code from email")
                continue
            }

            try {
                val verifyResult =
                    emailClient.verifyExternalEmailAddress(
                        VerifyExternalEmailAddressInput(
                            emailAddress = externalAddress,
                            emailMaskId = emailMaskId,
                            verificationCode = verificationCode,
                        ),
                    )

                if (verifyResult.isVerified) {
                    logger.info("Successfully verified with code: $verificationCode")
                    return VerificationResult(verificationCode, email.messageId)
                } else {
                    logger.debug("Verification failed with code $verificationCode: ${verifyResult.reason}")
                }
            } catch (e: Exception) {
                logger.debug("Verification failed with code $verificationCode: ${e.message}")
                // Continue trying other codes
            }
        }

        logger.error("None of the verification codes succeeded")
        return null
    }
}
