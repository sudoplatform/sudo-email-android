/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.rules

import android.util.Log
import org.junit.rules.ExternalResource
import timber.log.Timber
import timber.log.Timber.Tree

/**
 * Log rule that sets up a timber logger that logs to the test
 * console for easy viewing of log results.
 */
class TimberLogRule(
    /**
     * the min priority is reset to this value for every test
     */
    private val defaultMinPriority: Int = Log.VERBOSE
) : ExternalResource() {

    /**
     * This value can be changed in each test function to adjust
     * the minimum priority of logs to output
     */
    var minPriority: Int = defaultMinPriority

    private val tree: Tree = object : Tree() {
        override fun isLoggable(tag: String?, priority: Int): Boolean {
            return priority >= minPriority
        }

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            println("${tag ?: ""}:$message")
            t?.printStackTrace()
        }
    }

    override fun before() {
        minPriority = defaultMinPriority
        Timber.plant(tree)
    }

    override fun after() {
        Timber.uprootAll()
    }
}
