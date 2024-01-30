/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.appsync

import com.apollographql.apollo.GraphQLCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal suspend fun <T> GraphQLCall<T>.enqueue(): Response<T> = suspendCoroutine { cont ->
    enqueue(object : GraphQLCall.Callback<T>() {
        override fun onResponse(response: Response<T>) {
            cont.resume(response)
        }
        override fun onFailure(e: ApolloException) {
            cont.resumeWithException(e)
        }
    })
}

/**
 * Deal with a GraphQL call that can return two sets of results but we only want the first.
 */
internal suspend fun <T> GraphQLCall<T>.enqueueFirst(): Response<T> = suspendCoroutine { cont ->
    val counter = AtomicInteger(0)
    enqueue(object : GraphQLCall.Callback<T>() {
        override fun onResponse(response: Response<T>) {
            if (counter.getAndIncrement() == 0) {
                cont.resume(response)
            }
        }
        override fun onFailure(e: ApolloException) {
            if (counter.getAndIncrement() == 0) {
                cont.resumeWithException(e)
            }
        }
    })
}
