/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * LALM (Large Audio Language Model) backend — POST to an OpenAI-compatible
 * /v1/chat/completions endpoint with audio as multimodal content.
 *
 * Works with models like GPT-4o-audio-preview that accept audio input.
 */
class LALMBackend(
    private val getUrl: () -> String?,
    private val getApiKey: () -> String?,
    private val getModel: () -> String?,
    private val getSystemPrompt: () -> String?,
    private val getAudioFormat: () -> LalmAudioFormat,
    private val getTemperature: () -> String?,
    private val getThinkingMode: () -> LalmThinkingMode = { LalmThinkingMode.Enabled },
    private val getSaveApiDebug: () -> Boolean = { false },
) : VoiceBackend {

    override val name = "LALM"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun transcribe(
        audioBytes: ByteArray,
        mime: String,
        prompt: String?,
        selectedText: String?,
        imageBase64: String?
    ): Result<TranscriptionResult> = withContext(Dispatchers.IO) {
        runTranscriptionCatching {
            val apiKey = requireConfigured(getApiKey(), "LALM API key")
            val model = requireConfigured(getModel(), "LALM model")
            val baseUrl = requireSecureBaseUrl(getUrl(), "LALM API URL")
            val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            val audioPart = buildLalmAudioPart(getAudioFormat(), model, audioBase64, mime)
            val saveApiDebug = getSaveApiDebug()

            val (systemPrompt, userText) = if (selectedText != null) {
                // Edit mode: voice is an instruction to modify selected text
                val sys = lalmSystemPrompt(getSystemPrompt(), editMode = true)
                val contextBlock = if (!prompt.isNullOrBlank()) {
                    "<TRANSCRIPTION_CONTEXT>\n$prompt\n</TRANSCRIPTION_CONTEXT>\n"
                } else ""
                val user = "${contextBlock}<SELECTED_TEXT>\n$selectedText\n</SELECTED_TEXT>\n" +
                    "Execute the voice instruction on the selected text. Output ONLY the edited text, nothing else."
                sys to user
            } else {
                // Normal transcription mode
                val sys = lalmSystemPrompt(getSystemPrompt(), editMode = false)
                val contextBlock = if (!prompt.isNullOrBlank()) {
                    "<TRANSCRIPTION_CONTEXT>\n$prompt\n</TRANSCRIPTION_CONTEXT>\n"
                } else ""
                val user = "${contextBlock}Transcribe the following audio. Output ONLY the transcription text, nothing else."
                sys to user
            }

            val userContentParts = mutableListOf<JsonObject>()

            // Add text instruction
            userContentParts.add(JsonObject(mapOf(
                "type" to JsonPrimitive("text"),
                "text" to JsonPrimitive(userText)
            )))

            // Add screenshot if available
            if (!imageBase64.isNullOrEmpty()) {
                Timber.d("[$name] Including screenshot in request")
                userContentParts.add(JsonObject(mapOf(
                    "type" to JsonPrimitive("image_url"),
                    "image_url" to JsonObject(mapOf(
                        "url" to JsonPrimitive(imageBase64)
                    ))
                )))
            }

            // Add audio
            userContentParts.add(audioPart)

            val userContent = JsonArray(userContentParts.toList())

            // Build full request as JsonObject for precise control
            val requestFields = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
                "model" to JsonPrimitive(model),
                "messages" to JsonArray(listOf(
                    JsonObject(mapOf(
                        "role" to JsonPrimitive("system"),
                        "content" to JsonPrimitive(systemPrompt)
                    )),
                    JsonObject(mapOf(
                        "role" to JsonPrimitive("user"),
                        "content" to userContent
                    ))
                ))
            )
            getTemperature()?.trim()?.toDoubleOrNull()?.let {
                requestFields["temperature"] = JsonPrimitive(it)
            }
            lalmThinkingType(getThinkingMode()).let {
                requestFields["thinking"] = JsonObject(mapOf("type" to JsonPrimitive(it)))
            }

            val requestJson = JsonObject(requestFields)

            val requestBody = json.encodeToString(JsonObject.serializer(), requestJson)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            httpClient.newCall(httpRequest).executeAsync().use { response ->
                parseLalmResponse(response.body.string(), response.code, saveApiDebug)
            }
        }
    }

}
