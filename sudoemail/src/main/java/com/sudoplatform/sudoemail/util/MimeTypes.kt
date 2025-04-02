/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

/**
 * Defines constants for different MIME types.
 */
internal class MimeTypes {
    companion object {
        const val TEXT_ANY = "text/*"
        const val TEXT_PLAIN = "text/plain"
        const val TEXT_HTML = "text/html"
        const val TEXT_HTML_UTF8 = "text/html; charset=UTF-8"
        const val MULTIPART = "multipart/*"
        const val MULTIPART_ALTERNATIVE = "multipart/alternative"
        const val RFC822_HEADERS = "text/rfc822-headers"
        const val RFC822_MESSAGE = "message/rfc822"
        const val DELIVERY_STATUS = "message/delivery-status"
        const val CALENDAR = "text/calendar"
    }
}
