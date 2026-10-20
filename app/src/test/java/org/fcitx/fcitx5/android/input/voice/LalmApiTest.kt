/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LalmApiTest {

    @Test
    fun mimoInputUsesWavDataUrlWithoutFormat() {
        assertEquals(
            Json.parseToJsonElement("""{"type":"input_audio","input_audio":{"data":"data:audio/wav;base64,QUJD"}}"""),
            buildLalmAudioPart(LalmAudioFormat.InputAudio, "mimo-v2.6-flash", "QUJD", "audio/wav"),
        )
    }

    @Test
    fun proxyModelNamesAlsoSelectMimoFormat() {
        assertEquals(
            buildLalmAudioPart(LalmAudioFormat.InputAudioDataUrl, "custom-model", "QUJD", "audio/wav"),
            buildLalmAudioPart(LalmAudioFormat.InputAudio, "xiaomi/mimo-v2.6-flash", "QUJD", "audio/wav"),
        )
    }

    @Test
    fun mp3DataUrlUsesMatchingMimeType() {
        for (mime in listOf("audio/mpeg", "audio/mp3")) {
            assertEquals(
                Json.parseToJsonElement("""{"type":"input_audio","input_audio":{"data":"data:audio/mpeg;base64,QUJD"}}"""),
                buildLalmAudioPart(LalmAudioFormat.InputAudioDataUrl, "custom", "QUJD", mime),
            )
        }
    }

    @Test
    fun otherModelsKeepOpenAiAudioFormat() {
        assertEquals(
            Json.parseToJsonElement("""{"type":"input_audio","input_audio":{"data":"QUJD","format":"wav"}}"""),
            buildLalmAudioPart(LalmAudioFormat.InputAudio, "gpt-4o-audio-preview", "QUJD", "audio/wav"),
        )
    }

    @Test
    fun explicitAudioUrlIsPreservedForMimo() {
        assertEquals(
            Json.parseToJsonElement("""{"type":"audio_url","audio_url":{"url":"data:audio/wav;base64,QUJD"}}"""),
            buildLalmAudioPart(LalmAudioFormat.AudioUrl, "mimo-v2.6-flash", "QUJD", "audio/wav"),
        )
    }

    @Test
    fun automaticThinkingDisablesOnlyMimoV26() {
        assertEquals("disabled", lalmThinkingType(LalmThinkingMode.Auto, "mimo-v2.6-flash"))
        assertEquals("disabled", lalmThinkingType(LalmThinkingMode.Auto, "xiaomi/mimo-v2.6-flash"))
        assertNull(lalmThinkingType(LalmThinkingMode.Auto, "gpt-4o-audio-preview"))
        assertNull(lalmThinkingType(LalmThinkingMode.Auto, "mimo-v2.5-asr"))
    }

    @Test
    fun thinkingCanBeExplicitlyEnabledOrDisabled() {
        assertEquals("enabled", lalmThinkingType(LalmThinkingMode.Enabled, "mimo-v2.6-flash"))
        assertEquals("disabled", lalmThinkingType(LalmThinkingMode.Disabled, "custom"))
    }

    @Test
    fun completeResponseIsOptInAndTranscriptionStaysSeparate() {
        val body = """{"choices":[{"message":{"content":"你好","reasoning_content":"private reasoning"},"finish_reason":"stop"}],"usage":{"prompt_tokens":20,"completion_tokens":5,"completion_tokens_details":{"reasoning_tokens":3}}}"""
        val result = parseLalmResponse(body, 200, true)
        assertEquals("你好", result.text)
        assertEquals(body, result.rawResponseBody)
        assertEquals("", parseLalmResponse(body, 200, false).rawResponseBody)
    }

    @Test
    fun nullAndMissingContentNeverCommitReasoning() {
        for (message in listOf(
            """{"content":null,"reasoning_content":"not a transcription"}""",
            """{"reasoning_content":"not a transcription"}""",
            """{"content":""}""",
        )) {
            val body = """{"choices":[{"message":$message,"finish_reason":"length"}]}"""
            val result = parseLalmResponse(body, 200, true)
            assertEquals("", result.text)
            assertEquals(body, result.rawResponseBody)
        }
        assertEquals("", parseLalmResponse("""{"choices":[]}""", 200, true).text)
    }

    @Test
    fun textContentPartsAreJoined() {
        val body = """{"choices":[{"message":{"content":[{"type":"text","text":"你好"},{"type":"text","text":"世界"}]}}]}"""
        assertEquals("你好世界", parseLalmResponse(body, 200, false).text)
    }

    @Test
    fun httpErrorsRetainBodyOnlyWhenDebugging() {
        val body = """{"error":{"message":"private server details","code":"invalid_audio"}}"""
        for (debug in listOf(true, false)) {
            val error = runCatching { parseLalmResponse(body, 400, debug) }.exceptionOrNull()
            assertTrue(error is TranscriptionException)
            assertEquals("LALM error: 400", error?.message)
            assertFalse(error!!.message!!.contains("private server details"))
            assertEquals(if (debug) body else "", (error as TranscriptionException).rawResponseBody)
        }
    }

    @Test
    fun malformedResponsesRemainAvailableForDiagnosis() {
        for (body in listOf("not JSON", "{}", "", """{"choices":[{"message":{"content":123}}]}""")) {
            for (debug in listOf(true, false)) {
                val error = runCatching { parseLalmResponse(body, 200, debug) }.exceptionOrNull()
                assertTrue(error is TranscriptionException)
                assertEquals(if (debug) body else "", (error as TranscriptionException).rawResponseBody)
            }
        }
    }
}
