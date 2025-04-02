/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import android.net.Uri
import com.sudoplatform.sudoemail.types.EmailAttachment

/**
 * Contains a series of extensions to support operations on email data
 * throughout the Sudo Platform Email SDK.
 */

/** Regex to match all image tags. */
private val IMAGE_SOURCE_REGEX = "<img[^>]*>".toRegex()

/** Regex to identify the parameter type and the value within the tag. */
private val IMAGE_PARAMETERS_REGEX = "(src|alt)=\"([^\"\\s]*)\"".toRegex()

private const val SRC_TAG = "src"
private const val FILE_TAG = "file:"
private const val CID_TAG = "cid:"
private const val STYLE = "style=\"width: 288px;\""

/**
 * Replaces inline attachment file paths with corresponding Content-IDs (cids).
 */
internal fun replaceInlinePathsWithCids(
    htmlBody: String,
    inlineAttachments: List<EmailAttachment>,
): String? {
    if (htmlBody.isBlank()) return null

    val inlineAttachmentMap = inlineAttachments.associateBy { it.fileName }
    var cleanHTML = htmlBody

    // Find inline images with tags and replace paths or cids with the correct values
    if (IMAGE_SOURCE_REGEX.containsMatchIn(cleanHTML)) {
        val matches = IMAGE_SOURCE_REGEX.findAll(cleanHTML)
        matches.forEach {
            val fullTag = it.value
            val tags = IMAGE_PARAMETERS_REGEX.findAll(fullTag)
            // If no file uri can be extracted then this tag is skipped
            val uri = extractUriFromTags(tags) ?: return@forEach
            val path = Uri.parse(uri).path
            requireNotNull(path)
            // Get the filename
            val filename = path.substringAfterLast('/')
            // Get the inline email attachment for the file. If it does not exist then exit
            val inlineAttachment = inlineAttachmentMap[filename] ?: return@forEach
            val cid = resolveCidForImage(path, inlineAttachment)
            cleanHTML = replaceUriForCid(fullTag, cleanHTML, uri, cid)
        }
    }
    return cleanHTML
}

/**
 * Extract a URI based on a sequence of regular expression matches
 * on HTML tags.
 */
private fun extractUriFromTags(tags: Sequence<MatchResult>): String? {
    return tags.mapNotNull {
        val tag = it.groups[1]?.value
        val tagValue = it.groups[2]?.value

        if (tag == SRC_TAG && tagValue?.startsWith(FILE_TAG) == true) {
            tagValue
        } else {
            null
        }
    }.firstOrNull()
}

/**
 * Replaces a URI in a HTML tag with a Content-ID (cid) and updates the HTML body accordingly.
 */
private fun replaceUriForCid(originalTag: String, body: String, uri: String, cid: String): String {
    val replacementTag = originalTag
        .replace(STYLE, "")
        .replace("$SRC_TAG=\"$uri\"", "$SRC_TAG=\"$CID_TAG$cid\" $STYLE")
    return body.replace(originalTag, replacementTag)
}

private fun resolveCidForImage(path: String, inlineAttachment: EmailAttachment): String {
    var cid = path.hashCode().toString()
    inlineAttachment.contentId.let { cid = it }
    return cid
}
