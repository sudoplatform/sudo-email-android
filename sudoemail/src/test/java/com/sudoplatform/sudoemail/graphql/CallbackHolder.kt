/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.graphql

import com.amazonaws.mobileconnectors.appsync.AppSyncMutationCall
import com.amazonaws.mobileconnectors.appsync.AppSyncQueryCall
import com.amazonaws.mobileconnectors.appsync.AppSyncQueryWatcher
import com.apollographql.apollo.GraphQLCall
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.OperationName
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.api.cache.http.HttpCachePolicy
import com.apollographql.apollo.cache.CacheHeaders
import com.apollographql.apollo.fetcher.ResponseFetcher

/**
 * A test helper for mocking GraphQL operations and callbacks.
 *
 * @since 2020-08-05
 */
class CallbackHolder<D> {

    var callback: GraphQLCall.Callback<D>? = null

    val queryOperation = object : AppSyncQueryCall<D> {
        private var isCancelled = false
        override fun cacheHeaders(cacheHeaders: CacheHeaders) = this
        override fun cancel() { isCancelled = true }
        override fun clone() = this
        override fun httpCachePolicy(httpCachePolicy: HttpCachePolicy.Policy) = this
        override fun isCanceled() = isCancelled
        override fun operation(): Operation<*, *, *> { throw IllegalAccessError() }
        override fun watcher(): AppSyncQueryWatcher<D> { throw IllegalAccessError() }
        override fun responseFetcher(fetcher: ResponseFetcher) = this
        override fun enqueue(cb: GraphQLCall.Callback<D>?) {
            callback = cb
        }
    }

    val mutationOperation = object : AppSyncMutationCall<D> {
        private var isCancelled = false
        override fun cacheHeaders(cacheHeaders: CacheHeaders) = this
        override fun cancel() { isCancelled = true }
        override fun clone() = this

        override fun isCanceled() = isCancelled
        override fun operation(): Operation<*, *, *> { throw IllegalAccessError() }
        override fun refetchQueries(vararg operationNames: OperationName?) = this
        override fun refetchQueries(vararg queries: Query<*, *, *>?) = this
        override fun enqueue(cb: GraphQLCall.Callback<D>?) {
            callback = cb
        }
    }
}
