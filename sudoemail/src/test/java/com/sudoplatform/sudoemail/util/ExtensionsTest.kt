/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.types.EmailAttachment
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test the operation of Extension functions under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class ExtensionsTest : BaseTests() {

    @Test
    fun `Should extract image tag from email body successfully`() {
        val body = "<div \"ltr\"><br>\n" +
            "<img \n" +
            "src=\"file:///path/to/my/important/image.png\" height=\"156\"\n" +
            "alt=\"ii_ia6yo3z92_14d962f8450cc6f1\" width=144>\n" +
            "<br>\n" +
            "Cras eu velit ac purus feugiat impe<br>Best regards<br></div>"
        val inlineAttachment = EmailAttachment(
            "image.png",
            "ii_ia6yo3z92_14d962f8450cc6f1",
            "image/png",
            true,
            "This is an important image".toByteArray(),
        )

        val cleanHTML = replaceInlinePathsWithCids(body, listOf(inlineAttachment))!!
        assertTrue(cleanHTML.contains("src=\"cid:ii_ia6yo3z92_14d962f8450cc6f1\""))
    }

    @Test
    fun `Should extract image tag from an email body when there is no tag with content id`() {
        val body = "<div \"ltr\"><br>\n" +
            "<img \n" +
            "src=\"file:///path/to/my/important/image.png\" height=\"156\"\n" +
            "<br>\n" +
            "Cras eu velit ac purus feugiat impe<br>Best regards<br></div>"
        val inlineAttachment = EmailAttachment(
            "image.png",
            "ii_ia6yo3z92_14d962f8450cc6f1",
            "image/png",
            true,
            "This is an important image".toByteArray(),
        )

        val cleanHTML = replaceInlinePathsWithCids(body, listOf(inlineAttachment))!!
        assertTrue(cleanHTML.contains("src=\"cid:ii_ia6yo3z92_14d962f8450cc6f1\""))
    }

    @Test
    fun `Should extract image tag from an email body when there is no tag with content id and two images`() {
        val cidFirstImage = "ii_ia6yo3z92_14d962f8450cc6f1"
        val cidSecondImage = "ii_ia6yo3z92_14d9WW62f8450cc6f1"
        val body = "<div \"ltr\"><br>\n" +
            "<img \n" +
            "src=\"file:///path/to/my/important/image.png\" height=\"156\">\n" +
            "<br>\n" +
            "<img \n" +
            "src=\"file:///path/to/my/important/other_image.png\" height=\"156\">\n" +
            "Cras eu velit ac purus feugiat impe<br>Best regards<br></div>"
        val inlineAttachmentOne = EmailAttachment(
            "image.png",
            cidFirstImage,
            "image/png",
            true,
            "This is an important image".toByteArray(),
        )
        val inlineAttachmentTwo = EmailAttachment(
            "other_image.png",
            cidSecondImage,
            "image/png",
            true,
            "This is another important image".toByteArray(),
        )

        val cleanHTML = replaceInlinePathsWithCids(body, listOf(inlineAttachmentOne, inlineAttachmentTwo))!!
        assertTrue(cleanHTML.contains("src=\"cid:$cidFirstImage\""))
        assertTrue(cleanHTML.contains("src=\"cid:$cidSecondImage\""))
    }

    @Test
    fun `Should extract image tag from an email body when there is no tag with content id and image has no content id`() {
        val body = "<div \"ltr\"><br>\n" +
            "<img \n" +
            "src=\"file:///path/to/my/important/image.png\" height=\"156\">\n" +
            "<br>\n" +
            "Cras eu velit ac purus feugiat impe<br>Best regards<br></div>"
        val inlineAttachment = EmailAttachment(
            "image.png",
            "",
            "image/png",
            true,
            "This is an important image".toByteArray(),
        )

        val cleanHTML = replaceInlinePathsWithCids(body, listOf(inlineAttachment))!!
        assertTrue(cleanHTML.contains("src=\"cid:"))
    }
}
