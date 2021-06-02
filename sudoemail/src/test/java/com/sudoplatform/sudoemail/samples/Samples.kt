/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.samples

import android.content.Context
import org.mockito.kotlin.mock
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.types.inputs.filters.filterEmailAddressesBy
import com.sudoplatform.sudoemail.types.inputs.filters.filterEmailMessagesBy
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudouser.SudoUserClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * These are sample snippets of code that are included in the generated documentation. They are
 * placed here in the test code so that at least we know they will compile.
 *
 * @since 2020-08-20
 */
@RunWith(RobolectricTestRunner::class)
@Suppress("UNUSED_VARIABLE")
class Samples : BaseTests() {

    private val context by before { mock<Context>() }

    @Test
    fun mockTest() {
        // Just to keep junit happy
    }

    fun sudoEmailClient(sudoUserClient: SudoUserClient, sudoProfilesClient: SudoProfilesClient) {
        val emailClient = SudoEmailClient.builder()
            .setContext(context)
            .setSudoUserClient(sudoUserClient)
            .setSudoProfilesClient(sudoProfilesClient)
            .build()
    }

    suspend fun emailAddressFilter(emailClient: SudoEmailClient) {
        val exampleEmailAddress = emailClient.listEmailAddresses {
            filterEmailAddressesBy {
                allOf(
                    emailAddress notEqualTo "foo@sudoplatform.com",
                    emailAddress beginsWith "fooBar"
                )
            }
        }
    }

    suspend fun emailMessageFilter(emailClient: SudoEmailClient) {
        val newMessages = emailClient.listEmailMessages {
            filterEmailMessagesBy {
                allOf(
                    direction equalTo inbound,
                    not(seen)
                )
            }
        }

        val sentMessages = emailClient.listEmailMessages {
            filterEmailMessagesBy {
                direction equalTo outbound
            }
        }
    }
}
