/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.internal.data.common.mechanisms.Unsealer.Companion.KEY_SIZE_AES
import com.sudoplatform.sudoemail.rules.ActualPropertyResetter
import com.sudoplatform.sudoemail.rules.PropertyResetRule
import com.sudoplatform.sudoemail.rules.PropertyResetter
import com.sudoplatform.sudoemail.rules.TimberLogRule
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudologging.LogDriverInterface
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import org.apache.commons.codec.binary.Base64
import org.junit.Rule
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verifyNoMoreInteractions

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

    protected val mockContext by before {
        mock<Context>()
    }

    protected open val mockUserClient by before {
        mock<SudoUserClient>()
    }

    protected open val mockApiClient by before {
        mock<ApiClient>()
    }

    protected open val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    protected open val mockS3Client by before {
        mock<S3Client>()
    }

    fun verifyNoMoreInteractionsOnBaseMocks() {
        verifyNoMoreInteractions(
            mockS3Client,
            mockKeyManager,
            mockApiClient,
            mockUserClient,
        )
    }

    protected fun mockSeal(value: String): String {
        val valueBytes = value.toByteArray()
        val data = ByteArray(KEY_SIZE_AES)
        valueBytes.copyInto(data)
        return Base64.encodeBase64String(data)
    }

    // Common mock IDs and values used across tests
    protected val mockEmailAddressId = "mockEmailAddressId"
    protected val mockEmailMessageId = "mockEmailMessageId"
    protected val mockDraftId = "mockDraftId"
    protected val mockFolderId = "mockFolderId"
    protected val mockCustomFolderId = "mockCustomFolderId"
    protected val mockCustomFolderName = "mockCustomFolderName"
    protected val mockKeyId = "mockKeyId"
    protected val mockOwner = "mockOwner"
    protected val mockSudoId = "mockSudoId"
    protected val mockSymmetricKeyId = "mockSymmetricKeyId"
    protected val mockAlgorithm = "AES/CBC/PKCS7Padding"

    // Common test email domains
    protected val mockInternalDomain = "internal.sudoplatform.com"
    protected val mockMixedCaseInternalDomain = "InTeRnAl.SuDoPlAtFoRm.CoM"
    protected val mockExternalDomain = "sudoplatform.com"
    protected val mockSenderAddress = "sender@$mockInternalDomain"
    protected val mockExternalRecipientAddress = "recipient@$mockExternalDomain"
    protected val mockInternalRecipientAddress = "recipient@$mockInternalDomain"
    protected val mockMixedCaseInternalRecipientAddress = "rEcIpIeNt@$mockMixedCaseInternalDomain"
}
