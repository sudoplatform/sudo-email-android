/*
 * Copyright © 2020 - Anonyome Labs, Inc. - All rights reserved
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.types.inputs.filters.filterEmailAddressesBy
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import org.junit.Test

/**
 * Test that the [EmailAddressTransformer.toGraphQLFilter] converts to GraphQL filters correctly.
 *
 * @since 2020-08-27
 */
class EmailAddressTransformerTest : BaseTests() {

    @Test
    fun testAddressFilterTransformation() {
        val addressFilter = EmailAddressTransformer.toGraphQLFilter(
            filterEmailAddressesBy {
                emailAddress equalTo "example@sudoplatform.com"
            }
        )
        addressFilter shouldNotBe null
        addressFilter?.emailAddress()?.eq() shouldBe "example@sudoplatform.com"
    }

    @Test
    fun testNotAddressFilterTransformation() {
        val filter = EmailAddressTransformer.toGraphQLFilter(
            filterEmailAddressesBy {
                emailAddress notEqualTo "foo@sudoplatform.com"
            }
        )
        filter shouldNotBe null
        filter?.emailAddress()?.ne() shouldBe "foo@sudoplatform.com"
    }

    @Test
    fun testAndFilterTransformation() {
        val andFilter = EmailAddressTransformer.toGraphQLFilter(
            filterEmailAddressesBy {
                allOf(
                    emailAddress notEqualTo "foo@sudoplatform.com",
                    emailAddress beginsWith "bar"
                )
            }
        )
        andFilter shouldNotBe null
        andFilter?.and()?.size shouldBe 2
        andFilter?.and()?.get(0)?.emailAddress()?.ne() shouldBe "foo@sudoplatform.com"
        andFilter?.and()?.get(1)?.emailAddress()?.beginsWith() shouldBe "bar"
    }

    @Test
    fun testNestedAndFilterTransformation() {
        val andFilter = EmailAddressTransformer.toGraphQLFilter(
            filterEmailAddressesBy {
                allOf(
                    allOf(
                        emailAddress notEqualTo "foo@sudoplatform.com"
                    ),
                    allOf(
                        emailAddress beginsWith "bar"
                    )
                )
            }
        )
        andFilter shouldNotBe null
        andFilter?.and()?.size shouldBe 2
        andFilter?.and()?.get(0)?.and()?.get(0)?.emailAddress()?.ne() shouldBe "foo@sudoplatform.com"
        andFilter?.and()?.get(1)?.and()?.get(0)?.emailAddress()?.beginsWith() shouldBe "bar"
    }

    @Test
    fun testNestedOrFilterTransformation() {
        val andFilter = EmailAddressTransformer.toGraphQLFilter(
            filterEmailAddressesBy {
                oneOf(
                    allOf(
                        emailAddress notEqualTo "foo@sudoplatform.com"
                    ),
                    allOf(
                        emailAddress beginsWith "bar"
                    )
                )
            }
        )
        andFilter shouldNotBe null
        andFilter?.or()?.size shouldBe 2
        andFilter?.or()?.get(0)?.and()?.get(0)?.emailAddress()?.ne() shouldBe "foo@sudoplatform.com"
        andFilter?.or()?.get(1)?.and()?.get(0)?.emailAddress()?.beginsWith() shouldBe "bar"
    }

    @Test
    fun testOrFilterTransformation() {
        val orFilter = EmailAddressTransformer.toGraphQLFilter(
            filterEmailAddressesBy {
                oneOf(
                    emailAddress equalTo "example@sudoplatform.com",
                    emailAddress beginsWith "example"
                )
            }
        )
        orFilter shouldNotBe null
        orFilter?.or()?.size shouldBe 2
        orFilter?.or()?.get(0)?.emailAddress()?.eq() shouldBe "example@sudoplatform.com"
        orFilter?.or()?.get(1)?.emailAddress()?.beginsWith() shouldBe "example"
    }
}
