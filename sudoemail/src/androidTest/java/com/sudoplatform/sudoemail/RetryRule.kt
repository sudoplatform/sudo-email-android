/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import timber.log.Timber

/**
 * A JUnit4 [TestRule] that retries a failing test up to [retryCount] additional
 * times before reporting it as failed.
 *
 * This rule provides in-run retry behaviour for the integration test suite. A test that passes on any attempt is
 * reported as passing; only when every attempt fails is the last failure rethrown.
 *
 * @param retryCount The number of *additional* attempts after the initial run.
 *   For example, a value of `2` means a test may run up to 3 times in total.
 * @param retryDelayMillis How long to wait between a failed attempt and the next
 *   retry. A short delay gives transient/eventually-consistent backend conditions a
 *   chance to settle before the test runs again. No delay is applied after the final
 *   attempt.
 */
class RetryRule(
    private val retryCount: Int = DEFAULT_RETRY_COUNT,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
) : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val totalAttempts = retryCount + 1
                var lastError: Throwable? = null

                for (attempt in 1..totalAttempts) {
                    try {
                        base.evaluate()
                        if (attempt > 1) {
                            report("${description.displayName} PASSED on retry attempt $attempt of $totalAttempts")
                        }
                        return
                    } catch (t: Throwable) {
                        lastError = t
                        val willRetry = attempt < totalAttempts
                        report(
                            "${description.displayName} FAILED on attempt $attempt of $totalAttempts" +
                                if (willRetry) " - retrying in ${retryDelayMillis}ms" else "",
                            t,
                        )
                        if (willRetry && retryDelayMillis > 0) {
                            try {
                                Thread.sleep(retryDelayMillis)
                            } catch (interrupted: InterruptedException) {
                                Thread.currentThread().interrupt()
                                throw interrupted
                            }
                        }
                    }
                }

                report("${description.displayName} FAILED after all $totalAttempts attempts", lastError)
                throw lastError ?: IllegalStateException(
                    "Test failed but no error was captured for ${description.displayName}",
                )
            }

            /**
             * Reports retry activity to both Timber (logcat) and stdout. On Gradle
             * Managed Devices the Gradle/UTP test-progress console does not surface
             * logcat, so printing to stdout ensures retries are visible in the
             * pipeline job output, not just in a captured logcat artifact.
             */
            private fun report(
                message: String,
                error: Throwable? = null,
            ) {
                println("[RetryRule] $message")
                if (error != null) {
                    Timber.w(error, message)
                } else {
                    Timber.w(message)
                }
            }
        }

    companion object {
        const val DEFAULT_RETRY_COUNT = 2
        const val DEFAULT_RETRY_DELAY_MILLIS = 2000L
    }
}
