/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.rules.ActualPropertyResetter
import com.sudoplatform.sudoemail.rules.PropertyResetRule
import com.sudoplatform.sudoemail.rules.PropertyResetter
import com.sudoplatform.sudoemail.rules.TimberLogRule
import com.sudoplatform.sudoemail.types.transformers.Unsealer.Companion.KEY_SIZE_AES
import com.sudoplatform.sudologging.LogDriverInterface
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import org.apache.commons.codec.binary.Base64
import org.junit.Rule
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * Base class that sets up:
 * - [TimberLogRule]
 * - [PropertyResetRule]
 *
 * And provides convenient access to the [PropertyResetRule.before] via [PropertyResetter.before].
 */
abstract class BaseTests : PropertyResetter by ActualPropertyResetter() {
    @Rule
    @JvmField
    val timberLogRule = TimberLogRule()

    private val mockLogDriver by before {
        mock<LogDriverInterface>().stub {
            on { logLevel } doReturn LogLevel.VERBOSE
        }
    }

    protected val mockLogger by before {
        Logger("mock", mockLogDriver)
    }

    protected fun mockSeal(value: String): String {
        val valueBytes = value.toByteArray()
        val data = ByteArray(KEY_SIZE_AES)
        valueBytes.copyInto(data)
        return Base64.encodeBase64String(data)
    }
}
