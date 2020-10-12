/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.sudoplatform.sudoemail.rules.ActualPropertyResetter
import com.sudoplatform.sudoemail.rules.PropertyResetRule
import com.sudoplatform.sudoemail.rules.PropertyResetter
import com.sudoplatform.sudoemail.rules.TimberLogRule
import com.sudoplatform.sudologging.LogDriverInterface
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import org.junit.Rule

/**
 * Base class that sets up:
 * - [TimberLogRule]
 * - [PropertyResetRule]
 *
 * And provides convenient access to the [PropertyResetRule.before] via [PropertyResetter.before].
 */
abstract class BaseTests : PropertyResetter by ActualPropertyResetter() {
    @Rule @JvmField val timberLogRule = TimberLogRule()

    private val mockLogDriver by before {
        mock<LogDriverInterface>().stub {
            on { logLevel } doReturn LogLevel.VERBOSE
        }
    }

    protected val mockLogger by before {
        Logger("mock", mockLogDriver)
    }
}
