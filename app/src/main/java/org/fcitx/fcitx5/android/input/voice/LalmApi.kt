/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

internal fun buildLalmAudioPart(
    format: LalmAudioFormat,
    model: String,
    audioBase64: String,
    mime: String,
): JsonObject {
    val audioFormat = if (mime == "audio/mpeg" || mime.contains("mp3")) "mp3" else "wav"
    val audioMime = if (audioFormat == "mp3") "audio/mpeg" else "audio/wav"
    val dataUrl = "data:$audioMime;base64,$audioBase64"
    val useDataUrl = format == LalmAudioFormat.InputAudioDataUrl ||
        model.substringAfterLast('/').startsWith("mimo-", ignoreCase = true)
    return buildJsonObject {
        if (format == LalmAudioFormat.AudioUrl) {
            put("type", "audio_url")
            put("audio_url", buildJsonObject { put("url", dataUrl) })
        } else {
            put("type", "input_audio")
            put("input_audio", buildJsonObject {
                put("data", if (useDataUrl) dataUrl else audioBase64)
                if (!useDataUrl) put("format", audioFormat)
            })
        }
    }
}

internal fun lalmThinkingType(mode: LalmThinkingMode, model: String): String? = when (mode) {
    LalmThinkingMode.Auto -> if (
        model.substringAfterLast('/').startsWith("mimo-v2.6-", ignoreCase = true)
    ) "disabled" else null
    LalmThinkingMode.Disabled -> "disabled"
    LalmThinkingMode.Enabled -> "enabled"
}

internal fun parseLalmResponse(body: String, statusCode: Int, saveDebug: Boolean): TranscriptionResult {
    val rawResponseBody = if (saveDebug) body else ""
    if (statusCode !in 200..299) {
        throw TranscriptionException("LALM error: $statusCode", rawResponseBody)
    }
    if (body.isBlank()) {
        throw TranscriptionException("Empty response from LALM", rawResponseBody)
    }
    try {
        val response = Json.parseToJsonElement(body) as JsonObject
        val choices = response["choices"] as JsonArray
        val choice = choices.firstOrNull() as? JsonObject
        val message = choice?.get("message") as? JsonObject
        val content = message?.get("content")
        val text = when (content) {
            null, JsonNull -> ""
            is JsonPrimitive -> {
                require(content.isString)
                content.content
            }
            is JsonArray -> content.joinToString("") { part ->
                val textPart = part as JsonObject
                if ((textPart["type"] as? JsonPrimitive)?.contentOrNull == "text") {
                    (textPart["text"] as JsonPrimitive).content
                } else ""
            }
            else -> error("Unsupported LALM content")
        }
        // reasoning_content is diagnostic data, never text to commit to the input field.
        return TranscriptionResult(text = text, rawResponseBody = rawResponseBody)
    } catch (_: Exception) {
        throw TranscriptionException("Invalid response from LALM", rawResponseBody)
    }
}
